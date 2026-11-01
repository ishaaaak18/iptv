package io.github.ishaaaak18.iptv.ui.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ishaaaak18.iptv.IptvApp
import io.github.ishaaaak18.iptv.core.model.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Pseudo-groups shown before the playlist's own groups. */
const val GROUP_ALL = "\u0000all"
const val GROUP_FAVORITES = "\u0000fav"
const val GROUP_RECENT = "\u0000recent"

class ChannelsViewModel : ViewModel() {

    private val repo = IptvApp.instance.repository

    sealed interface Load {
        data object Loading : Load
        data class Error(val message: String) : Load
        data object Ready : Load
    }

    private var playlistId = ""
    private val all = MutableStateFlow<List<Channel>>(emptyList())
    val load = MutableStateFlow<Load>(Load.Loading)
    val query = MutableStateFlow("")
    val group = MutableStateFlow(GROUP_ALL)

    val groups: StateFlow<List<String>> = all
        .map { list -> list.map { it.group }.filter { it.isNotBlank() }.distinct() }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val favoriteUrls: StateFlow<Set<String>> = repo.favorites
        .map { favs -> favs.map { it.channel.url }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** The list currently shown, after group and search filters. */
    val visible: StateFlow<List<Channel>> = combine(all, query, group, repo.favorites, repo.history) { list, q, g, favs, hist ->
        val base = when (g) {
            GROUP_ALL -> list
            GROUP_FAVORITES -> favs.filter { it.playlistId == playlistId }.map { it.channel }
            GROUP_RECENT -> hist.filter { it.playlistId == playlistId }.map { it.channel }
            else -> list.filter { it.group == g }
        }
        val needle = q.trim()
        if (needle.isEmpty()) base else base.filter { it.name.contains(needle, ignoreCase = true) }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun open(id: String) {
        if (id == playlistId && load.value == Load.Ready) return
        playlistId = id
        fetch(refresh = false)
    }

    fun refresh() = fetch(refresh = true)

    private fun fetch(refresh: Boolean) {
        load.value = Load.Loading
        viewModelScope.launch {
            runCatching { repo.channels(playlistId, refresh) }
                .onSuccess {
                    all.value = it
                    load.value = Load.Ready
                }
                .onFailure { load.value = Load.Error(it.message ?: it.javaClass.simpleName) }
        }
    }

    fun toggleFavorite(channel: Channel) {
        viewModelScope.launch { repo.toggleFavorite(playlistId, channel) }
    }

    val currentPlaylistId: String get() = playlistId
}
