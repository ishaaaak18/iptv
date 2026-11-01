package io.github.ishaaaak18.iptv.core

import io.github.ishaaaak18.iptv.core.m3u.M3uParser
import io.github.ishaaaak18.iptv.core.model.Channel
import io.github.ishaaaak18.iptv.core.model.PlaylistSource
import io.github.ishaaaak18.iptv.core.xtream.XtreamApi
import io.github.ishaaaak18.iptv.core.xtream.XtreamCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** Turns any [PlaylistSource] into a list of channels. */
class PlaylistLoader(private val client: OkHttpClient) {

    private val xtream = XtreamApi(client)

    data class Loaded(val channels: List<Channel>, val epgUrl: String)

    suspend fun load(source: PlaylistSource): Loaded = when (source) {
        is PlaylistSource.M3uText -> parse(source.text)
        is PlaylistSource.M3uUrl -> parse(download(source.url))
        is PlaylistSource.Xtream -> {
            val c = XtreamCredentials(source.host, source.username, source.password)
            Loaded(xtream.liveChannels(c), "${c.host}/xmltv.php?username=${c.username}&password=${c.password}")
        }
    }

    private suspend fun parse(text: String) = withContext(Dispatchers.Default) {
        val r = M3uParser.parse(text)
        if (r.channels.isEmpty()) throw IOException("No channels found in playlist")
        Loaded(r.channels, r.epgUrl)
    }

    private suspend fun download(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("User-Agent", XtreamApi.USER_AGENT).build()
        client.newCall(request).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            r.body?.string().orEmpty()
        }
    }

    companion object {
        /** Classifies what the user typed: an Xtream link becomes an Xtream source, anything else an M3U URL. */
        fun sourceFromInput(input: String): PlaylistSource? {
            val text = input.trim()
            if (!text.startsWith("http", ignoreCase = true)) return null
            val isXtreamLink = text.contains("get.php", true) || text.contains("player_api.php", true)
            if (isXtreamLink) {
                XtreamCredentials.parse(text)?.let { return PlaylistSource.Xtream(it.host, it.username, it.password) }
            }
            return PlaylistSource.M3uUrl(text)
        }
    }
}
