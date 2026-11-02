package org.librehu.fm.web

import java.util.Locale

/** One entry of a playlist file: a stream address and its name when the file gives one. */
data class PlaylistEntry(
    val url: String,
    val name: String = "",
)

/**
 * Internet radio playlist files, the usual ways a station gives its stream:
 * - **M3U**: one address per line, `#` lines are comments; **extended M3U** (`#EXTM3U`) names the next entry with
 *   `#EXTINF:<length>,<name>` (UTF-8 for `.m3u8`, often Latin-1 for old `.m3u`);
 * - **PLS** (SHOUTcast / Icecast): INI file, `[playlist]`, `File1=` … `FileN=` and `Title1=` … (`Length` -1 = live);
 * - an `.m3u8` that is an **HLS** stream (`#EXT-X-` tags) is not a list of stations: it is played as such.
 * Sources: Wikipedia "M3U" and "PLS (file format)"; Apple's HLS (RFC 8216) for the `#EXT-X-` tags.
 */
object Playlists {
    enum class Kind { M3U, PLS, HLS, NONE }

    /** Addresses that name a playlist file by their extension (before any `?query`). */
    fun kindOfUrl(url: String): Kind {
        val path = url.substringBefore('?').substringBefore('#').lowercase(Locale.ROOT)
        return when {
            path.endsWith(".pls") -> Kind.PLS
            path.endsWith(".m3u") || path.endsWith(".m3u8") -> Kind.M3U
            else -> Kind.NONE
        }
    }

    /** Playlist kind from an HTTP `Content-Type` (parameters such as `; charset=` ignored). */
    fun kindOfContentType(contentType: String?): Kind {
        val t = contentType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT) ?: return Kind.NONE
        return when (t) {
            "audio/x-scpls", "application/pls+xml", "audio/scpls" -> Kind.PLS
            "audio/x-mpegurl", "audio/mpegurl", "application/x-mpegurl", "application/vnd.apple.mpegurl" -> Kind.M3U
            else -> Kind.NONE
        }
    }

    /** Kind of a downloaded file, from its content: what matters when the server or the extension lies. */
    fun kindOfText(text: String): Kind {
        val t = text.trimStart('﻿', ' ', '\t', '\r', '\n')
        return when {
            t.startsWith("[playlist]", ignoreCase = true) -> Kind.PLS
            t.lineSequence().any { it.trim().startsWith("#EXT-X-", ignoreCase = true) } -> Kind.HLS
            t.lineSequence().any { line -> line.trim().let { it.startsWith("http://", true) || it.startsWith("https://", true) } } ->
                Kind.M3U
            else -> Kind.NONE
        }
    }

    /** Entries of an M3U or PLS file ([kindOfText] decides); empty for an HLS stream or an unknown file. */
    fun parse(text: String): List<PlaylistEntry> =
        when (kindOfText(text)) {
            Kind.PLS -> parsePls(text)
            Kind.M3U -> parseM3u(text)
            else -> emptyList()
        }

    fun parseM3u(text: String): List<PlaylistEntry> {
        val out = mutableListOf<PlaylistEntry>()
        var name = ""
        for (raw in text.lineSequence()) {
            val line = raw.trim().trimStart('﻿')
            when {
                line.isEmpty() -> {}
                line.startsWith("#EXTINF", ignoreCase = true) -> name = line.substringAfter(',', "").trim()
                line.startsWith("#") -> {}
                isStreamUrl(line) -> {
                    out += PlaylistEntry(line, name)
                    name = ""
                }
            }
        }
        return out.distinctBy { it.url }
    }

    fun parsePls(text: String): List<PlaylistEntry> {
        val files = sortedMapOf<Int, String>()
        val titles = mutableMapOf<Int, String>()
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            val eq = line.indexOf('=')
            if (eq <= 0) continue
            val key = line.substring(0, eq).trim().lowercase(Locale.ROOT)
            val value = line.substring(eq + 1).trim()
            val n = key.trimStart('f', 'i', 'l', 'e', 't').toIntOrNull() ?: continue
            when {
                key.startsWith("file") && isStreamUrl(value) -> files[n] = value
                key.startsWith("title") -> titles[n] = value
            }
        }
        return files.map { (n, url) -> PlaylistEntry(url, titles[n].orEmpty()) }.distinctBy { it.url }
    }

    /** A stream address the player can open (http, https). */
    fun isStreamUrl(s: String): Boolean = s.startsWith("http://", ignoreCase = true) || s.startsWith("https://", ignoreCase = true)

    /** Name shown for a stream without one: its host ("stream.example.org"). */
    fun nameFromUrl(url: String): String =
        url.substringAfter("://").substringBefore('/').substringBefore(':').substringAfter('@').ifBlank { url }
}
