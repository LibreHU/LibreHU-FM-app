package org.librehu.fm

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** One saved station. [frequency] in 100 kHz units (1015 = 101.5 MHz). */
data class Station(
    val frequency: Int,
    val name: String = "",
)

data class RadioState(
    val available: Boolean = true,
    val error: String = "",
    val poweredOn: Boolean = false,
    val busy: Boolean = false,
    val scanning: Boolean = false,
    val frequency: Int = Band.DEFAULT,
    val ps: String = "",
    val radioText: String = "",
    val presets: List<Station> = emptyList(),
    val stations: List<Station> = emptyList(),
) {
    val isPreset: Boolean get() = presets.any { it.frequency == frequency }

    /** Station name: RDS PS, else the preset name. */
    val title: String
        get() = ps.ifBlank { presets.firstOrNull { it.frequency == frequency }?.name.orEmpty() }
}

object Band {
    const val MIN = 875
    const val MAX = 1080
    const val STEP = 1
    const val DEFAULT = 1000

    fun mhz(frequency: Int): Float = frequency / 10f

    fun format(frequency: Int): String = "%d.%d".format(frequency / 10, frequency % 10)

    fun clamp(frequency: Int): Int = frequency.coerceIn(MIN, MAX)
}

/** Persistence of the last frequency, presets and scan results. */
class RadioStore(
    context: Context,
) {
    private val prefs = context.getSharedPreferences("radio", Context.MODE_PRIVATE)

    var frequency: Int
        get() = Band.clamp(prefs.getInt("frequency", Band.DEFAULT))
        set(value) = prefs.edit().putInt("frequency", value).apply()

    var presets: List<Station>
        get() = read("presets")
        set(value) = write("presets", value)

    var stations: List<Station>
        get() = read("stations")
        set(value) = write("stations", value)

    private fun read(key: String): List<Station> =
        try {
            val a = JSONArray(prefs.getString(key, "[]"))
            List(a.length()) { i ->
                val o = a.getJSONObject(i)
                Station(o.getInt("f"), o.optString("n"))
            }
        } catch (_: Exception) {
            emptyList()
        }

    private fun write(
        key: String,
        list: List<Station>,
    ) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("f", it.frequency).put("n", it.name)) }
        prefs.edit().putString(key, a.toString()).apply()
    }
}
