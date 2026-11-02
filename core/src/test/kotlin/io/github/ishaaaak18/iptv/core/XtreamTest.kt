package io.github.ishaaaak18.iptv.core

import io.github.ishaaaak18.iptv.core.model.PlaylistSource
import io.github.ishaaaak18.iptv.core.xtream.XtreamApi
import io.github.ishaaaak18.iptv.core.xtream.XtreamCredentials
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class XtreamTest {

    @Test
    fun parsesCredentialForms() {
        val expected = XtreamCredentials("http://h.example:8080", "u1", "p1")
        assertEquals(expected, XtreamCredentials.parse("http://h.example:8080/get.php?username=u1&password=p1&type=m3u_plus"))
        assertEquals(expected, XtreamCredentials.parse("  http://h.example:8080/player_api.php?username=u1&password=p1 "))
        assertEquals(expected, XtreamCredentials.parse("http://h.example:8080/live/u1/p1/123.ts"))
        assertEquals(expected, XtreamCredentials.parse("http://h.example:8080/u1/p1/123"))
        assertNull(XtreamCredentials.parse("http://h.example:8080/live/u1"))
        assertNull(XtreamCredentials.parse("not a link"))
    }

    @Test
    fun classifiesUserInput() {
        assertIs<PlaylistSource.Xtream>(PlaylistLoader.sourceFromInput("http://h:80/get.php?username=a&password=b"))
        assertIs<PlaylistSource.M3uUrl>(PlaylistLoader.sourceFromInput("https://x.example/list.m3u"))
        assertNull(PlaylistLoader.sourceFromInput("hello"))
    }

    @Test
    fun loadsAccountAndLiveChannels() = runTest {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val body = when (request.requestUrl?.queryParameter("action")) {
                    null -> """{"user_info":{"status":"Active","exp_date":"1893456000","active_cons":"1","max_connections":"2"}}"""
                    "get_live_categories" -> """[{"category_id":"7","category_name":"News"}]"""
                    "get_live_streams" -> """[{"stream_id":42,"name":"Channel A","category_id":"7","stream_icon":"http://i/a.png","tv_archive_duration":"2"},{"name":"no id"}]"""
                    else -> "[]"
                }
                return MockResponse().setBody(body)
            }
        }
        server.start()
        try {
            val host = server.url("/").toString().trimEnd('/')
            val creds = XtreamCredentials(host, "u", "p")
            val api = XtreamApi(OkHttpClient())

            val info = api.account(creds).userInfo!!
            assertEquals("Active", info.status)
            assertEquals(1893456000L, info.expiresAt)
            assertEquals(1, info.active)
            assertEquals(2, info.max)

            val ch = api.liveChannels(creds).single()
            assertEquals("Channel A", ch.name)
            assertEquals("News", ch.group)
            assertEquals("$host/live/u/p/42.m3u8", ch.url)
            assertEquals(2, ch.catchupDays)
        } finally {
            server.shutdown()
        }
    }
}
