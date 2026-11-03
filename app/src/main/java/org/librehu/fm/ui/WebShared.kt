package org.librehu.fm.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.librehu.fm.FmService
import org.librehu.fm.R
import org.librehu.fm.RadioSettings
import org.librehu.fm.RadioState
import org.librehu.fm.Settings
import org.librehu.fm.Source
import org.librehu.fm.web.CatalogEntry
import org.librehu.fm.web.CatalogRegion
import org.librehu.fm.web.CatalogStation
import org.librehu.fm.web.CatalogStore
import org.librehu.fm.web.Playlists
import org.librehu.fm.web.WebCatalog
import org.librehu.fm.web.WebLogos
import org.librehu.fm.web.WebPlayer
import org.librehu.fm.web.WebStation

/*
 * What the three looks of the internet radio (modern, factory, LHU-Link) share: the windows (add a station, import a
 * file, remove, choose a logo), the texts of the now-playing line, and the catalogue (download, regions, genres,
 * search). Each look only draws them.
 */

/** Windows of the internet radio, opened from any look; [WebDialogs] draws the open one. */
internal class WebUi(
    private val importLauncher: ManagedActivityResultLauncher<Array<String>, Uri?>,
) {
    var adding by mutableStateOf(false)
    var removing by mutableStateOf<WebStation?>(null)
    var logoFor by mutableStateOf<WebStation?>(null)

    /** Any type: file managers and USB keys often give playlists no MIME type, or a wrong one. */
    fun import() = importLauncher.launch(arrayOf("*/*"))
}

@Composable
internal fun rememberWebUi(): WebUi {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) importPlaylist(context, uri) }
    return remember(launcher) { WebUi(launcher) }
}

@Composable
internal fun WebDialogs(ui: WebUi) {
    val context = LocalContext.current
    // Registered all the time: the window closes before the picture picker answers.
    var logoTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val pick =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            val id = logoTarget
            if (uri != null && id != null) Thread({ WebLogos.setManual(context.applicationContext, id, uri) }, "web-logo").start()
        }
    if (ui.adding) AddWebStationDialog(onDismiss = { ui.adding = false })
    ui.removing?.let { st ->
        CarAlertDialog(
            onDismissRequest = { ui.removing = null },
            title = { Text(st.name) },
            text = { Text(stringResource(R.string.web_remove_confirm)) },
            confirmButton = {
                DialogKey(stringResource(R.string.web_remove)) {
                    FmService.removeWebStation(context, st.id)
                    ui.removing = null
                }
            },
            dismissButton = { DialogKey(stringResource(android.R.string.cancel)) { ui.removing = null } },
        )
    }
    ui.logoFor?.let { st ->
        val manual = remember(st.id) { WebLogos.hasManual(context, st.id) }
        CarAlertDialog(
            onDismissRequest = { ui.logoFor = null },
            title = { Text(stringResource(R.string.web_logo_title, st.name)) },
            text = { Text(stringResource(R.string.logo_hint)) },
            confirmButton = {
                DialogKey(stringResource(R.string.logo_pick)) {
                    logoTarget = st.id
                    ui.logoFor = null
                    pick.launch("image/*")
                }
            },
            dismissButton = {
                if (manual) {
                    DialogKey(stringResource(R.string.logo_auto)) {
                        ui.logoFor = null
                        WebLogos.clearManual(context, st.id)
                    }
                } else {
                    DialogKey(stringResource(android.R.string.cancel)) { ui.logoFor = null }
                }
            },
        )
    }
}

/** Key of a dialog at the car touch size ([TOUCH] high). */
@Composable
private fun DialogKey(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.heightIn(min = TOUCH)) { Text(label, fontSize = 18.sp) }
}

/** The internet station is the source and the user wants it to play (it may be connecting). */
internal val RadioState.webActive: Boolean get() = source == Source.WEB && webWanted

/** The station shown is not in the saved list (catalogue, FM offer): it can be saved. */
internal val RadioState.webCanSave: Boolean get() = web != null && webStations.none { it.url == web.url }

