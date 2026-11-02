package org.librehu.fm.web

import org.librehu.fm.StationLogos
import org.librehu.fm.WebQuality
import java.util.Locale

/** A station of the Radio Browser catalogue (fields of its API, "json/stations"). */
data class CatalogStation(
    val uuid: String,
    val name: String,
    /** Stream after the playlists and redirects (url_resolved), else the address given. */
    val url: String,
    val favicon: String = "",
    val country: String = "",
    /** Region as written by the community ("state"), "" when none. */
    val state: String = "",
    val tags: List<String> = emptyList(),
    val language: String = "",
    val codec: String = "",
    val bitrate: Int = 0,
    val hls: Boolean = false,
    val votes: Int = 0,
    val clicks: Int = 0,
) {
    fun toWebStation(): WebStation =
        WebStation(
            id = "rb:$uuid",
            name = name.trim(),
            url = url,
            logoUrl = favicon,
            country = country,
            region = state.trim(),
            codec = codec.takeUnless { it.equals("UNKNOWN", true) }.orEmpty(),
            bitrate = bitrate,
            rbUuid = uuid,
        )
}

/** One station of the list: its stream chosen for the quality setting, and how many streams it has. */
data class CatalogEntry(
    val station: CatalogStation,
    val streams: Int,
)

/** A region of the list, its label (most common spelling) and its number of stations. */
data class CatalogRegion(
    val key: String,
    val label: String,
    val count: Int,
)

/**
 * The catalogue of a country, as the app shows it: grouped by region, filtered by genre and name, one entry per
 * station. Pure functions (tested on the JVM).
 *
 * Regions come from the "state" field, filled by the community: about one French station in five has one (count of
 * 02/11/2026: 760 of 3 547), with several spellings of the same region ("Ile de France", "Île-de-France"), merged
 * here. The others, national networks first, are in [NO_REGION].
 */
object WebCatalog {
    /** Key of the stations without a region (national, or region not given). */
    const val NO_REGION = ""

    /** Same region whatever the accents, case and dashes; the country itself or "Europe" counts as no region. */
    fun regionKey(
        state: String,
        countryName: String = "",
    ): String {
        val k = StationLogos.normalize(state)
        if (k.isEmpty() || k == StationLogos.normalize(countryName) || k in NOT_REGIONS) return NO_REGION
        return k
    }

    /** Regions of [list] with a station, the biggest first; the stations without a region are not listed. */
    fun regions(
        list: List<CatalogStation>,
        countryName: String = "",
    ): List<CatalogRegion> =
        list
            .groupBy { regionKey(it.state, countryName) }
            .filterKeys { it != NO_REGION }
            .map { (key, stations) ->
                val label = stations.groupingBy { it.state.trim() }.eachCount().maxByOrNull { it.value }!!.key
                CatalogRegion(key, label, stations.map { nameKey(it.name) }.distinct().size)
            }.sortedWith(compareByDescending<CatalogRegion> { it.count }.thenBy { it.label })

    /** Tags given to at least [min] stations, the most used first (genres, and networks such as "nrj"). */
    fun genres(
        list: List<CatalogStation>,
        min: Int = 3,
        max: Int = 60,
    ): List<Pair<String, Int>> =
        list
            .flatMap { it.tags }
            .groupingBy { it }
            .eachCount()
            .filter { it.value >= min }
            .toList()
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
            .take(max)

    /**
     * Stations of the region [region] (null = all, [NO_REGION] = without one), with the tag [genre] (null = all) and
     * whose name contains [query]; one entry per station (its streams grouped by [nameKey]), the most listened first.
     */
    fun entries(
        list: List<CatalogStation>,
        quality: WebQuality,
        region: String? = null,
        genre: String? = null,
        query: String = "",
        countryName: String = "",
    ): List<CatalogEntry> {
        val q = StationLogos.normalize(query)
        return list
            .asSequence()
            .filter { region == null || regionKey(it.state, countryName) == region }
            .filter { genre == null || genre in it.tags }
            .filter { q.isEmpty() || StationLogos.normalize(it.name).contains(q) }
            .groupBy { nameKey(it.name) }
            .map { (_, streams) -> CatalogEntry(pick(streams, quality), streams.size) }
            .sortedByDescending { e -> e.station.clicks }
            .toList()
    }

