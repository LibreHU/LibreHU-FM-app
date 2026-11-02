package org.librehu.fm.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.librehu.fm.Band
import org.librehu.fm.FmService
import org.librehu.fm.R
import org.librehu.fm.RadioState
import org.librehu.fm.Source
import org.librehu.fm.web.CatalogStation

/*
 * The internet radio in LibreHU Launcher's two car styles, with the same pieces as their FM screens.
 * Factory (Modularer Infotainment-Baukasten): the station between framed ‹ › keys (previous / next saved station),
 * pages of six saved station tiles, the catalogue as a list; soft keys FM / Catalogue / play / Add / Import / Setup.
 * Ambient (LHU-Link): tabs Playing / Internet stations / Catalogue in framed panels, the station panel with its side
 * keys, lists on the red selection line; bar FM / play, Options (add, import, save, logo, refresh, settings).
 */

private const val STATIONS_BY_PAGE = 6
private val DIM_GREY = Color(0xFFD2D3D8)

/** Sends [st] to the player: a saved station by its id, a catalogue one whole. */
private fun playCatalog(
    context: android.content.Context,
    st: CatalogStation,
) = FmService.playWebStation(context, st.toWebStation())

// --- Factory -------------------------------------------------------------------------------------------------------

@Composable
internal fun FactoryWebRadio(
    s: RadioState,
    send: (String, Int?) -> Unit,
    onSettings: () -> Unit,
    onInternet: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val ui = rememberWebUi()
    var catalog by rememberSaveable { mutableStateOf(false) }
    FactoryScreen(
        title = null,
        back = false,
        keys =
            listOf(
                SoftKey(stringResource(R.string.source_fm), Icons.Outlined.Radio) { onInternet(false) },
                SoftKey(stringResource(R.string.web_catalog), Icons.Outlined.Public, catalog) { catalog = !catalog },
                SoftKey(
                    stringResource(if (s.webActive) R.string.pause else R.string.play),
                    if (s.webActive) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                ) { webToggle(context, s, send) },
                SoftKey(stringResource(R.string.web_add_short), Icons.Outlined.Add) { ui.adding = true },
                SoftKey(stringResource(R.string.web_import_short), Icons.Outlined.FileOpen) { ui.import() },
                null,
                SoftKey(stringResource(R.string.car_setup), Icons.Outlined.Settings, onClick = onSettings),
            ),
    ) {
        if (catalog) {
            FactoryCatalog(s)
        } else {
            Column(Modifier.fillMaxSize()) {
                FactoryWebStation(s, send, ui)
                Box(Modifier.weight(1f).fillMaxWidth()) { FactoryWebTiles(s, ui) }
            }
        }
    }
    WebDialogs(ui)
}

/**
 * The station between framed ‹ › keys (previous / next saved station), its state or song under it, and framed keys
 * for what applies: save it, its logo, back to the FM station it came from.
 */
