package org.librehu.fm.web

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import org.librehu.fm.RadioBrowser
import java.io.File
import java.net.URLEncoder
import java.util.Locale

/**
 * Radio Browser catalogue of a country, downloaded in one request (stations/bycountrycodeexact, broken streams left
 * out: about 4 MB for France) and kept on the head unit for a week, so that regions, genres and the search work
 * without a request each. Blocking: call from a background thread.
 */
class CatalogStore(
    private val context: Context,
) {
    private val http = RadioBrowser.Http(context)
    private val dir = File(context.filesDir, "catalog").apply { mkdirs() }

    /** Kept catalogue of [country] and its age in ms, if any (offline, or before the download). */
    fun cached(country: String): Pair<List<CatalogStation>, Long>? {
        val f = file(country)
        if (!f.exists()) return null
        val list = parseCompact(f.readText())
        return if (list.isEmpty()) null else list to (System.currentTimeMillis() - f.lastModified())
    }

    fun isFresh(country: String): Boolean {
        val f = file(country)
        return f.exists() && System.currentTimeMillis() - f.lastModified() < MAX_AGE_MS
    }

    /** Downloads the catalogue of [country] from [server]; null when the network or the server fails. */
    fun download(
        server: String,
        country: String,
    ): List<CatalogStation>? {
        val cc = country.uppercase(Locale.ROOT)
        val url = "$server/json/stations/bycountrycodeexact/$cc?hidebroken=true&order=clickcount&reverse=true&limit=$MAX_STATIONS"
        val body = http.get(url, MAX_BYTES)?.toString(Charsets.UTF_8) ?: return null
        val list = parseApi(body)
        if (list.isEmpty()) return null
        file(country).writeText(toCompact(list))
        Log.i(TAG, "catalogue $cc: ${list.size} stations")
        return list
    }

    /** Stations whose name contains [query] in [country] (for the FM stations' internet stream), most voted first. */
    fun search(
        server: String,
        country: String,
        query: String,
    ): List<CatalogStation> {
        val q =
            "name=${URLEncoder.encode(query.trim(), "UTF-8")}&countrycode=${country.uppercase(Locale.ROOT)}" +
                "&hidebroken=true&order=votes&reverse=true&limit=40"
        val body = http.get("$server/json/stations/search?$q")?.toString(Charsets.UTF_8) ?: return emptyList()
        return parseApi(body)
    }

    /**
     * Tells Radio Browser that the station is played ("json/url/<uuid>", asked by its documentation: the most played
     * stations come first for everyone). Counted once a day per address by the server.
     */
    fun countClick(
        server: String,
        uuid: String,
    ) {
        http.get("$server/json/url/${URLEncoder.encode(uuid, "UTF-8")}", 64 * 1024)
    }

    private fun file(country: String) = File(dir, "${country.uppercase(Locale.ROOT)}.json")

    companion object {
        private const val TAG = "LibreHU-Web"
        const val MAX_AGE_MS = 7L * 24 * 3600 * 1000
        private const val MAX_STATIONS = 15_000
        private const val MAX_BYTES = 32 * 1024 * 1024

        /** Stations of an API answer; those without an http(s) stream are left out. */
        fun parseApi(body: String): List<CatalogStation> =
            try {
                val a = JSONArray(body)
                (0 until a.length()).mapNotNull { i ->
                    val o = a.getJSONObject(i)
                    val url = o.optString("url_resolved").ifBlank { o.optString("url") }
                    if (!Playlists.isStreamUrl(url) || o.optString("name").isBlank()) return@mapNotNull null
                    CatalogStation(
                        uuid = o.optString("stationuuid"),
                        name = o.optString("name").trim(),
                        url = url,
                        favicon = o.optString("favicon").takeIf { it.startsWith("http") }.orEmpty(),
                        country = o.optString("countrycode"),
                        state = o.optString("state").trim(),
                        tags = o.optString("tags").split(',').map { it.trim().lowercase(Locale.ROOT) }.filter { it.isNotEmpty() },
                        language = o.optString("language"),
                        codec = o.optString("codec"),
                        bitrate = o.optInt("bitrate"),
                        hls = o.optInt("hls") == 1,
                        votes = o.optInt("votes"),
                        clicks = o.optInt("clickcount"),
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "catalogue: ${e.message}")
                emptyList()
            }

        private fun toCompact(list: List<CatalogStation>): String =
            JSONArray()
                .apply {
                    list.forEach { s ->
                        put(
                            JSONObject()
                                .put("i", s.uuid)
                                .put("n", s.name)
                                .put("u", s.url)
                                .put("f", s.favicon)
                                .put("c", s.country)
                                .put("s", s.state)
                                .put("t", s.tags.joinToString(","))
                                .put("l", s.language)
                                .put("k", s.codec)
                                .put("b", s.bitrate)
                                .put("h", s.hls)
                                .put("v", s.votes)
                                .put("p", s.clicks),
                        )
                    }
                }.toString()

        private fun parseCompact(text: String): List<CatalogStation> =
            try {
                val a = JSONArray(text)
                List(a.length()) { i ->
                    val o = a.getJSONObject(i)
                    CatalogStation(
                        uuid = o.optString("i"),
                        name = o.optString("n"),
                        url = o.optString("u"),
                        favicon = o.optString("f"),
                        country = o.optString("c"),
                        state = o.optString("s"),
                        tags = o.optString("t").split(',').filter { it.isNotEmpty() },
                        language = o.optString("l"),
                        codec = o.optString("k"),
                        bitrate = o.optInt("b"),
                        hls = o.optBoolean("h"),
                        votes = o.optInt("v"),
                        clicks = o.optInt("p"),
                    )
                }
            } catch (_: Exception) {
                emptyList()
            }
    }
}
