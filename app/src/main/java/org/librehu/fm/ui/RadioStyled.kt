package org.librehu.fm.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.filled.ArrowLeft
import androidx.compose.material.icons.filled.ArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.librehu.fm.Band
import org.librehu.fm.FmService
import org.librehu.fm.R
import org.librehu.fm.RadioState
import kotlin.math.roundToInt

/*
 * The radio in LibreHU Launcher's two car styles. Factory (Modularer Infotainment-Baukasten): the frequency between
 * framed ‹ › keys, pages of six preset tiles, a station list and a manual tuning scale, soft keys Stations / Manual /
 * play / Setup. Ambient (LHU-Link): tabs Frequencies / Stations / Presets, a tuner panel with its scale and the red
 * needle, the station panel with seek keys, a turning station list with the red selection line, and the bar Back /
 * play / Options.
 */

private const val PRESET_PAGES = 3
private const val PRESETS_BY_PAGE = 6

/** Problem, scan, radiotext or "off": the line under the station name. */
@Composable
private fun statusLine(s: RadioState): String =
    when {
        !s.available || s.error.isNotEmpty() -> s.error.ifEmpty { stringResource(R.string.error_driver) }
        s.scanning -> stringResource(R.string.scanning)
        s.radioText.isNotBlank() -> s.radioText
        !s.poweredOn -> stringResource(R.string.off)
        else -> ""
    }

// --- Factory -------------------------------------------------------------------------------------------------------

private enum class FactoryView { PRESETS, STATIONS, MANUAL }

@Composable
internal fun FactoryRadio(
    s: RadioState,
    send: (String, Int?) -> Unit,
    onSettings: () -> Unit,
) {
    var view by rememberSaveable { mutableStateOf(FactoryView.PRESETS) }
    FactoryScreen(
        title = null,
        back = false,
        keys =
            listOf(
                SoftKey(stringResource(R.string.stations), Icons.AutoMirrored.Outlined.List, view == FactoryView.STATIONS) {
                    view = if (view == FactoryView.STATIONS) FactoryView.PRESETS else FactoryView.STATIONS
                },
                SoftKey(stringResource(R.string.fm_manual), Icons.Outlined.Straighten, view == FactoryView.MANUAL) {
                    view = if (view == FactoryView.MANUAL) FactoryView.PRESETS else FactoryView.MANUAL
                },
                SoftKey(
                    stringResource(if (s.poweredOn) R.string.pause else R.string.play),
                    if (s.poweredOn) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                ) { send(FmService.ACTION_TOGGLE, null) },
                null,
                null,
                null,
                SoftKey(stringResource(R.string.car_setup), Icons.Outlined.Settings, onClick = onSettings),
            ),
    ) {
        when (view) {
            FactoryView.STATIONS -> FactoryStationList(s, send)
            else ->
                Column(Modifier.fillMaxSize()) {
                    FactoryFrequency(s, send, manual = view == FactoryView.MANUAL)
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        if (view == FactoryView.MANUAL) {
                            TuningScale(s.frequency, ambient = false, modifier = Modifier.align(Alignment.Center).fillMaxWidth().height(150.dp).padding(horizontal = 40.dp)) {
                                send(FmService.ACTION_TUNE, it)
                            }
                        } else {
                            FactoryPresets(s, send)
                        }
                    }
                }
        }
    }
}

