package org.librehu.fm

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/** How the FM audio reaches the speakers (see [FmAudio]). */
enum class AudioMode { AUTO, PATCH, RENDER }

/** Stream chosen when an internet station has several: the best sound, or the least data. */
enum class WebQuality { BEST, ECO }

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
    /** Artist and title from the RT+ tags in the media card, the notification and the widget. */
    val rtPlus: Boolean = true,
    /** Band plan (limits, step, de-emphasis, PTY names); null = the one of [countryCode]. */
    val region: Region? = null,
    /** Mono even when the station is received in stereo: less hiss on weak stations. */
    val forceMono: Boolean = false,
    /** Seek and scan stop on strong stations only (local), not on distant ones (DX). */
    val localSearch: Boolean = false,
    /** Traffic announcements (TA) of the station: louder while the radio plays, played over another source. */
    val trafficAnnouncements: Boolean = false,
    /** Head unit clock set from the RDS clock time when it is more than a minute off. */
    val rdsClock: Boolean = false,
    val audioMode: AudioMode = AudioMode.AUTO,
    val logos: Boolean = true,
    /** Logos from the downloaded library only, never from the network. */
    val logosOffline: Boolean = false,
    /** Radio Browser server: empty = automatic (all.api.radio-browser.info), host name, or full URL. */
    val logoServer: String = "",
    /** Country of the stations (ISO 3166 code); empty = the head unit's country. */
    val country: String = "",
    /** Internet stations: stream chosen among a station's streams. */
    val webQuality: WebQuality = WebQuality.BEST,
    /** Offers the internet stream of the FM station played, when there is one and the internet works. */
    val webSuggest: Boolean = true,
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

    /** Band plan in use. */
    val band: Region
        get() = region ?: Region.forCountry(countryCode)

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
        Band.region = s.band
        f.value = s
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("rds", s.rds)
            .putBoolean("radio_text", s.radioText)
            .putBoolean("pty", s.pty)
            .putBoolean("af", s.af)
            .putBoolean("rds_preset_names", s.rdsPresetNames)
            .putBoolean("rt_plus", s.rtPlus)
            .putString("region", s.region?.name.orEmpty())
            .putBoolean("force_mono", s.forceMono)
            .putBoolean("local_search", s.localSearch)
            .putBoolean("traffic_announcements", s.trafficAnnouncements)
            .putBoolean("rds_clock", s.rdsClock)
            .putString("audio_mode", s.audioMode.name)
            .putBoolean("logos", s.logos)
            .putBoolean("logos_offline", s.logosOffline)
            .putString("logo_server", s.logoServer)
            .putString("country", s.country)
            .putString("web_quality", s.webQuality.name)
            .putBoolean("web_suggest", s.webSuggest)
            .apply()
    }

    @Synchronized
    private fun state(context: Context): MutableStateFlow<Settings> =
        flow ?: MutableStateFlow(load(context)).also {
            Band.region = it.value.band
            flow = it
        }

    private fun load(context: Context): Settings {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val d = Settings()
        return Settings(
            rds = p.getBoolean("rds", d.rds),
            radioText = p.getBoolean("radio_text", d.radioText),
            pty = p.getBoolean("pty", d.pty),
            af = p.getBoolean("af", d.af),
            rdsPresetNames = p.getBoolean("rds_preset_names", d.rdsPresetNames),
            rtPlus = p.getBoolean("rt_plus", d.rtPlus),
            region = runCatching { Region.valueOf(p.getString("region", null).orEmpty()) }.getOrNull(),
            forceMono = p.getBoolean("force_mono", d.forceMono),
            localSearch = p.getBoolean("local_search", d.localSearch),
            trafficAnnouncements = p.getBoolean("traffic_announcements", d.trafficAnnouncements),
            rdsClock = p.getBoolean("rds_clock", d.rdsClock),
            audioMode = runCatching { AudioMode.valueOf(p.getString("audio_mode", null) ?: "") }.getOrDefault(d.audioMode),
            logos = p.getBoolean("logos", d.logos),
            logosOffline = p.getBoolean("logos_offline", d.logosOffline),
            logoServer = p.getString("logo_server", d.logoServer).orEmpty(),
            country = p.getString("country", d.country).orEmpty(),
            webQuality = runCatching { WebQuality.valueOf(p.getString("web_quality", null) ?: "") }.getOrDefault(d.webQuality),
            webSuggest = p.getBoolean("web_suggest", d.webSuggest),
        )
    }
}
