package org.librehu.fm.web

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * An internet radio station saved in the app. [url] is the stream or a playlist file (M3U, PLS), resolved each time
 * it plays (a station that moves its stream keeps the same playlist address). The other fields are optional: added by
 * hand, a station has only a name and an address.
 */
data class WebStation(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val url: String,
    /** Logo address (Radio Browser "favicon"), "" when none. */
    val logoUrl: String = "",
    /** ISO 3166 country code and region (Radio Browser "countrycode", "state"). */
    val country: String = "",
    val region: String = "",
    /** Codec and bitrate in kbit/s announced by the catalogue (0 = unknown). */
    val codec: String = "",
    val bitrate: Int = 0,
    /** Radio Browser station id ("stationuuid"), "" for a station added by hand. */
    val rbUuid: String = "",
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("n", name)
            .put("u", url)
            .put("l", logoUrl)
            .put("c", country)
            .put("r", region)
            .put("k", codec)
            .put("b", bitrate)
            .put("rb", rbUuid)

    companion object {
        fun fromJson(o: JSONObject): WebStation? {
            val url = o.optString("u")
            if (!Playlists.isStreamUrl(url)) return null
            return WebStation(
                id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                name = o.optString("n").ifBlank { Playlists.nameFromUrl(url) },
                url = url,
                logoUrl = o.optString("l"),
                country = o.optString("c"),
                region = o.optString("r"),
                codec = o.optString("k"),
                bitrate = o.optInt("b"),
                rbUuid = o.optString("rb"),
            )
        }

        fun listToJson(list: List<WebStation>): String = JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()

        fun listFromJson(json: String?): List<WebStation> =
            try {
                val a = JSONArray(json ?: "[]")
                (0 until a.length()).mapNotNull { fromJson(a.getJSONObject(it)) }
            } catch (_: Exception) {
                emptyList()
            }
    }
}