/** Codec and bitrate of the stream played ("AAC 128 kbit/s"), from the stream itself; "" when unknown. */
internal fun webAudio(s: RadioState): String =
    if (s.source != Source.WEB) "" else listOf(s.webCodec, if (s.webBitrate > 0) "${s.webBitrate} kbit/s" else "").filter { it.isNotBlank() }.joinToString(" ")

/** Codec and bitrate announced for a station (catalogue, offer), "AAC 192". */
internal fun stationAudio(
    codec: String,
    bitrate: Int,
): String = listOf(codec.takeUnless { it.equals("UNKNOWN", true) }.orEmpty(), if (bitrate > 0) "$bitrate" else "").filter { it.isNotBlank() }.joinToString(" ")

/** The line under the station name: connection, reconnection, song title, or off. */
@Composable
internal fun webStatus(s: RadioState): String =
    when {
        s.source != Source.WEB || !s.webWanted -> stringResource(if (s.web == null) R.string.web_empty_hint else R.string.off)
        s.webRetrying -> stringResource(R.string.web_reconnecting)
        s.webBuffering && !s.webPlaying -> stringResource(R.string.web_connecting)
        else -> s.webTitle
    }

/** Play / pause of the internet station shown (the last one, or the first saved). */
internal fun webToggle(
    context: Context,
    s: RadioState,
    send: (String, Int?) -> Unit,
) {
    val st = s.web
    when {
        s.webActive -> send(FmService.ACTION_PAUSE, null)
        st != null -> FmService.playWebStation(context, st)
    }
}

/** Text of the FM screen's offer of the station's internet stream. */
@Composable
internal fun offerText(st: WebStation): String {
    val audio = listOf(st.codec, if (st.bitrate > 0) "${st.bitrate} kbit/s" else "").filter { it.isNotBlank() }.joinToString(" ")
    return if (audio.isBlank()) stringResource(R.string.web_offer) else stringResource(R.string.web_offer_audio, audio)
}

/** The FM station's internet stream is offered: radio on, internet working, a stream found. */
internal val RadioState.webOffer: WebStation? get() = fmWeb?.takeIf { poweredOn && online }

// --- Add, import ---------------------------------------------------------------------------------------------------

/** Station added by hand: its address (stream, or M3U / PLS link) and a name (the address's host when left empty). */
@Composable
private fun AddWebStationDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    val valid = Playlists.isStreamUrl(url.trim())
    CarAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.web_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.web_add_hint), color = CarColors.TextDim, fontSize = 15.sp)
                WebField(url, { url = it }, stringResource(R.string.web_address), "https://…", KeyboardType.Uri)
                WebField(name, { name = it }, stringResource(R.string.web_name), stringResource(R.string.web_name_hint), KeyboardType.Text)
            }
        },
        confirmButton = {
            DialogKey(stringResource(R.string.web_add_confirm), enabled = valid) {
                val u = url.trim()
                FmService.addWebStations(context, listOf(WebStation(name = name.trim().ifBlank { Playlists.nameFromUrl(u) }, url = u)))
                onDismiss()
            }
        },
        dismissButton = { DialogKey(stringResource(android.R.string.cancel), onClick = onDismiss) },
    )
}

/** A text field in the colours of the look (white on black in the car styles). */
@Composable
internal fun WebField(
    value: String,
    onValue: (String) -> Unit,
    label: String?,
    placeholder: String,
    type: KeyboardType,
    modifier: Modifier = Modifier.fillMaxWidth(),
    imeAction: ImeAction = ImeAction.Next,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = label?.let { { Text(it) } },
        placeholder = { Text(placeholder) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = type, imeAction = imeAction),
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedTextColor = if (carStyled) KitLight else CarColors.Text,
                unfocusedTextColor = if (carStyled) KitLight else CarColors.Text,
                focusedBorderColor = CarColors.Accent,
                unfocusedBorderColor = if (carStyled) KitDim else CarColors.TextDim,
                focusedLabelColor = CarColors.Accent,
                unfocusedLabelColor = if (carStyled) KitDim else CarColors.TextDim,
                focusedPlaceholderColor = if (carStyled) KitDim else CarColors.TextDim,
                unfocusedPlaceholderColor = if (carStyled) KitDim else CarColors.TextDim,
                cursorColor = CarColors.Accent,
            ),
        modifier = modifier,
    )
}

