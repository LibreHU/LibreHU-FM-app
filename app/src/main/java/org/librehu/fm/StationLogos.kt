package org.librehu.fm

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale

/**
 * Station logos from Radio Browser (https://www.radio-browser.info, open community database): search by the RDS
 * station name in the chosen country, keep the best match by votes, download its `favicon`, cache it per
 * frequency. A logo is only accepted when the station name matches, to avoid showing a wrong one.
 * The downloaded library ([LogoLibrary]) is looked up first; offline mode never goes to the network.
 * Blocking: call from a background thread.
 */
class StationLogos(
    private val context: Context,
) {
    private val dir = File(context.filesDir, "logos").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("logos", Context.MODE_PRIVATE)
    private val http = RadioBrowser.Http(context)
    private val manualDir = File(context.filesDir, "logos_manual").apply { mkdirs() }

    init {
        // A better name matching: the stations missed with the previous one are looked up again.
        if (prefs.getInt(KEY_MATCHER, 0) != MATCHER) {
            val e = prefs.edit()
            prefs.all.keys
                .filter { it.startsWith("miss_") }
                .forEach { e.remove(it) }
            e.putInt(KEY_MATCHER, MATCHER).apply()
        }
    }

    /** Cached logo for [frequency], if any (whatever the name); the one chosen by hand first. */
    fun cached(frequency: Int): Bitmap? {
        if (!RadioSettings.get(context).value.logos) return null
        manual(frequency)?.let { return it }
        val f = file(frequency)
        return if (f.exists()) BitmapFactory.decodeFile(f.path) else null
    }

    /** Logo chosen by hand for [frequency] (a picture file), kept whatever the RDS name. */
    fun setManual(
        frequency: Int,
        uri: android.net.Uri,
    ): Boolean {
        val bitmap =
            try {
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            } catch (e: Exception) {
                Log.w("LibreHU-FM", "manual logo: ${e.message}")
                null
            } ?: return false
        val scaled =
            if (bitmap.width > MANUAL_PX || bitmap.height > MANUAL_PX) {
                val k = MANUAL_PX.toFloat() / maxOf(bitmap.width, bitmap.height)
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * k).toInt().coerceAtLeast(1),
                    (bitmap.height * k).toInt().coerceAtLeast(1),
                    true,
                )
            } else {
                bitmap
            }
        File(manualDir, "$frequency.png").outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
        changed.value++
        return true
    }

    fun hasManual(frequency: Int) = File(manualDir, "$frequency.png").exists()

    /** Back to the automatic logo for [frequency]. */
    fun clearManual(frequency: Int) {
        File(manualDir, "$frequency.png").delete()
        changed.value++
    }

    private fun manual(frequency: Int): Bitmap? {
        val f = File(manualDir, "$frequency.png")
        return if (f.exists()) BitmapFactory.decodeFile(f.path) else null
    }

    /** Logo for [name] on [frequency]: cache, downloaded library, then network. Null when nothing matches. */
    fun find(
        frequency: Int,
        name: String,
    ): Bitmap? {
        val settings = RadioSettings.get(context).value
        if (!settings.logos) return null
        manual(frequency)?.let { return it }
        val key = normalize(name)
        if (key.length < 2) return null
        val f = file(frequency)
        if (prefs.getString(frequency.toString(), null) == key) {
            // Same name as last time: cached logo, or a known miss (retried after a week, or when the library changed).
            if (f.exists()) return BitmapFactory.decodeFile(f.path)
            val missAt = prefs.getLong("miss_$frequency", 0)
            if (missAt > LogoLibrary.updatedAt(context) &&
                System.currentTimeMillis() - missAt < RETRY_MISS_MS
            ) {
                return null
            }
        }
        val bitmap =
            LogoLibrary.find(context, name)
                ?: if (settings.logosOffline) {
                    null
                } else {
                    queries(name)
                        .firstNotNullOfOrNull { q ->
                            RadioBrowser.search(http, settings.serverUrl, settings.countryCode, q, name)
                        }?.let { http.logo(it) }
                }
        if (bitmap == null) return miss(frequency, key)
        f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        prefs
            .edit()
            .putString(frequency.toString(), key)
            .remove("miss_$frequency")
            .apply()
        return bitmap
    }

    private fun miss(
        frequency: Int,
        key: String,
    ): Bitmap? {
        file(frequency).delete()
        prefs
            .edit()
            .putString(frequency.toString(), key)
            .putLong("miss_$frequency", System.currentTimeMillis())
            .apply()
        return null
    }

    private fun file(frequency: Int) = File(dir, "$frequency.png")

    companion object {
        private const val RETRY_MISS_MS = 7L * 24 * 3600 * 1000

        /** Forgets every logo cached per frequency (they are looked up again). */
        fun clearCache(context: Context) {
            File(context.filesDir, "logos").listFiles()?.filter { it.isFile }?.forEach { it.delete() }
            context
                .getSharedPreferences("logos", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()
        }

        /** Upper case, no accents, single spaces: "Fip " / "FIP" / "fip" are the same station. */
        fun normalize(s: String): String =
            Normalizer
                .normalize(s, Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "")
                .uppercase(Locale.ROOT)
                .replace(Regex("[^A-Z0-9]+"), " ")
                .trim()

        /** How well a station called [candidate] matches the RDS name [wanted] (both normalized); 0 = not at all. */
        fun score(
            wanted: String,
            candidate: String,
        ): Int = variants(candidate).maxOf { scoreOne(wanted, it) }

        private fun scoreOne(
            wanted: String,
            candidate: String,
        ): Int {
            val w = core(wanted)
            val c = core(candidate)
            val wc = w.replace(" ", "")
            val cc = c.replace(" ", "")
            return when {
                candidate == wanted || (w.isNotEmpty() && c == w) || (wc.length >= 3 && cc == wc) -> 3
                candidate.startsWith("$wanted ") || (w.isNotEmpty() && c.startsWith("$w ")) -> 2
                // RDS names are 8 characters, often without spaces: "ICIVAUCL" for "ici Vaucluse".
                wc.length >= 5 && cc.startsWith(wc) -> 2
                candidate.split(' ').contains(wanted) || (w.length >= 3 && c.split(' ').contains(w)) -> 1
                else -> 0
            }
        }

        /** The name without the words most stations add or drop: "RTV FM" and "Radio RTV" are "RTV". */
        private fun core(s: String): String = s.split(' ').filter { it.isNotEmpty() && it !in NOISE }.joinToString(" ")

        /** Former names: the France Bleu stations became "ici …" in January 2025. */
        private fun variants(candidate: String): List<String> =
            buildList {
                add(candidate)
                if (candidate.startsWith("FRANCE BLEU ")) add("ICI " + candidate.removePrefix("FRANCE BLEU "))
                if (candidate.startsWith("ICI ")) add("FRANCE BLEU " + candidate.removePrefix("ICI "))
            }

        /**
         * Searches to send to Radio Browser (it matches a part of the name): the RDS name, without "FM" / "radio",
         * and for a name without spaces its known prefix split off ("ICIVAUCL" → "ici VAUCL") or its last letters.
         */
        fun queries(name: String): List<String> {
            val n = normalize(name)
            val out = LinkedHashSet<String>()
            out += name.trim()
            core(n).takeIf { it.length >= 2 }?.let { out += it }
            val single = core(n).replace(" ", "")
            if (!core(n).contains(' ') && single.length >= 6) {
                PREFIXES.firstOrNull { single.startsWith(it) && single.length - it.length >= 3 }?.let {
                    out += "$it ${single.removePrefix(it)}"
                }
                out += single.takeLast(5)
            }
            return out.toList().take(4)
        }

        private const val KEY_MATCHER = "matcher"
        private const val MATCHER = 2
        private const val MANUAL_PX = 256
        private val NOISE = setOf("FM", "RADIO", "LA", "LE", "OFFICIEL", "OFFICIAL")
        private val PREFIXES = listOf("ICI", "FRANCE", "RADIO", "RCF", "RTL", "NRJ", "RFM", "RMC", "FIP", "RTS", "RTV")

        /** Bumped when a logo is chosen or removed by hand: the service shows it again. */
        val changed = kotlinx.coroutines.flow.MutableStateFlow(0)
    }
}

