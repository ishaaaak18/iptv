package io.github.ishaaaak18.iptv.core.m3u

import io.github.ishaaaak18.iptv.core.model.Channel

/**
 * Tolerant M3U / M3U Plus parser.
 *
 * Handles quoted attributes (commas inside quotes do not end the attribute list),
 * #EXTGRP, #EXTVLCOPT user-agent / referrer, the url-tvg / x-tvg-url header and
 * catchup-days. Lines that are neither directives nor URLs are ignored.
 */
object M3uParser {

    data class Result(val channels: List<Channel>, val epgUrl: String)

    private val attrRegex = Regex("""([A-Za-z0-9_-]+)\s*=\s*"([^"]*)"""")

    fun parse(content: String): Result {
        val channels = ArrayList<Channel>()
        var epgUrl = ""
        var pending: Pending? = null
        var pendingGroup = ""
        var userAgent = ""
        var referrer = ""

        content.lineSequence().forEach { raw ->
            val line = raw.trim().removePrefix("﻿")
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTM3U", ignoreCase = true) -> {
                    val attrs = attributes(line)
                    epgUrl = attrs["url-tvg"] ?: attrs["x-tvg-url"] ?: epgUrl
                }
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    pending = parseExtInf(line)
                    pendingGroup = ""
                    userAgent = ""
                    referrer = ""
                }
                line.startsWith("#EXTGRP:", ignoreCase = true) ->
                    pendingGroup = line.substringAfter(':').trim()
                line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                    val opt = line.substringAfter(':')
                    val key = opt.substringBefore('=').trim().lowercase()
                    val value = opt.substringAfter('=', "").trim()
                    when (key) {
                        "http-user-agent" -> userAgent = value
                        "http-referrer", "http-referer" -> referrer = value
                    }
                }
                line.startsWith("#") -> Unit
                else -> {
                    val p = pending ?: Pending(name = line.substringAfterLast('/'))
                    channels += Channel(
                        id = "${channels.size}:${line.hashCode()}",
                        name = p.name.ifBlank { "—" },
                        url = line,
                        group = p.group.ifBlank { pendingGroup },
                        logo = p.logo,
                        tvgId = p.tvgId,
                        userAgent = userAgent,
                        referrer = referrer,
                        catchupDays = p.catchupDays,
                    )
                    pending = null
                    pendingGroup = ""
                }
            }
        }
        return Result(channels, epgUrl)
    }

    private data class Pending(
        val name: String,
        val group: String = "",
        val logo: String = "",
        val tvgId: String = "",
        val catchupDays: Int = 0,
    )

    private fun parseExtInf(line: String): Pending {
        // The display name follows the first comma that is outside quotes.
        var inQuotes = false
        var split = -1
        for (i in line.indices) {
            when (line[i]) {
                '"' -> inQuotes = !inQuotes
                ',' -> if (!inQuotes) { split = i; break }
            }
        }
        val head = if (split >= 0) line.substring(0, split) else line
        val attrs = attributes(head)
        val name = (if (split >= 0) line.substring(split + 1).trim() else "")
            .ifBlank { attrs["tvg-name"].orEmpty().trim() }
        return Pending(
            name = name,
            group = attrs["group-title"].orEmpty().trim(),
            logo = attrs["tvg-logo"].orEmpty().trim(),
            tvgId = attrs["tvg-id"].orEmpty().trim(),
            catchupDays = attrs["catchup-days"]?.toIntOrNull() ?: 0,
        )
    }

    private fun attributes(text: String): Map<String, String> =
        attrRegex.findAll(text).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
}