    /**
     * Name of a station without the words that tell its stream apart ("France Inter (AAC 192)", "FIP HiFi"): the
     * streams of a station share it.
     */
    fun nameKey(name: String): String =
        StationLogos
            .normalize(name)
            .split(' ')
            .filter { it.isNotEmpty() && it !in STREAM_WORDS && !it.matches(BITRATE) }
            .joinToString(" ")
            .ifEmpty { StationLogos.normalize(name) }

    /**
     * Sound quality of a stream, for the choice among a station's streams: lossless first, then the bitrate, AAC and
     * Opus counted 1.5 times (same sound for less data than MP3); unknown bitrate taken as a common 96 kbit/s.
     */
    fun quality(s: CatalogStation): Int {
        val codec = s.codec.uppercase(Locale.ROOT)
        if ("FLAC" in codec) return 100_000
        val kbps = if (s.bitrate > 0) s.bitrate else 96
        val factor = if ("AAC" in codec || "OPUS" in codec || "OGG" in codec) 1.5 else 1.0
        return (kbps * factor).toInt()
    }

    /** The stream played for [quality]: the best sound, or the least data (at least 48 kbit/s when there is one). */
    fun pick(
        streams: List<CatalogStation>,
        quality: WebQuality,
    ): CatalogStation =
        when (quality) {
            WebQuality.BEST -> streams.maxWith(compareBy<CatalogStation> { quality(it) }.thenBy { it.clicks })
            WebQuality.ECO ->
                streams.filter { it.bitrate >= ECO_MIN_KBPS }.minByOrNull { it.bitrate }
                    ?: streams.maxWith(compareBy<CatalogStation> { quality(it) }.thenBy { it.clicks })
        }

    /**
     * Internet stream of the FM station named [rdsName] (RDS name, or US call letters) among [candidates]: a match of
     * at least 2 with the logo matcher (same name, or the name it starts with), never a word in common only; the
     * stream for [quality]. Null when nothing matches well enough to switch to it.
     */
    fun matchFm(
        rdsName: String,
        candidates: List<CatalogStation>,
        quality: WebQuality,
    ): CatalogStation? {
        val wanted = StationLogos.normalize(rdsName)
        if (wanted.length < 2) return null
        val scored = candidates.map { it to StationLogos.score(wanted, StationLogos.normalize(it.name)) }
        val best = scored.maxOfOrNull { it.second } ?: return null
        if (best < MIN_FM_SCORE) return null
        val top = scored.filter { it.second == best }.map { it.first }
        val groups = top.groupBy { nameKey(it.name) }
        // The station whose name adds the fewest letters to the RDS name (cut at 8 characters): "NOSTALGI" is
        // Nostalgie, not "Nostalgie Best of 80". Without an exact match, two stations about as close are a doubt
        // ("FRANCE I": France Info or France Inter?): nothing is offered rather than the wrong station.
        val wantedLength = wanted.replace(" ", "").length
        val byExtra = groups.keys.sortedBy { it.replace(" ", "").length - wantedLength }
        if (best < EXACT_SCORE && byExtra.size > 1) {
            val first = byExtra[0].replace(" ", "").length
            val second = byExtra[1].replace(" ", "").length
            if (second - first <= AMBIGUOUS_LETTERS) return null
        }
        return pick(groups.getValue(byExtra[0]), quality)
    }

    private const val ECO_MIN_KBPS = 48
    private const val MIN_FM_SCORE = 2
    private const val EXACT_SCORE = 3
    private const val AMBIGUOUS_LETTERS = 1
    /** Usual stream bitrates only: "Radio 105" keeps its number. */
    private val BITRATE = Regex("(24|32|48|56|64|96|112|128|160|192|256|320)(K|KB|KBPS|KBIT)?")
    private val STREAM_WORDS =
        setOf(
            "AAC", "MP3", "OGG", "OPUS", "FLAC", "HLS", "HD", "HQ", "LQ", "HIFI", "KBPS", "KBIT", "KB",
            "LOW", "HIGH", "MID", "MOBILE", "STREAM", "SD", "320K", "AACP", "LOSSLESS",
        )
    private val NOT_REGIONS = setOf("EUROPE", "WORLD", "INTERNATIONAL", "NATIONAL", "NATIONWIDE", "INTERNET", "ONLINE")
}