@Composable
private fun FactoryWebStation(
    s: RadioState,
    send: (String, Int?) -> Unit,
    ui: WebUi,
) {
    val context = LocalContext.current
    val active = s.source == Source.WEB
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.Top) {
        FramedKey(onClick = { if (active) send(FmService.ACTION_PREVIOUS, null) else s.webStations.lastOrNull()?.let { FmService.playWeb(context, it.id) } }) {
            Icon(Icons.Outlined.ChevronLeft, stringResource(R.string.previous), tint = Color.White, modifier = Modifier.size(40.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (s.webActive && !s.webPlaying) {
                    CircularProgressIndicator(color = CarColors.Accent, strokeWidth = 3.dp, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.width(14.dp))
                }
                Text(
                    s.web?.name ?: stringResource(R.string.web_none),
                    color = Color.White,
                    fontSize = 40.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val line = listOf(webStatus(s), webAudio(s)).filter { it.isNotBlank() }.joinToString("  ·  ")
            Text(line.ifEmpty { " " }, color = DIM_GREY, fontSize = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        FramedKey(onClick = { if (active) send(FmService.ACTION_NEXT, null) else s.webStations.firstOrNull()?.let { FmService.playWeb(context, it.id) } }) {
            Icon(Icons.Outlined.ChevronRight, stringResource(R.string.next), tint = Color.White, modifier = Modifier.size(40.dp))
        }
    }
    val current = s.web
    if (current != null) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            if (s.webCanSave) FramedTextKey(stringResource(R.string.web_save)) { FmService.addWebStations(context, listOf(current)) }
            FramedTextKey(stringResource(R.string.web_logo)) { ui.logoFor = current }
            if (active && s.webFromFm != 0) {
                FramedTextKey(stringResource(R.string.web_back_to_fm, Band.format(s.webFromFm))) {
                    send(FmService.ACTION_TUNE, s.webFromFm)
                    send(FmService.ACTION_PLAY_FM, null)
                }
            }
        }
    }
}

/**
 * Pages of six saved station tiles, numbered, like the FM presets: a touch plays, a long touch offers to remove it;
 * the station playing shows its logo.
 */
@Composable
private fun FactoryWebTiles(
    s: RadioState,
    ui: WebUi,
) {
    val context = LocalContext.current
    if (s.webStations.isEmpty()) {
        Text(
            stringResource(R.string.web_no_stations),
            color = DIM_GREY,
            fontSize = 22.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(32.dp),
        )
        return
    }
    val pages = (s.webStations.size + STATIONS_BY_PAGE - 1) / STATIONS_BY_PAGE
    val pager = rememberPagerState { pages }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(pager, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(STATIONS_BY_PAGE) { k ->
                    val i = page * STATIONS_BY_PAGE + k
                    val st = s.webStations.getOrNull(i)
                    val current = st != null && s.source == Source.WEB && s.web?.id == st.id
                    FactoryTile(
                        label = st?.name.orEmpty(),
                        selected = current,
                        aspect = 1.45f,
                        modifier = Modifier.weight(1f),
                        onLongClick = { if (st != null) ui.removing = st },
                        onClick = { if (st != null) FmService.playWeb(context, st.id) },
                    ) {
                        if (st != null) {
                            Text((i + 1).toString(), color = Color.White, fontSize = 30.sp, modifier = Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 12.dp))
                            val logo = s.webLogo
                            if (current && logo != null) {
                                val image = remember(logo) { logo.asImageBitmap() }
                                Image(image, st.name, contentScale = ContentScale.Fit, modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.62f).fillMaxHeight(0.42f))
                            } else {
                                val audio = stationAudio(st.codec, st.bitrate)
                                if (audio.isNotBlank()) Text(audio, color = DIM_GREY, fontSize = 16.sp, modifier = Modifier.align(Alignment.BottomStart).padding(10.dp))
                            }
                        }
                    }
                }
            }
        }
        PageDots(pages, pager.currentPage, Modifier.padding(vertical = 14.dp))
    }
}

/** The catalogue: framed keys for the region, the genre and a new download, the name search, then the list. */
@Composable
private fun FactoryCatalog(s: RadioState) {
    val context = LocalContext.current
    val cat = rememberCatalogUi()
    if (cat.pick != CatalogPick.NONE) return CatalogGrid(cat, Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 10.dp), 220.dp)
    val saved = s.webStations.map { it.url }.toSet()
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            FramedTextKey(cat.countryLabel()) { cat.open(CatalogPick.COUNTRY) }
            FramedTextKey(cat.regionLabel()) { cat.open(CatalogPick.REGION) }
            FramedTextKey(cat.genreLabel()) { cat.open(CatalogPick.GENRE) }
            FramedTextKey(stringResource(if (cat.loading) R.string.web_catalog_loading_short else R.string.web_catalog_update)) { if (!cat.loading) cat.refresh() }
            Spacer(Modifier.width(12.dp))
            WebField(cat.query, cat.setQuery, null, stringResource(R.string.web_search), KeyboardType.Text, Modifier.weight(1f), ImeAction.Search)
        }
        FactoryListTitle(cat.countLine())
        LazyColumn(Modifier.fillMaxSize()) {
            items(cat.entries, key = { it.station.uuid.ifBlank { it.station.url } }) { e ->
                val st = e.station
                val isSaved = st.url in saved
                FactoryListRow(
                    st.name,
                    leading = { SaveStar(isSaved, Color.White) { FmService.addWebStations(context, listOf(st.toWebStation())) } },
                    value = listOf(cat.regionOf(st), stationAudio(st.codec, st.bitrate)).filter { it.isNotBlank() }.joinToString("  ·  "),
                    selected = s.web?.url == st.url && s.webActive,
                ) { playCatalog(context, st) }
            }
        }
    }
}