/** The frequency between framed ‹ › keys (seek; step by 0.1 MHz in manual tuning), the station name and status under it. */
@Composable
private fun FactoryFrequency(
    s: RadioState,
    send: (String, Int?) -> Unit,
    manual: Boolean,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.Top) {
        FramedKey(onClick = { send(if (manual) FmService.ACTION_STEP_DOWN else FmService.ACTION_SEEK_DOWN, null) }) {
            Icon(Icons.Outlined.ChevronLeft, stringResource(if (manual) R.string.step_down else R.string.seek_down), tint = Color.White, modifier = Modifier.size(40.dp))
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Row {
                Text(Band.format(s.frequency), color = Color.White, fontSize = 48.sp)
                Text("MHz", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, modifier = Modifier.padding(start = 6.dp, top = 6.dp))
            }
            val line = listOf(s.title, statusLine(s)).filter { it.isNotBlank() }.joinToString("  ·  ")
            Text(
                line.ifEmpty { " " },
                color = if (s.error.isNotEmpty() || !s.available) Color(0xFFFF6E66) else Color(0xFFD2D3D8),
                fontSize = 22.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        FramedKey(onClick = { send(if (manual) FmService.ACTION_STEP_UP else FmService.ACTION_SEEK_UP, null) }) {
            Icon(Icons.Outlined.ChevronRight, stringResource(if (manual) R.string.step_up else R.string.seek_up), tint = Color.White, modifier = Modifier.size(40.dp))
        }
    }
}

/**
 * Pages of six preset tiles, numbered: a touch tunes, a touch on an empty place stores the station playing, a long
 * touch on the station playing takes it out of the presets.
 */
@Composable
private fun FactoryPresets(
    s: RadioState,
    send: (String, Int?) -> Unit,
) {
    val pager = rememberPagerState { PRESET_PAGES }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(pager, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(PRESETS_BY_PAGE) { k ->
                    val i = page * PRESETS_BY_PAGE + k
                    val st = s.presets.getOrNull(i)
                    val current = st != null && st.frequency == s.frequency
                    FactoryTile(
                        label = st?.let { it.name.ifBlank { Band.format(it.frequency) } } ?: "",
                        selected = current,
                        aspect = 1.45f,
                        modifier = Modifier.weight(1f),
                        onLongClick = { if (current) send(FmService.ACTION_TOGGLE_PRESET, null) },
                        onClick = {
                            when {
                                st != null -> send(FmService.ACTION_TUNE, st.frequency)
                                !s.isPreset -> send(FmService.ACTION_TOGGLE_PRESET, null)
                            }
                        },
                    ) {
                        Text(
                            (i + 1).toString(),
                            color = Color.White,
                            fontSize = 30.sp,
                            modifier = Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 12.dp),
                        )
                        val logo = s.logo
                        if (current && logo != null) {
                            val image = remember(logo) { logo.asImageBitmap() }
                            Image(
                                image,
                                st?.name,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.62f).fillMaxHeight(0.42f),
                            )
                        }
                    }
                }
            }
        }
        PageDots(PRESET_PAGES, pager.currentPage, Modifier.padding(vertical = 14.dp))
    }
}

/** Station list: the scan line first ("Update station list", or the search running), then the stations found. */
@Composable
private fun FactoryStationList(
    s: RadioState,
    send: (String, Int?) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        if (s.scanning) {
            Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = CarColors.Accent, strokeWidth = 3.dp, modifier = Modifier.size(34.dp))
                Spacer(Modifier.width(24.dp))
                Text(stringResource(R.string.fm_searching, Band.format(s.frequency)), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 22.sp)
            }
        } else {
            FactoryListTitle(stringResource(R.string.fm_station_list))
        }
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                FactoryListRow(
                    stringResource(if (s.scanning) R.string.fm_stop_search else R.string.fm_update_list),
                    icon = if (s.scanning) Icons.Outlined.Stop else Icons.Outlined.Refresh,
                ) { if (s.poweredOn || s.scanning) send(if (s.scanning) FmService.ACTION_STOP_SCAN else FmService.ACTION_SCAN, null) }
            }
            if (s.stations.isEmpty()) {
                item { Text(stringResource(R.string.no_stations), color = Color(0xFFD2D3D8), fontSize = 22.sp, modifier = Modifier.padding(16.dp)) }
            }
            itemsIndexed(s.stations, key = { _, st -> st.frequency }) { _, st ->
                FactoryListRow(
                    st.name.ifBlank { Band.format(st.frequency) },
                    icon = Icons.Outlined.Radio,
                    value = Band.format(st.frequency) + " MHz",
                    selected = st.frequency == s.frequency,
                ) { send(FmService.ACTION_TUNE, st.frequency) }
            }
        }
    }
}

// --- Tuning scale --------------------------------------------------------------------------------------------------

/**
 * The band from 87.5 to 108 MHz drawn as a scale: a touch or a drag moves the needle, the frequency is sent when the
 * finger is lifted. Factory: white ticks and an accent needle; ambient: thin lines, the labels between them and the
 * glowing red needle of the original.
 */