/** Radio Browser API (https://api.radio-browser.info): station search, mirror list, logo download. */
object RadioBrowser {
    private const val TAG = "LibreHU-FM"
    private const val TIMEOUT_MS = 8000
    const val MAX_JSON_BYTES = 512 * 1024
    private const val MAX_LOGO_BYTES = 1024 * 1024
    private const val LOGO_PX = 256

    /** Favicon URL of the best station named like [name] in [country]. */
    fun search(
        http: Http,
        server: String,
        country: String,
        query: String,
        name: String = query,
    ): String? {
        val q =
            "name=${URLEncoder.encode(query.trim(), "UTF-8")}&countrycode=$country&hidebroken=true" +
                "&order=votes&reverse=true&limit=20"
        val body = http.get("$server/json/stations/search?$q")?.toString(Charsets.UTF_8) ?: return null
        val wanted = StationLogos.normalize(name)
        val stations =
            try {
                JSONArray(body)
            } catch (_: Exception) {
                return null
            }
        var best: String? = null
        var bestScore = 0
        for (i in 0 until stations.length()) {
            val o = stations.getJSONObject(i)
            val favicon = o.optString("favicon")
            if (!favicon.startsWith("http")) continue
            // Exact name first, then "NRJ" -> "NRJ France"; votes break ties (list already sorted by votes).
            val score = StationLogos.score(wanted, StationLogos.normalize(o.optString("name")))
            if (score > bestScore) {
                best = favicon
                bestScore = score
            }
        }
        return best
    }

