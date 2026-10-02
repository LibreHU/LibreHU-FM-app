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
 * station name in the head unit's country, keep the best match by votes, download its `favicon`, cache it per
 * frequency. A logo is only accepted when the station name matches, to avoid showing a wrong one.
 * Blocking: call from a background thread.
 */
class StationLogos(
    context: Context,
) {
    private val dir = File(context.filesDir, "logos").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("logos", Context.MODE_PRIVATE)
    private val userAgent = "LibreHU-FM/${context.packageManager.getPackageInfo(context.packageName, 0).versionName}"

    /** Cached logo for [frequency], if any (whatever the name). */
    fun cached(frequency: Int): Bitmap? {
        val f = file(frequency)
        return if (f.exists()) BitmapFactory.decodeFile(f.path) else null
    }

    /** Logo for [name] on [frequency]: cache first, then network. Null when nothing matches. */
    fun find(
        frequency: Int,
        name: String,
    ): Bitmap? {
        val key = normalize(name)
        if (key.length < 2) return null
        val f = file(frequency)
        if (prefs.getString(frequency.toString(), null) == key) {
            // Same name as last time: cached logo, or a known miss (retried after a week).
            if (f.exists()) return BitmapFactory.decodeFile(f.path)
            if (System.currentTimeMillis() - prefs.getLong("miss_$frequency", 0) < RETRY_MISS_MS) return null
        }
        val url = search(name) ?: return miss(frequency, key)
        val bitmap = download(url) ?: return miss(frequency, key)
        f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        prefs
            .edit()
            .putString(frequency.toString(), key)
            .remove("miss_$frequency")
            .apply()
        return bitmap
    }

    fun forget(frequency: Int) {
        file(frequency).delete()
        prefs
            .edit()
            .remove(frequency.toString())
            .remove("miss_$frequency")
            .apply()
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

    /** Favicon URL of the best station named like [name] in the current country. */
    private fun search(name: String): String? {
        val country = Locale.getDefault().country.ifBlank { "FR" }
        val q =
            "name=${URLEncoder.encode(name.trim(), "UTF-8")}&countrycode=$country&hidebroken=true" +
                "&order=votes&reverse=true&limit=20"
        val body = get("https://$API_HOST/json/stations/search?$q")?.toString(Charsets.UTF_8) ?: return null
        val wanted = normalize(name)
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
            val n = normalize(o.optString("name"))
            // Exact name first, then "NRJ" -> "NRJ France"; votes break ties (list already sorted by votes).
            val score =
                when {
                    n == wanted -> 3
                    n.startsWith("$wanted ") -> 2
                    n.split(' ').contains(wanted) -> 1
                    else -> 0
                }
            if (score > bestScore) {
                best = favicon
                bestScore = score
            }
        }
        return best
    }

    private fun download(url: String): Bitmap? {
        val bytes = get(url, MAX_LOGO_BYTES) ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= LOGO_PX) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun get(
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

    private fun file(frequency: Int) = File(dir, "$frequency.png")

    companion object {
        private const val TAG = "LibreHU-FM"
        private const val API_HOST = "all.api.radio-browser.info"
        private const val TIMEOUT_MS = 8000
        private const val MAX_JSON_BYTES = 512 * 1024
        private const val MAX_LOGO_BYTES = 1024 * 1024
        private const val LOGO_PX = 256
        private const val RETRY_MISS_MS = 7L * 24 * 3600 * 1000

        /** Upper case, no accents, single spaces: "Fip " / "FIP" / "fip" are the same station. */
        fun normalize(s: String): String =
            Normalizer
                .normalize(s, Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "")
                .uppercase(Locale.ROOT)
                .replace(Regex("[^A-Z0-9]+"), " ")
                .trim()
    }
}
