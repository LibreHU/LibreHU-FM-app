package org.librehu.fm

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** A song heard on the radio, from the RT+ (or eRT+) tags: [time] in epoch milliseconds. */
data class HeardTitle(
    val time: Long,
    val frequency: Int,
    val station: String,
    val artist: String,
    val title: String,
)

/** The last [MAX] songs, newest first, kept in shared preferences. */
object TitleHistory {
    private const val PREFS = "history"
    private const val KEY = "titles"
    private const val MAX = 100
    private var flow: MutableStateFlow<List<HeardTitle>>? = null

    fun get(context: Context): StateFlow<List<HeardTitle>> = state(context).asStateFlow()

    /** Adds a song unless it is the one added last for this station (RT+ tags come again every few seconds). */
    fun add(
        context: Context,
        entry: HeardTitle,
    ) {
        if (entry.artist.isBlank() && entry.title.isBlank()) return
        val f = state(context)
        val last = f.value.firstOrNull { it.frequency == entry.frequency }
        if (last != null && last.artist == entry.artist && last.title == entry.title) return
        f.value = (listOf(entry) + f.value).take(MAX)
        save(context, f.value)
    }

    fun clear(context: Context) {
        state(context).value = emptyList()
        save(context, emptyList())
    }

    @Synchronized
    private fun state(context: Context): MutableStateFlow<List<HeardTitle>> =
        flow ?: MutableStateFlow(load(context)).also { flow = it }

    private fun load(context: Context): List<HeardTitle> =
        try {
            val a = JSONArray(prefs(context).getString(KEY, "[]"))
            List(a.length()) { i ->
                val o = a.getJSONObject(i)
                HeardTitle(o.getLong("t"), o.getInt("f"), o.optString("s"), o.optString("a"), o.optString("n"))
            }
        } catch (_: Exception) {
            emptyList()
        }

    private fun save(
        context: Context,
        list: List<HeardTitle>,
    ) {
        val a = JSONArray()
        list.forEach {
            a.put(JSONObject().put("t", it.time).put("f", it.frequency).put("s", it.station).put("a", it.artist).put("n", it.title))
        }
        prefs(context).edit().putString(KEY, a.toString()).apply()
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