/** The star of a catalogue line: touched, the station is saved; filled once it is. */
@Composable
private fun SaveStar(
    saved: Boolean,
    tint: Color,
    onSave: () -> Unit,
) {
    Icon(
        if (saved) Icons.Outlined.Star else Icons.Outlined.StarBorder,
        stringResource(R.string.web_save),
        tint = if (saved) CarColors.Accent else tint,
        modifier = Modifier.size(32.dp).clickable(enabled = !saved, onClick = onSave),
    )
}

// --- Ambient -------------------------------------------------------------------------------------------------------

@Composable
internal fun AmbientWebRadio(
    s: RadioState,
    send: (String, Int?) -> Unit,
    onSettings: () -> Unit,
    onInternet: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val ui = rememberWebUi()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val current = s.web
    AmbientScreen(
        title = stringResource(R.string.web_radio),
        keys =
            listOf(
                AmbientKey(stringResource(R.string.source_fm)) { onInternet(false) },
                AmbientKey(stringResource(if (s.webActive) R.string.pause else R.string.play), enabled = current != null) { webToggle(context, s, send) },
            ),
        options =
            listOfNotNull(
                AmbientOption(stringResource(R.string.web_add)) { ui.adding = true },
                AmbientOption(stringResource(R.string.web_import)) { ui.import() },
                if (s.webCanSave && current != null) AmbientOption(stringResource(R.string.web_save)) { FmService.addWebStations(context, listOf(current)) } else null,
                current?.let { AmbientOption(stringResource(R.string.web_logo)) { ui.logoFor = it } },
                if (s.source == Source.WEB && s.webFromFm != 0) {
                    AmbientOption(stringResource(R.string.web_back_to_fm, Band.format(s.webFromFm))) {
                        send(FmService.ACTION_TUNE, s.webFromFm)
                        send(FmService.ACTION_PLAY_FM, null)
                    }
                } else {
                    null
                },
                AmbientOption(stringResource(R.string.settings), onClick = onSettings),
            ),
    ) {
        val accent = CarColors.Accent
        Column(Modifier.fillMaxSize()) {
            AmbientTabs(
                listOf(stringResource(R.string.web_playing), stringResource(R.string.web_stations), stringResource(R.string.web_catalog)),
                tab,
            ) { tab = it }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .drawBehind {
                        val w = 1.dp.toPx()
                        drawLine(accent, Offset(w / 2, 0f), Offset(w / 2, size.height), w)
                        drawLine(accent, Offset(size.width - w / 2, 0f), Offset(size.width - w / 2, size.height), w)
                        drawLine(accent, Offset(0f, size.height - w / 2), Offset(size.width, size.height - w / 2), w)
                    },
            ) {
                when (tab) {
                    0 -> AmbientWebPlaying(s, send, ui)
                    1 -> AmbientWebStations(s, ui)
                    // Composed only when open: the catalogue is downloaded when it is wanted.
                    else -> AmbientCatalog(s)
                }
            }
        }
    }
    WebDialogs(ui)
}

