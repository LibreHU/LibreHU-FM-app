package org.librehu.fm

import android.content.Context
import android.graphics.Bitmap
import org.json.JSONArray
import org.json.JSONObject
import org.librehu.fm.web.WebStation
import java.util.Locale

/** One saved station. [frequency] in 100 kHz units (1015 = 101.5 MHz). */
data class Station(
    val frequency: Int,
    val name: String = "",
)

/** What the radio plays: the FM chip, or an internet stream. */
enum class Source { FM, WEB }

data class RadioState(
    val available: Boolean = true,
    val error: String = "",
    val poweredOn: Boolean = false,
    val busy: Boolean = false,
    val scanning: Boolean = false,
    val frequency: Int = Band.DEFAULT,
    val ps: String = "",
    val radioText: String = "",
    /** Song playing, from the RT+ tags of the radiotext (empty when the station sends none). */
    val song: String = "",
    val artist: String = "",
    val presets: List<Station> = emptyList(),
    val stations: List<Station> = emptyList(),
    /** Logo of the current station (Radio Browser, cached), when found. */
    val logo: Bitmap? = null,
    /** Call letters computed from the PI code (US stations, [Rbds]), "" elsewhere. */
    val callSign: String = "",
    /** RDS programme type (0 = none, 1–31 per EN 50067), traffic programme / announcement flags. */
    val pty: Int = 0,
    val tp: Boolean = false,
    val ta: Boolean = false,
    /** Audio path in use, for the settings screen. */
    val audioPath: FmAudio.Path = FmAudio.Path.NONE,
    /** Signal of the tuned station in dBm (null: unknown) and stereo reception. */
    val rssi: Int? = null,
    val stereo: Boolean = false,
    /** Programme type 31: alarm (RDS) / emergency (RBDS) on air. */
    val alarm: Boolean = false,
    /** Chip kept on for the traffic announcements while another source plays, and an announcement playing. */
    val standby: Boolean = false,
    val announcing: Boolean = false,
    /** Forced mono asked but refused by the FM driver. */
    val monoUnsupported: Boolean = false,
    /** Source played (or played last): FM or internet. */
    val source: Source = Source.FM,
    /** Internet stations saved, and the one played (or last played). */
    val webStations: List<WebStation> = emptyList(),
    val web: WebStation? = null,
    /** Internet stream: wanted by the user, playing, buffering, reconnecting (after a network loss). */
    val webWanted: Boolean = false,
    val webPlaying: Boolean = false,
    val webBuffering: Boolean = false,
    val webRetrying: Boolean = false,
    /** "Artist - Title" sent by the stream (ICY), "" when none. */
    val webTitle: String = "",
    /** Audio of the stream: codec label ("AAC", "MP3"…), kbit/s (0 = unknown). */
    val webCodec: String = "",
    val webBitrate: Int = 0,
    /** Logo of the internet station (its address in the catalogue), when found. */
    val webLogo: Bitmap? = null,
) {
    /** Sound coming out: the FM chip or the internet stream. */
    val playing: Boolean get() = if (source == Source.WEB) webWanted else poweredOn

    /** Station name for the media card, the notification and the widget, whatever the source. */
    val shownTitle: String get() = if (source == Source.WEB) web?.name.orEmpty() else title

    /** Second line, whatever the source: the stream's title for an internet station. */
    val shownNowPlaying: String get() = if (source == Source.WEB) webTitle else nowPlaying

    val shownLogo: Bitmap? get() = if (source == Source.WEB) webLogo else logo

    val isPreset: Boolean get() = presets.any { it.frequency == frequency }

    /** Station name: RDS PS, else the preset name, else the call letters. */
    val title: String
        get() = ps.ifBlank { presets.firstOrNull { it.frequency == frequency }?.name.orEmpty() }.ifBlank { callSign }

    /** Name to look the logo up with: the call letters (stable, US) before the PS, which some stations scroll. */
    val logoName: String
        get() = callSign.ifBlank { title }.trim()

    /** Signal bars, 0 to 4 (dBµV ≈ dBm + 107: about 17, 27, 37 and 47 dBµV), null when unknown. */
    val bars: Int?
        get() = rssi?.let { r -> listOf(-90, -80, -70, -60).count { r >= it } }

    /** Second line of the media card, the notification and the widget: "artist – title" (RT+), else the radiotext. */
    val nowPlaying: String
        get() = listOf(artist, song).filter { it.isNotBlank() }.joinToString(" – ").ifBlank { radioText }
}

