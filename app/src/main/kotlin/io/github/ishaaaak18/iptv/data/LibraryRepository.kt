package io.github.ishaaaak18.iptv.data

import io.github.ishaaaak18.iptv.core.PlaylistLoader
import io.github.ishaaaak18.iptv.core.model.Channel
import io.github.ishaaaak18.iptv.core.model.Playlist
import io.github.ishaaaak18.iptv.core.model.PlaylistSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/** A favorite or history entry; keeps the full channel so it plays even if the playlist changes. */
@Serializable
data class SavedChannel(val playlistId: String, val channel: Channel, val at: Long = System.currentTimeMillis())

/**
 * File-backed storage. Playlists, favorites and history are small JSON files;
 * each playlist's channels are cached in their own file so even very large lists
 * (tens of thousands of entries) load without size limits.
 */
class LibraryRepository(private val dir: File, private val loader: PlaylistLoader) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val mutex = Mutex()
    private val channelCache = HashMap<String, List<Channel>>()

    private val playlistsFile = File(dir, "playlists.json")
    private val favoritesFile = File(dir, "favorites.json")
    private val historyFile = File(dir, "history.json")
    private fun channelsFile(id: String) = File(dir, "channels_$id.json")

    private val _playlists = MutableStateFlow(read(playlistsFile, ListSerializer(Playlist.serializer())))
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _favorites = MutableStateFlow(read(favoritesFile, ListSerializer(SavedChannel.serializer())))
    val favorites: StateFlow<List<SavedChannel>> = _favorites.asStateFlow()

    private val _history = MutableStateFlow(read(historyFile, ListSerializer(SavedChannel.serializer())))
    val history: StateFlow<List<SavedChannel>> = _history.asStateFlow()

    fun playlist(id: String): Playlist? = _playlists.value.firstOrNull { it.id == id }

    /** Downloads the source first, so a broken link is reported before anything is saved. */
    suspend fun addPlaylist(name: String, source: PlaylistSource): Playlist {
        val loaded = loader.load(source)
        val playlist = Playlist(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { "Playlist ${_playlists.value.size + 1}" },
            source = source,
            epgUrl = loaded.epgUrl,
            updatedAt = System.currentTimeMillis(),
        )
        saveChannels(playlist.id, loaded.channels)
        mutex.withLock {
            _playlists.update { it + playlist }
            write(playlistsFile, ListSerializer(Playlist.serializer()), _playlists.value)
        }
        return playlist
    }

    suspend fun deletePlaylist(id: String) = mutex.withLock {
        _playlists.update { list -> list.filterNot { it.id == id } }
        write(playlistsFile, ListSerializer(Playlist.serializer()), _playlists.value)
        channelCache.remove(id)
        withContext(Dispatchers.IO) { channelsFile(id).delete() }
    }

    /** Cached channels, falling back to a network load when no cache exists or [refresh] is true. */
    suspend fun channels(id: String, refresh: Boolean = false): List<Channel> {
        if (!refresh) {
            channelCache[id]?.let { return it }
            val cached = withContext(Dispatchers.IO) {
                channelsFile(id).takeIf { it.exists() }?.let { read(it, ListSerializer(Channel.serializer())) }
            }
            if (!cached.isNullOrEmpty()) return cached.also { channelCache[id] = it }
        }
        val playlist = playlist(id) ?: return emptyList()
        val loaded = loader.load(playlist.source)
        saveChannels(id, loaded.channels)
        mutex.withLock {
            _playlists.update { list ->
                list.map { if (it.id == id) it.copy(updatedAt = System.currentTimeMillis(), epgUrl = loaded.epgUrl) else it }
            }
            write(playlistsFile, ListSerializer(Playlist.serializer()), _playlists.value)
        }
        return loaded.channels
    }

    fun isFavorite(channel: Channel) = _favorites.value.any { it.channel.url == channel.url }

    suspend fun toggleFavorite(playlistId: String, channel: Channel) = mutex.withLock {
        _favorites.update { list ->
            if (list.any { it.channel.url == channel.url }) list.filterNot { it.channel.url == channel.url }
            else list + SavedChannel(playlistId, channel)
        }
        write(favoritesFile, ListSerializer(SavedChannel.serializer()), _favorites.value)
    }

    suspend fun recordPlay(playlistId: String, channel: Channel) = mutex.withLock {
        _history.update { list ->
            (listOf(SavedChannel(playlistId, channel)) + list.filterNot { it.channel.url == channel.url }).take(MAX_HISTORY)
        }
        write(historyFile, ListSerializer(SavedChannel.serializer()), _history.value)
    }

    private suspend fun saveChannels(id: String, channels: List<Channel>) {
        channelCache[id] = channels
        write(channelsFile(id), ListSerializer(Channel.serializer()), channels)
    }

    private fun <T> read(file: File, serializer: kotlinx.serialization.KSerializer<List<T>>): List<T> =
        runCatching { if (file.exists()) json.decodeFromString(serializer, file.readText()) else emptyList() }
            .getOrDefault(emptyList())

    private suspend fun <T> write(file: File, serializer: kotlinx.serialization.KSerializer<T>, value: T) =
        withContext(Dispatchers.IO) {
            // Write to a temp file first so a crash mid-write never corrupts saved data.
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(serializer, value))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }

    companion object {
        private const val MAX_HISTORY = 50
    }
}
