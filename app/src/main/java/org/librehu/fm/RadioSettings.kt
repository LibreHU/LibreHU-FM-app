package org.librehu.fm

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/** How the FM audio reaches the speakers (see [FmAudio]). */
enum class AudioMode { AUTO, PATCH, RENDER }

/** User settings of the radio. */
data class Settings(
    /** RDS decoding (station name, radiotext, PTY, TP / TA). */
    val rds: Boolean = true,
    val radioText: Boolean = true,
    /** Programme type (news, pop, classical…) next to the station name. */
    val pty: Boolean = true,
    /** Follows the RDS alternative frequencies when the signal gets weak. */
    val af: Boolean = false,
    /** Names the favourites without a name with the RDS station name. */
    val rdsPresetNames: Boolean = true,
    val audioMode: AudioMode = AudioMode.AUTO,
    val logos: Boolean = true,
    /** Logos from the downloaded library only, never from the network. */
    val logosOffline: Boolean = false,
    /** Radio Browser server: empty = automatic (all.api.radio-browser.info), host name, or full URL. */
    val logoServer: String = "",
    /** Country of the stations (ISO 3166 code); empty = the head unit's country. */
    val country: String = "",
) {
    /** Base URL of the Radio Browser API, without trailing slash. */
    val serverUrl: String
        get() =
            when {
                logoServer.isBlank() -> "https://$DEFAULT_SERVER"
                logoServer.startsWith("http://") || logoServer.startsWith("https://") -> logoServer.trimEnd('/')
                else -> "https://${logoServer.trim().trimEnd('/')}"
            }

    val countryCode: String
        get() = country.trim().uppercase(Locale.ROOT).ifBlank { Locale.getDefault().country.ifBlank { "FR" } }

    companion object {
        const val DEFAULT_SERVER = "all.api.radio-browser.info"
    }
}

/** Settings shared by the service and the screens (same process), saved in shared preferences. */
object RadioSettings {
    private const val PREFS = "settings"
    private var flow: MutableStateFlow<Settings>? = null

    fun get(context: Context): StateFlow<Settings> = state(context).asStateFlow()

    fun update(
        context: Context,
        change: (Settings) -> Settings,
    ) {
        val f = state(context)
        val s = change(f.value)
        f.value = s
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("rds", s.rds)
            .putBoolean("radio_text", s.radioText)
            .putBoolean("pty", s.pty)
            .putBoolean("af", s.af)
            .putBoolean("rds_preset_names", s.rdsPresetNames)
            .putString("audio_mode", s.audioMode.name)
            .putBoolean("logos", s.logos)
            .putBoolean("logos_offline", s.logosOffline)
            .putString("logo_server", s.logoServer)
            .putString("country", s.country)
            .apply()
    }

    @Synchronized
    private fun state(context: Context): MutableStateFlow<Settings> = flow ?: MutableStateFlow(load(context)).also { flow = it }

    private fun load(context: Context): Settings {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val d = Settings()
        return Settings(
            rds = p.getBoolean("rds", d.rds),
            radioText = p.getBoolean("radio_text", d.radioText),
            pty = p.getBoolean("pty", d.pty),
            af = p.getBoolean("af", d.af),
            rdsPresetNames = p.getBoolean("rds_preset_names", d.rdsPresetNames),
            audioMode = runCatching { AudioMode.valueOf(p.getString("audio_mode", null) ?: "") }.getOrDefault(d.audioMode),
            logos = p.getBoolean("logos", d.logos),
            logosOffline = p.getBoolean("logos_offline", d.logosOffline),
            logoServer = p.getString("logo_server", d.logoServer).orEmpty(),
            country = p.getString("country", d.country).orEmpty(),
        )
    }
}
