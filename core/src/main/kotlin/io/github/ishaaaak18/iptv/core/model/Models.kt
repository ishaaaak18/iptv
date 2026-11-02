package io.github.ishaaaak18.iptv.core.model

import kotlinx.serialization.Serializable

/** A single playable stream. */
@Serializable
data class Channel(
    val id: String,
    val name: String,
    val url: String,
    val group: String = "",
    val logo: String = "",
    val tvgId: String = "",
    val userAgent: String = "",
    val referrer: String = "",
    val catchupDays: Int = 0,
)

/** Where a playlist comes from. The app ships no content: every source is added by the user. */
@Serializable
sealed interface PlaylistSource {
    @Serializable
    data class M3uUrl(val url: String) : PlaylistSource

    @Serializable
    data class M3uText(val text: String) : PlaylistSource

    @Serializable
    data class Xtream(val host: String, val username: String, val password: String) : PlaylistSource
}

@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val source: PlaylistSource,
    val epgUrl: String = "",
    val updatedAt: Long = 0,
)