/** Every station of an M3U / PLS file, added to the list (names from #EXTINF / TitleN, else the host). */
private fun importPlaylist(
    context: Context,
    uri: Uri,
) {
    Thread({
        val stations =
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { input -> input.readNBytesCompat(MAX_IMPORT_BYTES) } ?: ByteArray(0)
                Playlists.parse(WebPlayer.decode(bytes)).map { e -> WebStation(name = e.name.ifBlank { Playlists.nameFromUrl(e.url) }, url = e.url) }
            } catch (_: Exception) {
                emptyList()
            }
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            FmService.addWebStations(context, stations)
            val msg =
                if (stations.isEmpty()) {
                    context.getString(R.string.web_import_none)
                } else {
                    context.resources.getQuantityString(R.plurals.web_imported, stations.size, stations.size)
                }
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }, "web-import").start()
}

/** A playlist is a few kilobytes: a big file chosen by mistake is not read whole. */
private const val MAX_IMPORT_BYTES = 4 * 1024 * 1024

private fun java.io.InputStream.readNBytesCompat(max: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(8192)
    while (out.size() < max) {
        val n = read(buf)
        if (n < 0) break
        out.write(buf, 0, minOf(n, max - out.size()))
    }
    return out.toByteArray()
}

// --- Catalogue -----------------------------------------------------------------------------------------------------

/** What the catalogue shows instead of its list: a grid of big tiles to choose the country, the region or the genre. */
internal enum class CatalogPick { NONE, COUNTRY, REGION, GENRE }

/** A tile of the choice grid: its value ([key] null = all), its name and its number of stations. */
internal class PickTile(
    val key: String?,
    val label: String,
    val count: Int = 0,
    /** Opens the second page of the grid (the small regions, the other countries…). */
    val more: Boolean = false,
)