/** The station panel: ⏮ ⏭ previous / next saved station on the sides, logo, name, song or state, stream flags. */
@Composable
private fun AmbientWebPlaying(
    s: RadioState,
    send: (String, Int?) -> Unit,
    ui: WebUi,
) {
    val context = LocalContext.current
    val active = s.source == Source.WEB
    Column(Modifier.fillMaxSize().padding(14.dp)) {
        AmbientFrame(Modifier.weight(1f).fillMaxWidth(), glow = true) {
            Row(Modifier.fillMaxSize()) {
                AmbientSideKey(Icons.Outlined.SkipPrevious) {
                    if (active) send(FmService.ACTION_PREVIOUS, null) else s.webStations.lastOrNull()?.let { FmService.playWeb(context, it.id) }
                }
                Box(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    val index = s.webStations.indexOfFirst { it.id == s.web?.id }
                    if (index >= 0 && active) Box(Modifier.align(Alignment.TopStart)) { NumberBadge(index + 1, strong = true) }
                    Row(Modifier.align(Alignment.TopEnd), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (active && s.webCodec.isNotBlank()) FlagBox(s.webCodec)
                        if (active && s.webBitrate > 0) FlagBox("${s.webBitrate} kbit/s")
                    }
                    Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                        AmbientLogo(if (active) s.webLogo else null, 132.dp) { s.web?.let { ui.logoFor = it } }
                        Spacer(Modifier.width(28.dp))
                        Column(Modifier.weight(1f, fill = false)) {
                            Text(
                                s.web?.name ?: stringResource(R.string.web_none),
                                color = KitLight,
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Light,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val status = webStatus(s)
                            if (status.isNotBlank()) {
                                Text(status, color = KitDim, fontSize = 19.sp, fontWeight = FontWeight.Light, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            if (s.webActive && !s.webPlaying) {
                                Spacer(Modifier.height(8.dp))
                                CircularProgressIndicator(color = KitLight, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
                AmbientSideKey(Icons.Outlined.SkipNext) {
                    if (active) send(FmService.ACTION_NEXT, null) else s.webStations.firstOrNull()?.let { FmService.playWeb(context, it.id) }
                }
            }
        }
    }
}

/** The station's logo in a square frame of the accent colour, the metal globe when there is none; a touch: change it. */
@Composable
private fun AmbientLogo(
    logo: android.graphics.Bitmap?,
    size: Dp,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(size)
            .border(BorderStroke(1.dp, CarColors.Accent))
            .background(if (logo != null) Color.White else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(if (logo != null) 8.dp else 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (logo != null) {
            val image = remember(logo) { logo.asImageBitmap() }
            Image(image, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        } else {
            MetalIcon(Icons.Outlined.Public, size * 0.6f)
        }
    }
}

/** The saved stations as a numbered list on the red selection line; a long touch offers to remove one. */
@Composable
private fun AmbientWebStations(
    s: RadioState,
    ui: WebUi,
) {
    val context = LocalContext.current
    if (s.webStations.isEmpty()) {
        Text(
            stringResource(R.string.web_no_stations),
            color = KitDim,
            fontSize = 21.sp,
            fontWeight = FontWeight.Light,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(32.dp),
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 1.dp)) {
        itemsIndexed(s.webStations, key = { _, st -> st.id }) { i, st ->
            AmbientListRow(
                st.name,
                value = stationAudio(st.codec, st.bitrate),
                selected = s.source == Source.WEB && s.web?.id == st.id,
                leading = { NumberBadge(i + 1) },
                onLongClick = { ui.removing = st },
            ) { FmService.playWeb(context, st.id) }
        }
    }
}

/** The catalogue: lit keys for the region, the genre, the name search, then the list on the red selection line. */
@Composable
private fun AmbientCatalog(s: RadioState) {
    val context = LocalContext.current
    val cat = rememberCatalogUi()
    if (cat.pick != CatalogPick.NONE) return CatalogGrid(cat, Modifier.fillMaxSize().padding(14.dp), 220.dp)
    val saved = s.webStations.map { it.url }.toSet()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            StyledPill(cat.countryLabel(), selected = cat.country != cat.settings.countryCode) { cat.open(CatalogPick.COUNTRY) }
            StyledPill(cat.regionLabel(), selected = cat.region != null) { cat.open(CatalogPick.REGION) }
            StyledPill(cat.genreLabel(), selected = cat.genre != null) { cat.open(CatalogPick.GENRE) }
            StyledPill(stringResource(if (cat.loading) R.string.web_catalog_loading_short else R.string.web_catalog_update), enabled = !cat.loading) { cat.refresh() }
            WebField(cat.query, cat.setQuery, null, stringResource(R.string.web_search), KeyboardType.Text, Modifier.weight(1f), ImeAction.Search)
        }
        Text(cat.countLine(), color = KitDim, fontSize = 15.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 1.dp)) {
            items(cat.entries, key = { it.station.uuid.ifBlank { it.station.url } }) { e ->
                val st = e.station
                AmbientListRow(
                    st.name,
                    value = listOf(cat.regionOf(st), stationAudio(st.codec, st.bitrate)).filter { it.isNotBlank() }.joinToString("  ·  "),
                    selected = s.web?.url == st.url && s.webActive,
                    leading = { SaveStar(st.url in saved, KitLight) { FmService.addWebStations(context, listOf(st.toWebStation())) } },
                ) { playCatalog(context, st) }
            }
        }
    }
}