    /** Host names of the Radio Browser mirrors, from the server list of the automatic address. */
    fun servers(http: Http): List<String> {
        val body = http.get("https://${Settings.DEFAULT_SERVER}/json/servers")?.toString(Charsets.UTF_8) ?: return emptyList()
        return try {
            val a = JSONArray(body)
            List(a.length()) { a.getJSONObject(it).optString("name") }.filter { it.isNotBlank() }.distinct().sorted()
        } catch (_: Exception) {
            emptyList()
        }
    }

    class Http(
        context: Context,
    ) {
        private val userAgent = "LibreHU-FM/${context.packageManager.getPackageInfo(context.packageName, 0).versionName}"

        /** Logo at [url], scaled down to about [LOGO_PX]; null when it is not an image. */
        fun logo(url: String): Bitmap? {
            val bytes = get(url, MAX_LOGO_BYTES) ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0) return null
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= LOGO_PX) sample *= 2
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        }

        fun get(
            url: String,
            maxBytes: Int = MAX_JSON_BYTES,
            redirects: Int = 3,
        ): ByteArray? =
            try {
                val c = URL(url).openConnection() as HttpURLConnection
                c.connectTimeout = TIMEOUT_MS
                c.readTimeout = TIMEOUT_MS
                c.instanceFollowRedirects = true
                c.setRequestProperty("User-Agent", userAgent)
                val code = c.responseCode
                if (code in 300..399 && redirects > 0) {
                    // HttpURLConnection does not follow http <-> https redirects.
                    val next = c.getHeaderField("Location")
                    c.disconnect()
                    if (next == null) null else get(URL(URL(url), next).toString(), maxBytes, redirects - 1)
                } else if (code != 200) {
                    c.disconnect()
                    null
                } else {
                    c.inputStream.use { input ->
                        val out = java.io.ByteArrayOutputStream()
                        val buf = ByteArray(8192)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            if (out.size() > maxBytes) return null
                        }
                        out.toByteArray()
                    }
                }
            } catch (e: Exception) {
                Log.i(TAG, "logo lookup $url: ${e.message}")
                null
            }
    }
}
