package org.librehu.fm

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Downloaded logos of a whole country, for offline use. */
data class LibraryState(
    val running: Boolean = false,
    /** Stations handled / to handle during a download. */
    val done: Int = 0,
    val total: Int = 0,
    /** Logos in the library. */
    val count: Int = 0,
    val bytes: Long = 0,
    val country: String = "",
    val updatedAt: Long = 0,
    val error: String = "",
)

/**
 * Logo library: every station of a country listed by Radio Browser, most voted first, with its logo saved locally
 * (one entry per station name). [StationLogos] looks here before the network, so the logos work without Internet.
 */
object LogoLibrary {
    private const val TAG = "LibreHU-FM"
    private const val PAGE = 250
    private const val MAX_PAGE_BYTES = 4 * 1024 * 1024

    /** Choices of the number of stations to download; 0 = all. */
    val LIMITS = listOf(200, 500, 1000, 0)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val libState = MutableStateFlow(LibraryState())
    private var loaded = false

    /** Normalized name → entry. */
    private var index: Map<String, Entry> = emptyMap()

    private data class Entry(
        val key: String,
        val name: String,
        val file: String,
        val votes: Int,
    )

    fun state(context: Context): StateFlow<LibraryState> {
        load(context)
        return libState.asStateFlow()
    }

    fun updatedAt(context: Context): Long {
        load(context)
        return libState.value.updatedAt
    }

    /** Logo of the library station best matching the RDS name [name], if any. */
    fun find(
        context: Context,
        name: String,
    ): Bitmap? {
        load(context)
        val wanted = StationLogos.normalize(name)
        if (wanted.length < 2) return null
        val best =
            index[wanted] ?: index.values
                .map { it to StationLogos.score(wanted, it.key) }
                .filter { it.second > 0 }
                .maxWithOrNull(compareBy<Pair<Entry, Int>> { it.second }.thenBy { it.first.votes })
                ?.first
        return best?.let { BitmapFactory.decodeFile(File(dir(context), it.file).path) }
    }

    /** Downloads the logos of the [limit] most voted stations of the chosen country (0 = all). */
    fun download(
        context: Context,
        limit: Int,
    ) {
        if (job?.isActive == true) return
        val app = context.applicationContext
        load(app)
        val settings = RadioSettings.get(app).value
        val http = RadioBrowser.Http(app)
        job =
            scope.launch {
                libState.update { it.copy(running = true, done = 0, total = 0, error = "") }
                try {
                    val stations = list(http, settings.serverUrl, settings.countryCode, limit)
                    if (stations == null) {
                        libState.update { it.copy(running = false, error = "network") }
                        return@launch
                    }
                    libState.update { it.copy(total = stations.size) }
                    val dir = dir(app).apply { mkdirs() }
                    val entries = LinkedHashMap<String, Entry>()
                    // Logos of an earlier download of the same country are kept when the station is still listed.
                    val previous = if (libState.value.country == settings.countryCode) index else emptyMap()
                    for ((i, s) in stations.withIndex()) {
                        if (!isActive) break
                        val old = previous[s.key]
                        val entry =
                            if (old != null && File(dir, old.file).exists()) {
                                old.copy(votes = s.votes)
                            } else {
                                http.logo(s.favicon)?.let { bmp ->
                                    val file = "${s.uuid.ifBlank { s.key.replace(' ', '_') }}.png"
                                    File(dir, file).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                                    Entry(s.key, s.name, file, s.votes)
                                }
                            }
                        if (entry != null) entries[s.key] = entry
                        libState.update { it.copy(done = i + 1) }
                    }
                    if (isActive) {
                        // Logos no longer listed are deleted.
                        val keep = entries.values.map { it.file }.toSet()
                        dir.listFiles()?.filter { it.name.endsWith(".png") && it.name !in keep }?.forEach { it.delete() }
                        save(app, settings.countryCode, entries.values.toList())
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "logo library: ${e.message}")
                    libState.update { it.copy(error = e.message ?: "error") }
                } finally {
                    libState.update { it.copy(running = false) }
                }
            }
    }

    fun cancel() {
        job?.cancel()
    }

    fun clear(context: Context) {
        cancel()
        dir(context).deleteRecursively()
        index = emptyMap()
        libState.value = LibraryState(updatedAt = System.currentTimeMillis())
    }

    private class Listed(
        val key: String,
        val name: String,
        val uuid: String,
        val favicon: String,
        val votes: Int,
    )

    /** Stations of [country] with a logo, most voted first, one per name; null on network error. */
    private fun CoroutineScope.list(
        http: RadioBrowser.Http,
        server: String,
        country: String,
        limit: Int,
    ): List<Listed>? {
        val out = LinkedHashMap<String, Listed>()
        var offset = 0
        while (isActive) {
            val url =
                "$server/json/stations/bycountrycodeexact/$country?hidebroken=true&order=votes&reverse=true" +
                    "&limit=$PAGE&offset=$offset"
            val body = http.get(url, MAX_PAGE_BYTES)?.toString(Charsets.UTF_8) ?: return if (offset == 0) null else out.values.toList()
            val a = JSONArray(body)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val favicon = o.optString("favicon")
                val key = StationLogos.normalize(o.optString("name"))
                if (!favicon.startsWith("http") || key.length < 2 || key in out) continue
                out[key] = Listed(key, o.optString("name").trim(), o.optString("stationuuid"), favicon, o.optInt("votes"))
                if (limit > 0 && out.size >= limit) return out.values.toList()
            }
            if (a.length() < PAGE) break
            offset += PAGE
        }
        return out.values.toList()
    }

    private fun dir(context: Context) = File(context.filesDir, "logo_library")

    private fun indexFile(context: Context) = File(dir(context), "index.json")

    @Synchronized
    private fun load(context: Context) {
        if (loaded) return
        loaded = true
        try {
            val f = indexFile(context)
            if (!f.exists()) return
            val o = JSONObject(f.readText())
            val a = o.getJSONArray("stations")
            index =
                List(a.length()) { i ->
                    val e = a.getJSONObject(i)
                    Entry(e.getString("k"), e.optString("n"), e.getString("f"), e.optInt("v"))
                }.associateBy { it.key }
            libState.value =
                LibraryState(
                    count = index.size,
                    bytes = size(context),
                    country = o.optString("country"),
                    updatedAt = o.optLong("updated"),
                )
        } catch (e: Exception) {
            Log.w(TAG, "logo library index: ${e.message}")
        }
    }

    private fun save(
        context: Context,
        country: String,
        entries: List<Entry>,
    ) {
        val a = JSONArray()
        entries.forEach {
            a.put(
                JSONObject()
                    .put("k", it.key)
                    .put("n", it.name)
                    .put("f", it.file)
                    .put("v", it.votes),
            )
        }
        val now = System.currentTimeMillis()
        indexFile(context).writeText(
            JSONObject()
                .put("country", country)
                .put("updated", now)
                .put("stations", a)
                .toString(),
        )
        index = entries.associateBy { it.key }
        libState.update { it.copy(count = index.size, bytes = size(context), country = country, updatedAt = now) }
    }

    private fun size(context: Context): Long = dir(context).listFiles()?.sumOf { it.length() } ?: 0
}