@Composable
private fun TuningScale(
    frequency: Int,
    ambient: Boolean,
    modifier: Modifier,
    onTune: (Int) -> Unit,
) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val span = (Band.MAX - Band.MIN).toFloat()
    val shown = drag ?: ((frequency - Band.MIN) / span)
    fun toFrequency(f: Float) = (Band.MIN + f.coerceIn(0f, 1f) * span).roundToInt()
    val accent = CarColors.Accent
    BoxWithConstraints(
        modifier
            .pointerInput(Unit) { detectTapGestures { o -> onTune(toFrequency(o.x / size.width)) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> drag = (o.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = {
                        drag?.let { onTune(toFrequency(it)) }
                        drag = null
                    },
                    onDragCancel = { drag = null },
                ) { change, _ -> drag = (change.position.x / size.width).coerceIn(0f, 1f) }
            },
    ) {
        if (ambient) {
            // Labels at eight even places, between two groups of lines; the needle's red light over them.
            val labels = List(8) { Band.format(Band.MIN + ((Band.MAX - Band.MIN) * it / 7f).roundToInt()) }
            Box(
                Modifier.fillMaxSize().drawBehind {
                    val line = Color(0xFF8E959E)
                    val w = 1.dp.toPx()
                    for (y in listOf(0.18f, 0.32f, 0.46f, 0.74f, 0.88f)) drawLine(line, Offset(0f, size.height * y), Offset(size.width, size.height * y), w)
                    val x = shown * size.width
                    drawRect(
                        Brush.horizontalGradient(
                            0f to Color.Transparent,
                            0.5f to SelectionRed.copy(alpha = 0.75f),
                            1f to Color.Transparent,
                            startX = x - 40.dp.toPx(),
                            endX = x + 40.dp.toPx(),
                        ),
                        topLeft = Offset(x - 40.dp.toPx(), 0f),
                        size = androidx.compose.ui.geometry.Size(80.dp.toPx(), size.height),
                    )
                    drawLine(Color(0xFFFFB27A), Offset(x, 0f), Offset(x, size.height), 2.dp.toPx())
                },
            ) {
                Row(Modifier.fillMaxWidth().align(Alignment.Center).padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    labels.forEach { Text(it, color = KitLight, fontSize = 15.sp, fontWeight = FontWeight.Light) }
                }
            }
        } else {
            Box(
                Modifier.fillMaxSize().drawBehind {
                    val w = 1.5.dp.toPx()
                    // One tick every 0.5 MHz, longer every MHz; the base line under them.
                    val steps = ((Band.MAX - Band.MIN) / 5)
                    for (i in 0..steps) {
                        val f = Band.MIN + i * 5
                        val x = (f - Band.MIN) / span * size.width
                        val long = f % 10 == 0
                        drawLine(Color.White, Offset(x, size.height * (if (long) 0.35f else 0.5f)), Offset(x, size.height * 0.7f), w)
                    }
                    drawLine(Color.White, Offset(0f, size.height * 0.7f), Offset(size.width, size.height * 0.7f), w)
                    val x = shown * size.width
                    drawLine(accent, Offset(x, size.height * 0.12f), Offset(x, size.height * 0.82f), 4.dp.toPx())
                },
            ) {
                Row(Modifier.fillMaxWidth().align(Alignment.BottomCenter), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf(880, 920, 960, 1000, 1040, 1080).forEach { Text(Band.format(it).removeSuffix(".0"), color = Color.White, fontSize = 18.sp) }
                }
            }
        }
        if (drag != null) {
            Text(
                Band.format(toFrequency(shown)),
                color = if (ambient) KitLight else accent,
                fontSize = 18.sp,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

// --- Ambient -------------------------------------------------------------------------------------------------------

@Composable
internal fun AmbientRadio(
    s: RadioState,
    send: (String, Int?) -> Unit,
    onSettings: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    AmbientScreen(
        title = "FM",
        keys =
            listOf(
                AmbientKey(stringResource(if (s.poweredOn) R.string.pause else R.string.play)) { send(FmService.ACTION_TOGGLE, null) },
            ),
        options =
            listOf(
                AmbientOption(stringResource(if (s.scanning) R.string.fm_stop_search else R.string.fm_update_list), enabled = s.poweredOn || s.scanning) {
                    send(if (s.scanning) FmService.ACTION_STOP_SCAN else FmService.ACTION_SCAN, null)
                },
                AmbientOption(stringResource(if (s.isPreset) R.string.fm_remove_preset else R.string.fm_store_preset)) { send(FmService.ACTION_TOGGLE_PRESET, null) },
                AmbientOption(stringResource(R.string.settings), onClick = onSettings),
            ),
    ) {
        val accent = CarColors.Accent
        Column(Modifier.fillMaxSize()) {
            AmbientTabs(
                listOf(stringResource(R.string.fm_frequencies), stringResource(R.string.stations), stringResource(R.string.fm_presets)),
                tab,
            ) { tab = it }
            // The open tab's page: its frame goes on from the tab (sides and bottom).
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
                    0 -> AmbientFrequencies(s, send)
                    1 -> AmbientStations(s, send)
                    else -> AmbientPresets(s, send)
                }
            }
        }
    }
}

/** The tuner panel (◀ ▶ by 0.1 MHz, the scale) over the station panel (⏮ ⏭ seek, preset number, traffic flags). */
@Composable
private fun AmbientFrequencies(
    s: RadioState,
    send: (String, Int?) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        AmbientFrame(Modifier.weight(1f).fillMaxWidth()) {
            Row(Modifier.fillMaxSize()) {
                AmbientSideKey(Icons.Filled.ArrowLeft) { send(FmService.ACTION_STEP_DOWN, null) }
                Box(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text("FM", color = KitLight, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopStart))
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(Band.format(s.frequency), color = KitLight, fontSize = 50.sp, fontWeight = FontWeight.Light)
                        TuningScale(s.frequency, ambient = true, modifier = Modifier.weight(1f).fillMaxWidth()) { send(FmService.ACTION_TUNE, it) }
                    }
                }
                AmbientSideKey(Icons.Filled.ArrowRight) { send(FmService.ACTION_STEP_UP, null) }
            }
        }
        AmbientFrame(Modifier.weight(1f).fillMaxWidth(), glow = true) {
            Row(Modifier.fillMaxSize()) {
                AmbientSideKey(Icons.Outlined.SkipPrevious) { send(FmService.ACTION_SEEK_DOWN, null) }
                Box(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    val preset = s.presets.indexOfFirst { it.frequency == s.frequency }
                    if (preset >= 0) Box(Modifier.align(Alignment.TopStart)) { NumberBadge(preset + 1, strong = true) }
                    Row(Modifier.align(Alignment.TopEnd), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val pty = stringArrayResource(R.array.pty_names)
                        if (s.pty in 1 until pty.size) FlagBox(pty[s.pty])
                        if (s.tp || s.ta) FlagBox(if (s.ta) "TA" else "TP")
                    }
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(s.title.ifBlank { " " }, color = KitLight, fontSize = 24.sp, fontWeight = FontWeight.Light, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(Band.format(s.frequency), color = KitLight, fontSize = 50.sp, fontWeight = FontWeight.Light)
                        val status = statusLine(s)
                        if (status.isNotEmpty()) {
                            Text(
                                status,
                                color = if (s.error.isNotEmpty() || !s.available) Color(0xFFFF6E66) else KitDim,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Light,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
                AmbientSideKey(Icons.Outlined.SkipNext) { send(FmService.ACTION_SEEK_UP, null) }
            }
        }
    }
}

/**
 * The stations found, as a turning list: the station playing stays in the middle on the red line with the speaker,
 * the others fade towards the edges; presets carry their number.
 */
@Composable
private fun AmbientStations(
    s: RadioState,
    send: (String, Int?) -> Unit,
) {
    if (s.stations.isEmpty()) {
        Text(
            stringResource(R.string.no_stations),
            color = KitDim,
            fontSize = 21.sp,
            fontWeight = FontWeight.Light,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(32.dp),
        )
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val rowHeight = 72.dp
        val pad = ((maxHeight - rowHeight) / 2).coerceAtLeast(0.dp)
        val list = rememberLazyListState()
        val current = s.stations.indexOfFirst { it.frequency == s.frequency }
        LaunchedEffect(current) { if (current >= 0) list.animateScrollToItem(current) }
        LazyColumn(
            state = list,
            contentPadding = PaddingValues(vertical = pad),
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(Brush.verticalGradient(0f to Color.Transparent, 0.3f to Color.Black, 0.7f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
                    },
        ) {
            itemsIndexed(s.stations, key = { _, st -> st.frequency }) { _, st ->
                val on = st.frequency == s.frequency
                val preset = s.presets.indexOfFirst { it.frequency == st.frequency }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(rowHeight)
                        .selectionGlow(on)
                        .pointerInput(st.frequency) { detectTapGestures { send(FmService.ACTION_TUNE, st.frequency) } },
                ) {
                    if (preset >= 0) Box(Modifier.align(Alignment.CenterStart).padding(start = 140.dp)) { NumberBadge(preset + 1) }
                    if (on) Icon(Icons.AutoMirrored.Outlined.VolumeUp, null, tint = Color.White, modifier = Modifier.align(Alignment.CenterStart).padding(start = 64.dp).size(30.dp))
                    Text(
                        Band.format(st.frequency),
                        color = KitLight,
                        fontSize = if (on) 44.sp else 32.sp,
                        fontWeight = FontWeight.Light,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    if (st.name.isNotBlank()) {
                        Text(
                            st.name,
                            color = if (on) KitLight else KitDim,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Light,
                            maxLines = 1,
                            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 64.dp),
                        )
                    }
                }
            }
        }
    }
}

/** The presets as a framed numbered list; the station playing on the red line. */
@Composable
private fun AmbientPresets(
    s: RadioState,
    send: (String, Int?) -> Unit,
) {
    if (s.presets.isEmpty()) {
        Text(
            stringResource(R.string.fm_no_presets),
            color = KitDim,
            fontSize = 21.sp,
            fontWeight = FontWeight.Light,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(32.dp),
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 1.dp)) {
        itemsIndexed(s.presets, key = { _, st -> st.frequency }) { i, st ->
            AmbientListRow(
                st.name.ifBlank { Band.format(st.frequency) },
                value = Band.format(st.frequency) + " MHz",
                selected = st.frequency == s.frequency,
                leading = { NumberBadge(i + 1) },
                onLongClick = { if (st.frequency == s.frequency) send(FmService.ACTION_TOGGLE_PRESET, null) },
            ) { send(FmService.ACTION_TUNE, st.frequency) }
        }
    }
}
