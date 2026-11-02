package org.librehu.fm.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
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
                TextButton(onClick = {
                    FmService.removeWebStation(context, st.id)
                    ui.removing = null
                }) { Text(stringResource(R.string.web_remove)) }
            },
            dismissButton = { TextButton(onClick = { ui.removing = null }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
    ui.logoFor?.let { st ->
        val manual = remember(st.id) { WebLogos.hasManual(context, st.id) }
        CarAlertDialog(
            onDismissRequest = { ui.logoFor = null },
            title = { Text(stringResource(R.string.web_logo_title, st.name)) },
            text = { Text(stringResource(R.string.logo_hint)) },
            confirmButton = {
                TextButton(onClick = {
                    logoTarget = st.id
                    ui.logoFor = null
                    pick.launch("image/*")
                }) { Text(stringResource(R.string.logo_pick)) }
            },
            dismissButton = {
                if (manual) {
                    TextButton(onClick = {
                        ui.logoFor = null
                        WebLogos.clearManual(context, st.id)
                    }) { Text(stringResource(R.string.logo_auto)) }
                } else {
                    TextButton(onClick = { ui.logoFor = null }) { Text(stringResource(android.R.string.cancel)) }
                }
            },
        )
    }
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
            TextButton(enabled = valid, onClick = {
                val u = url.trim()
                FmService.addWebStations(context, listOf(WebStation(name = name.trim().ifBlank { Playlists.nameFromUrl(u) }, url = u)))
                onDismiss()
            }) { Text(stringResource(R.string.web_add_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
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
                unfocusedBorderColor = if (carStyled) KitDim else CarColors.SurfaceHigh,
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

/** The catalogue of the country of the settings, its filters and what they show; [CatalogPickers] draws the choices. */
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
    val entries: List<CatalogEntry>,
    val region: String?,
    val genre: String?,
    val query: String,
    val setQuery: (String) -> Unit,
    val refresh: () -> Unit,
    val openPicker: (Int) -> Unit,
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

    @Composable
    fun countLine(): String =
        when {
            failed -> stringResource(R.string.web_catalog_failed)
            stations == null -> stringResource(R.string.web_catalog_loading)
            else -> stringResource(R.string.web_catalog_count, entries.size, country)
        }

    /** Region of a station when it has one (shown under its name). */
    fun regionOf(st: CatalogStation): String = if (WebCatalog.regionKey(st.state, countryName).isEmpty()) "" else st.state
}

/**
 * Downloads the catalogue once a week (the kept copy is shown at once, and without network), keeps the filters
 * (region, genre, name) and works out the entries for the quality setting.
 */
@Composable
internal fun rememberCatalogUi(): Pair<CatalogUi, @Composable () -> Unit> {
    val context = LocalContext.current
    val settings by RadioSettings.get(context).collectAsStateWithLifecycle()
    val country = settings.countryCode
    val countryName = remember(country) { runCatching { Locale.Builder().setRegion(country).build().getDisplayCountry(Locale.ENGLISH) }.getOrDefault("") }
    val store = remember { CatalogStore(context.applicationContext) }
    var stations by remember(country) { mutableStateOf<List<CatalogStation>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var region by rememberSaveable(country) { mutableStateOf<String?>(null) }
    var genre by rememberSaveable(country) { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var picker by remember { mutableIntStateOf(0) }

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

    val list = stations.orEmpty()
    val regions = remember(list, countryName) { WebCatalog.regions(list, countryName) }
    val genres = remember(list) { WebCatalog.genres(list) }
    val entries = remember(list, region, genre, query, settings.webQuality) { WebCatalog.entries(list, settings.webQuality, region, genre, query, countryName) }
    val ui =
        CatalogUi(
            settings, country, countryName, stations, loading, failed, regions, genres, entries, region, genre, query,
            setQuery = { query = it },
            refresh = { refresh++ },
            openPicker = { picker = it },
        )
    val pickers: @Composable () -> Unit = {
        when (picker) {
            1 ->
                PickerDialog(
                    title = stringResource(R.string.web_region),
                    options =
                        listOf<Pair<String?, String>>(null to stringResource(R.string.web_region_all)) +
                            regions.map { it.key to "${it.label} (${it.count})" } +
                            (WebCatalog.NO_REGION to stringResource(R.string.web_region_none)),
                    onPick = {
                        region = it
                        picker = 0
                    },
                    onDismiss = { picker = 0 },
                )

            2 ->
                PickerDialog(
                    title = stringResource(R.string.web_genre),
                    options = listOf<Pair<String?, String>>(null to stringResource(R.string.web_genre_all)) + genres.map { it.first to "${it.first} (${it.second})" },
                    onPick = {
                        genre = it
                        picker = 0
                    },
                    onDismiss = { picker = 0 },
                )
        }
    }
    return ui to pickers
}

@Composable
private fun PickerDialog(
    title: String,
    options: List<Pair<String?, String>>,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    CarAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(options) { (value, label) ->
                    Text(
                        label,
                        color = if (carStyled) KitLight else CarColors.Text,
                        fontSize = 18.sp,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(value) }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}