/** The catalogue of a country, its filters and what they show; [CatalogGrid] draws the choice grid. */
internal class CatalogUi(
    val settings: Settings,
    /** Country of the list shown (ISO 3166 code) and its English name (Radio Browser writes it so as a region). */
    val country: String,
    val countryName: String,
    val stations: List<CatalogStation>?,
    val loading: Boolean,
    val failed: Boolean,
    val regions: List<CatalogRegion>,
    val genres: List<Pair<String, Int>>,
    val countries: List<Pair<String, Int>>,
    val entries: List<CatalogEntry>,
    val region: String?,
    val genre: String?,
    val query: String,
    val pick: CatalogPick,
    val more: Boolean,
    val setQuery: (String) -> Unit,
    val refresh: () -> Unit,
    val open: (CatalogPick) -> Unit,
    val showMore: () -> Unit,
    val choose: (String?) -> Unit,
) {
    @Composable
    fun regionLabel(): String =
        when (region) {
            null -> stringResource(R.string.web_region_all)
            WebCatalog.NO_REGION -> stringResource(R.string.web_region_none)
            else -> regions.firstOrNull { it.key == region }?.label ?: region
        }

    @Composable
    fun genreLabel(): String = genre ?: stringResource(R.string.web_genre_all)

    /** The country in the user's language ("France", "États-Unis"). */
    fun countryLabel(): String = countryDisplay(country)

    @Composable
    fun countLine(): String =
        when {
            failed -> stringResource(R.string.web_catalog_failed)
            stations == null -> stringResource(R.string.web_catalog_loading)
            else -> stringResource(R.string.web_catalog_count, entries.size, countryLabel())
        }

    /** Region of a station when it has one (shown under its name). */
    fun regionOf(st: CatalogStation): String = if (WebCatalog.regionKey(st.state, countryName).isEmpty()) "" else st.state

    /**
     * Tiles of the open choice. First page: "all", the biggest regions (5 stations or more: the 19 French regions with
     * the most stations fit on one screen), the national ones, then "other regions"; second page: the small ones, A to Z.
     * Same for the genres (the 21 most used first) and the countries (the head unit's first, then by size).
     */
    @Composable
    fun tiles(): List<PickTile> =
        when (pick) {
            CatalogPick.REGION -> {
                val big = regions.filter { it.count >= BIG_REGION }
                val small = regions.filter { it.count < BIG_REGION }.sortedBy { it.label.lowercase(Locale.ROOT) }
                if (more) {
                    small.map { PickTile(it.key, it.label, it.count) }
                } else {
                    listOf(PickTile(null, stringResource(R.string.web_region_all))) +
                        big.map { PickTile(it.key, it.label, it.count) } +
                        PickTile(WebCatalog.NO_REGION, stringResource(R.string.web_region_none)) +
                        (if (small.isEmpty()) emptyList() else listOf(PickTile(null, stringResource(R.string.web_region_other, small.size), more = true)))
                }
            }

            CatalogPick.GENRE ->
                if (more) {
                    genres.drop(FIRST_GENRES).sortedBy { it.first }.map { PickTile(it.first, it.first, it.second) }
                } else {
                    listOf(PickTile(null, stringResource(R.string.web_genre_all))) +
                        genres.take(FIRST_GENRES).map { PickTile(it.first, it.first, it.second) } +
                        (if (genres.size > FIRST_GENRES) listOf(PickTile(null, stringResource(R.string.web_genre_other, genres.size - FIRST_GENRES), more = true)) else emptyList())
                }

            CatalogPick.COUNTRY -> {
                val home = settings.countryCode
                val ordered = countries.sortedByDescending { if (it.first == home) Int.MAX_VALUE else it.second }
                if (more) {
                    ordered.drop(FIRST_COUNTRIES).sortedBy { countryDisplay(it.first) }.map { PickTile(it.first, countryDisplay(it.first), it.second) }
                } else {
                    ordered.take(FIRST_COUNTRIES).map { PickTile(it.first, countryDisplay(it.first), it.second) } +
                        (if (ordered.size > FIRST_COUNTRIES) listOf(PickTile(null, stringResource(R.string.web_country_other, ordered.size - FIRST_COUNTRIES), more = true)) else emptyList())
                }
            }

            CatalogPick.NONE -> emptyList()
        }

    /** The tile of the value in use. */
    fun isChosen(t: PickTile): Boolean =
        !t.more &&
            when (pick) {
                CatalogPick.REGION -> t.key == region
                CatalogPick.GENRE -> t.key == genre
                CatalogPick.COUNTRY -> t.key == country
                CatalogPick.NONE -> false
            }

    companion object {
        private const val BIG_REGION = 5
        private const val FIRST_GENRES = 21
        private const val FIRST_COUNTRIES = 23

        fun countryDisplay(iso: String): String =
            runCatching { Locale.Builder().setRegion(iso).build().displayCountry }.getOrNull()?.ifBlank { null } ?: iso
    }
}

/**
 * Downloads the catalogue of the chosen country once a week (the kept copy is shown at once, and without network),
 * keeps the filters (country, region, genre, name) and works out the entries for the quality setting.
 */
