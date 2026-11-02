package io.github.ishaaaak18.iptv.core

import io.github.ishaaaak18.iptv.core.m3u.M3uParser
import kotlin.test.Test
import kotlin.test.assertEquals

class M3uParserTest {

    @Test
    fun parsesAttributesAndCommaInsideQuotes() {
        val r = M3uParser.parse(
            """
            #EXTM3U url-tvg="http://epg.example/guide.xml"
            #EXTINF:-1 tvg-id="news.ar" tvg-logo="http://l/a.png" group-title="News, Arabic" catchup-days="3",Al Jazeera
            https://example.com/aja/index.m3u8
            """.trimIndent()
        )
        assertEquals("http://epg.example/guide.xml", r.epgUrl)
        val c = r.channels.single()
        assertEquals("Al Jazeera", c.name)
        assertEquals("News, Arabic", c.group)
        assertEquals("http://l/a.png", c.logo)
        assertEquals("news.ar", c.tvgId)
        assertEquals(3, c.catchupDays)
        assertEquals("https://example.com/aja/index.m3u8", c.url)
    }

    @Test
    fun handlesExtgrpVlcOptionsBomAndCrlf() {
        val text = "﻿#EXTM3U\r\n#EXTINF:-1,One\r\n#EXTGRP:Kids\r\n" +
            "#EXTVLCOPT:http-user-agent=Mozilla/5.0\r\n#EXTVLCOPT:http-referrer=https://r.example/\r\n" +
            "http://a/1.ts\r\n#EXTINF:-1 tvg-name=\"Fallback\",\r\nhttp://a/2.ts\r\n"
        val r = M3uParser.parse(text)
        assertEquals(2, r.channels.size)
        with(r.channels[0]) {
            assertEquals("Kids", group)
            assertEquals("Mozilla/5.0", userAgent)
            assertEquals("https://r.example/", referrer)
        }
        assertEquals("Fallback", r.channels[1].name)
        assertEquals("", r.channels[1].userAgent)
    }

    @Test
    fun urlWithoutExtinfStillBecomesChannel() {
        val r = M3uParser.parse("#EXTM3U\nhttp://host/stream/live.m3u8\n")
        assertEquals("live.m3u8", r.channels.single().name)
    }
}
