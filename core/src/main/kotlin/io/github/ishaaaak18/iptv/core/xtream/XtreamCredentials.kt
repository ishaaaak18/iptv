package io.github.ishaaaak18.iptv.core.xtream

import java.net.URI

data class XtreamCredentials(val host: String, val username: String, val password: String) {

    val m3uUrl: String get() = "$host/get.php?username=$username&password=$password&type=m3u_plus&output=ts"
    val apiUrl: String get() = "$host/player_api.php?username=$username&password=$password"

    companion object {
        private val hostRegex = Regex("""(https?://[^/\s?#]+)""", RegexOption.IGNORE_CASE)
        private val userRegex = Regex("""(?:username|user)=([^&\s]+)""", RegexOption.IGNORE_CASE)
        private val passRegex = Regex("""(?:password|pass)=([^&\s]+)""", RegexOption.IGNORE_CASE)
        private val urlRegex = Regex("""(https?://\S+)""", RegexOption.IGNORE_CASE)
        private val pathMarkers = setOf("live", "movie", "series", "play", "vod", "timeshift")

        /**
         * Extracts Xtream credentials from any common link form:
         * get.php / player_api.php query links, or path links such as
         * http://host:port/live/user/pass/123.ts and http://host:port/user/pass/123.
         */
        fun parse(input: String): XtreamCredentials? {
            val text = input.trim()
            val host = hostRegex.find(text)?.groupValues?.get(1)?.trimEnd('/')
            val user = userRegex.find(text)?.groupValues?.get(1)
            val pass = passRegex.find(text)?.groupValues?.get(1)
            if (host != null && user != null && pass != null) return XtreamCredentials(host, user, pass)

            val url = urlRegex.find(text)?.groupValues?.get(1) ?: return null
            val base = hostRegex.find(url)?.groupValues?.get(1) ?: return null
            val parts = runCatching { URI(url).path.orEmpty() }.getOrDefault("")
                .split('/').filter { it.isNotEmpty() }
            if (parts.size < 2) return null
            return if (parts[0].lowercase() in pathMarkers) {
                if (parts.size < 3) null else XtreamCredentials(base, parts[1], parts[2])
            } else {
                XtreamCredentials(base, parts[0], parts[1])
            }
        }
    }
}