/**
 * FM band plan of a part of the world, frequencies in 100 kHz units. Sources (Wikipedia, "FM broadcast band" and "FM
 * broadcasting"): channels every 200 kHz on odd tenths in the Americas (ITU region 2: 87.9 to 107.9 MHz in the United
 * States), the same odd tenths in South Korea; 76 to 95 MHz in Japan; Brazil's extended band (eFM, channels 5 and 6,
 * 76.1 to 87.5 MHz, on air since May 2021); 100 kHz elsewhere; de-emphasis of 75 µs in the Americas and South Korea,
 * 50 µs in the rest of the world.
 */
enum class Region(
    val min: Int,
    val max: Int,
    /** Tuning step: 1 = 100 kHz; 2 = 200 kHz, on the odd tenths counted from 87.5 MHz. */
    val step: Int,
    val default: Int,
    /** 75 µs de-emphasis instead of 50 µs (set when the chip powers up). */
    val deemphasis75: Boolean,
    /** North American RDS (RBDS): programme types with other names. */
    val rbds: Boolean,
    /**
     * Band of the MediaTek FM library: FM_BAND_UE (1), FM_BAND_JAPAN (2) or FM_BAND_SPECIAL (4: limits of [min] and [max],
     * channels counted from [min]).
     */
    val chipBand: Int,
) {
    EUROPE(875, 1080, 1, 1000, false, false, 1),
    AMERICAS(875, 1079, 2, 1001, true, true, 1),

    /** Brazil with its extended band: 76.1 to 107.9 MHz on the odd tenths; RDS programme types (RBDS is the US one). */
    BRAZIL(761, 1079, 2, 1001, true, false, 4),
    JAPAN(760, 950, 1, 800, false, false, 2),
    ;

    /** Names of the programme types (PTY 0–31): RDS (EN 50067) or RBDS (North America). */
    val ptyNames: Int get() = if (rbds) R.array.pty_names_rbds else R.array.pty_names

    companion object {
        /** Sovereign states of the Americas (Brazil apart), the US territories and South Korea. */
        private val AMERICAS_COUNTRIES =
            setOf(
                "AG", "AR", "BB", "BO", "BS", "BZ", "CA", "CL", "CO", "CR", "CU", "DM", "DO", "EC", "GD", "GT", "GY",
                "HN", "HT", "JM", "KN", "LC", "MX", "NI", "PA", "PE", "PY", "SR", "SV", "TT", "US", "UY", "VC", "VE",
                "AS", "GU", "MP", "PR", "UM", "VI", "KR",
            )

        /** Band plan of a country (ISO 3166 code). */
        fun forCountry(code: String): Region =
            when (code.uppercase(Locale.ROOT)) {
                "JP" -> JAPAN
                "BR" -> BRAZIL
                in AMERICAS_COUNTRIES -> AMERICAS
                else -> EUROPE
            }
    }
}

/** Limits and step of the band of the chosen [Region] (set by [RadioSettings]). */
object Band {
    @Volatile
    var region: Region = Region.EUROPE

    val MIN: Int get() = region.min
    val MAX: Int get() = region.max
    val STEP: Int get() = region.step
    val DEFAULT: Int get() = region.default

    fun mhz(frequency: Int): Float = frequency / 10f

    fun format(frequency: Int): String = "%d.%d".format(frequency / 10, frequency % 10)

    /** In the band and on its channels (odd tenths with 200 kHz). */
    fun clamp(frequency: Int): Int {
        val r = region
        val f = frequency.coerceIn(r.min, r.max)
        return (r.min + (f - r.min + r.step / 2) / r.step * r.step).coerceAtMost(r.max)
    }

    /** Labels of the tuning scale: whole MHz every 4 MHz (108 for a band ending at 107.9). */
    fun scaleLabels(): List<Int> {
        val r = region
        val first = (r.min + 39) / 40 * 40
        return (first..r.max + 1 step 40).toList()
    }
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

    /** Internet stations saved, in the user's order. */
    var webStations: List<WebStation>
        get() = WebStation.listFromJson(prefs.getString("web_stations", "[]"))
        set(value) = prefs.edit().putString("web_stations", WebStation.listToJson(value)).apply()

    /** Source played last, and the internet station played last (its id). */
    var source: Source
        get() = if (prefs.getString("source", "fm") == "web") Source.WEB else Source.FM
        set(value) = prefs.edit().putString("source", if (value == Source.WEB) "web" else "fm").apply()

    var webCurrent: String
        get() = prefs.getString("web_current", "").orEmpty()
        set(value) = prefs.edit().putString("web_current", value).apply()

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