@Composable
internal fun rememberCatalogUi(): CatalogUi {
    val context = LocalContext.current
    val settings by RadioSettings.get(context).collectAsStateWithLifecycle()
    val country = settings.webCountryCode
    val countryName = remember(country) { runCatching { Locale.Builder().setRegion(country).build().getDisplayCountry(Locale.ENGLISH) }.getOrDefault("") }
    val store = remember { CatalogStore(context.applicationContext) }
    var stations by remember(country) { mutableStateOf<List<CatalogStation>?>(null) }
    var countries by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var region by rememberSaveable(country) { mutableStateOf<String?>(null) }
    var genre by rememberSaveable(country) { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var pick by rememberSaveable { mutableStateOf(CatalogPick.NONE) }
    var more by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(country, refresh) {
        loading = true
        failed = false
        val kept = withContext(Dispatchers.IO) { store.cached(country) }
        if (kept != null) stations = kept.first
        if (kept == null || refresh > 0 || !store.isFresh(country)) {
            val fresh = withContext(Dispatchers.IO) { store.download(settings.serverUrl, country) }
            if (fresh != null) stations = fresh else failed = stations == null
        }
        loading = false
    }
    LaunchedEffect(pick) {
        if (pick == CatalogPick.COUNTRY && countries.isEmpty()) countries = withContext(Dispatchers.IO) { store.countries(settings.serverUrl) }
    }

    val list = stations.orEmpty()
    val regions = remember(list, countryName) { WebCatalog.regions(list, countryName) }
    val genres = remember(list) { WebCatalog.genres(list, max = 200) }
    val entries = remember(list, region, genre, query, settings.webQuality) { WebCatalog.entries(list, settings.webQuality, region, genre, query, countryName) }
    return CatalogUi(
        settings, country, countryName, stations, loading, failed, regions, genres, countries, entries, region, genre, query, pick, more,
        setQuery = { query = it },
        refresh = { refresh++ },
        open = {
            pick = it
            more = false
        },
        showMore = { more = true },
        choose = { key ->
            when (pick) {
                CatalogPick.REGION -> region = key
                CatalogPick.GENRE -> genre = key
                CatalogPick.COUNTRY -> if (key != null) RadioSettings.update(context) { it.copy(webCountry = if (key == it.countryCode) "" else key) }
                CatalogPick.NONE -> {}
            }
            pick = CatalogPick.NONE
            more = false
        },
    )
}

/**
 * The choice grid, in place of the list: big tiles (name and number of stations), the value in use lit, a touch
 * chooses; a title line with Back (to the first page, then to the list). Drawn in the look of the screen.
 */
@Composable
internal fun CatalogGrid(
    cat: CatalogUi,
    modifier: Modifier = Modifier,
    minTile: Dp = 180.dp,
) {
    val tiles = cat.tiles()
    val title =
        stringResource(
            when (cat.pick) {
                CatalogPick.COUNTRY -> R.string.web_country
                CatalogPick.REGION -> R.string.web_region
                else -> R.string.web_genre
            },
        )
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
            GridTile(PickTile(null, "‹  " + stringResource(R.string.web_back)), chosen = false, Modifier.width(minTile)) {
                if (cat.more) cat.open(cat.pick) else cat.open(CatalogPick.NONE)
            }
            Spacer(Modifier.width(16.dp))
            Text(title, color = if (carStyled) KitLight else CarColors.Text, fontSize = 22.sp)
        }
        if (tiles.isEmpty()) {
            Text(stringResource(R.string.web_catalog_loading), color = if (carStyled) KitDim else CarColors.TextDim, fontSize = 18.sp)
            return
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minTile),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(tiles.size) { i ->
                val t = tiles[i]
                GridTile(t, cat.isChosen(t), Modifier.fillMaxWidth()) { if (t.more) cat.showMore() else cat.choose(t.key) }
            }
        }
    }
}

/** A big tile of the grid: a white frame (factory), the lit key (LHU-Link), a rounded tile (modern). */
@Composable
private fun GridTile(
    t: PickTile,
    chosen: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val content: @Composable () -> Unit = {
        Column(Modifier.padding(horizontal = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val fg =
                when {
                    RetroLook.factory && chosen -> CarColors.Accent
                    carStyled -> KitLight
                    chosen -> CarColors.OnAccent
                    else -> CarColors.Text
                }
            // Long names on two lines, smaller if needed; a line may end after a hyphen ("Provence-Alpes-" / "Côte d'Azur")
            // but never inside a word.
            BasicText(
                t.label.replace("-", "-\u200B"),
                style = TextStyle(color = fg, textAlign = TextAlign.Center, lineHeight = 1.15.em),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 18.sp),
            )
            if (t.count > 0) Text(t.count.toString(), color = fg.copy(alpha = 0.7f), fontSize = 13.sp)
        }
    }
    val m = modifier.height(76.dp)
    when {
        RetroLook.factory ->
            FramedKey(m.then(if (chosen) Modifier.border(BorderStroke(1.5.dp, CarColors.Accent)) else Modifier), onClick = onClick) { content() }

        RetroLook.ambient -> AmbientGridKey(m, selected = chosen, onClick = onClick) { content() }

        else ->
            Box(
                m
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (chosen) CarColors.Accent else CarColors.SurfaceHigh)
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center,
            ) { content() }
    }
}
