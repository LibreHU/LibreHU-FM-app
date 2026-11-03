package org.librehu.fm.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.librehu.fm.FmService
import org.librehu.fm.R
import org.librehu.fm.RadioState
import org.librehu.fm.Source
import org.librehu.fm.dab.DabStation
import org.librehu.fm.dab.DabStatus
import org.librehu.fm.dab.dabInfo
import org.librehu.fm.dab.dabLine

// --- Shared --------------------------------------------------------------------------------------------------------

/** The DAB station is the source and the user wants it to play (it may be tuning or waiting for the dongle). */
internal val RadioState.dabActive: Boolean get() = source == Source.DAB && dabWanted

/** DAB shown among the sources: a dongle is (or was) there, or stations were found. */
internal val RadioState.dabAvailable: Boolean
    get() = dabStatus != DabStatus.NO_DONGLE || dabStations.isNotEmpty() || source == Source.DAB

/** Favourites first (their order), then the other stations: the order of the tiles and of ⏮ ⏭. */
internal val RadioState.dabOrdered: List<DabStation>
    get() {
        val fav = dabFavourites.mapNotNull { k -> dabStations.firstOrNull { it.key == k } }
        return fav + dabStations.filter { it.key !in dabFavourites }
    }

/** Plays the station before / after the current one (favourites first, wraps around), the first when none. */
private fun dabStep(
    context: android.content.Context,
    s: RadioState,
    forward: Boolean,
) {
    val list = s.dabOrdered
    if (list.isEmpty()) return
    val i = list.indexOfFirst { it.key == s.dab?.key }
    val target = if (i < 0) list.first() else list[(i + if (forward) 1 else list.size - 1) % list.size]
    FmService.playDab(context, target.key)
}

/** Play / pause of the DAB screen: the DAB station plays (the other sources stop), or pauses. */
private fun dabToggle(
    context: android.content.Context,
    s: RadioState,
    send: (String, Int?) -> Unit,
) {
    if (s.dabActive) send(FmService.ACTION_PAUSE, null) else FmService.playDab(context, s.dab?.key)
}

/** The line under the station: its text (DLS) while it plays, else what the dongle part is doing. */
@Composable
private fun dabShownLine(s: RadioState): String {
    val context = LocalContext.current
    val playing = s.source == Source.DAB && (s.dabStatus == DabStatus.PLAYING || s.dabStatus == DabStatus.WEAK)
    if (playing && s.dabText.isNotBlank()) return s.dabText
    // The station's ensemble and audio are already under its name.
    return dabLine(context, s).takeUnless { line -> s.dab?.let { dabInfo(it) == line } == true }.orEmpty()
}

/** No dongle, an unknown one, a silent one, or no station yet: what to do, in words. */
@Composable
private fun DabHelp(
    s: RadioState,
    color: Color,
    dim: Color,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(dabLine(context, s), color = color, fontSize = 20.sp)
        if (s.dabStatus == DabStatus.NO_DONGLE || s.dabStatus == DabStatus.UNSUPPORTED) {
            Text(stringResource(R.string.dab_no_dongle_hint), color = dim, fontSize = 16.sp)
        }
        val scan = s.dabScan
        if (scan != null && scan.total > 0) {
            LinearProgressIndicator(
                progress = { (scan.index + 1f) / scan.total },
                color = CarColors.Accent,
                trackColor = dim.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// --- Modern ----------------------------------------------------------------------------------------------------------

/** The modern look's DAB+ radio: now-playing card on the left, favourites or all the stations on the right. */
@Composable
internal fun DabRadioScreen(
    s: RadioState,
    send: (String, Int?) -> Unit,
    onSettings: () -> Unit,
    onSource: (Source) -> Unit,
    modifier: Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        DabNowPlaying(s, send, onSettings, onSource, Modifier.weight(1.1f).fillMaxSize())
        DabPanel(s, send, Modifier.weight(1f).fillMaxSize())
    }
}

@Composable
private fun DabNowPlaying(
    s: RadioState,
    send: (String, Int?) -> Unit,
    onSettings: () -> Unit,
    onSource: (Source) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val active = s.source == Source.DAB
    val st = s.dab
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(28.dp))
                .background(CarColors.Surface)
                .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            SourceHeader(Source.DAB, s, onSource, onSettings)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                DabPicture(if (active) s.dabSlide else null, 88.dp, rounded = true)
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        st?.name ?: stringResource(R.string.dab_radio),
                        color = CarColors.Text,
                        fontSize = 34.sp,
                        lineHeight = 40.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (st != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(dabInfo(st), color = CarColors.TextDim, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            if (active && s.dabWanted && s.dabStatus != DabStatus.NO_DONGLE) {
                                Spacer(Modifier.width(10.dp))
                                SignalBars(s.dabBars, CarColors.Text, CarColors.TextDim.copy(alpha = 0.4f))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(dabShownLine(s).ifBlank { " " }, color = CarColors.TextDim, fontSize = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WebRoundButton(Icons.Default.SkipPrevious, R.string.previous) { dabStep(context, s, false) }
            Box(
                modifier =
                    Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(CarColors.Accent)
                        .clickable { dabToggle(context, s, send) },
                contentAlignment = Alignment.Center,
            ) {
                if (s.dabActive && s.dabStatus == DabStatus.TUNING) {
                    CircularProgressIndicator(color = CarColors.OnAccent, modifier = Modifier.size(40.dp))
                } else {
                    Icon(
                        if (s.dabActive) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(if (s.dabActive) R.string.pause else R.string.play),
                        tint = CarColors.OnAccent,
                        modifier = Modifier.size(52.dp),
                    )
                }
            }
            WebRoundButton(Icons.Default.SkipNext, R.string.next) { dabStep(context, s, true) }
        }
    }
}

/** The picture sent with the sound (slideshow) when there is one, else the tower. */
@Composable
private fun DabPicture(
    picture: android.graphics.Bitmap?,
    size: Dp,
    rounded: Boolean,
) {
    Box(
        Modifier
            .size(size)
            .clip(if (rounded) RoundedCornerShape(20.dp) else RoundedCornerShape(0.dp))
            .background(if (rounded) CarColors.SurfaceHigh else Color.Transparent)
            .then(if (rounded) Modifier else Modifier.border(BorderStroke(1.dp, CarColors.Accent))),
        contentAlignment = Alignment.Center,
    ) {
        if (picture != null) {
            val image = remember(picture) { picture.asImageBitmap() }
            Image(image, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        } else if (rounded) {
            Icon(Icons.Default.CellTower, contentDescription = null, tint = CarColors.Accent, modifier = Modifier.size(size * 0.5f))
        } else {
            MetalIcon(Icons.Default.CellTower, size * 0.6f)
        }
    }
}

/** Favourites / Stations, the star of the station playing and the scan key; help when there is nothing to show. */
@Composable
private fun DabPanel(
    s: RadioState,
    send: (String, Int?) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(if (s.dabFavourites.isEmpty()) 1 else 0) }
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(28.dp))
                .background(CarColors.Surface)
                .padding(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            CarTabs(
                listOf(CarTab(stringResource(R.string.favorites)), CarTab(stringResource(R.string.stations))),
                tab,
                Modifier.weight(1f, fill = false).widthIn(max = 440.dp),
            ) { tab = it }
            Row {
                val favourite = s.dab?.key in s.dabFavourites
                CarIconButton(
                    if (favourite) Icons.Default.Star else Icons.Default.StarBorder,
                    stringResource(R.string.toggle_favorite),
                    tint = if (favourite) CarColors.Accent else CarColors.Text,
                    enabled = s.dab != null,
                ) { send(FmService.ACTION_DAB_FAVOURITE, null) }
                if (s.dabStatus == DabStatus.SCANNING) {
                    Box(Modifier.size(TOUCH), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = CarColors.Accent, modifier = Modifier.size(30.dp))
                    }
                } else {
                    val canScan = s.dabStatus != DabStatus.NO_DONGLE && s.dabStatus != DabStatus.UNSUPPORTED
                    CarIconButton(Icons.Default.Refresh, stringResource(R.string.dab_scan), tint = if (canScan) CarColors.Text else CarColors.TextDim, enabled = canScan) {
                        send(FmService.ACTION_DAB_SCAN, null)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        val list = if (tab == 0) s.dabOrdered.filter { it.key in s.dabFavourites } else s.dabStations
        when {
            s.dabStatus == DabStatus.SCANNING || (s.dabStations.isEmpty() && s.dabStatus != DabStatus.READY) ->
                DabHelp(s, CarColors.Text, CarColors.TextDim, Modifier.padding(16.dp))

            list.isEmpty() ->
                Text(
                    stringResource(if (tab == 0 && s.dabStations.isNotEmpty()) R.string.no_favorites else R.string.dab_no_stations),
                    color = CarColors.TextDim,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(16.dp),
                )

            else ->
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(170.dp),
                    contentPadding = PaddingValues(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(list, key = { it.key }) { st ->
                        DabTile(st, selected = s.source == Source.DAB && s.dab?.key == st.key) { FmService.playDab(context, st.key) }
                    }
                }
        }
    }
}

@Composable
private fun DabTile(
    st: DabStation,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(96.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(if (selected) CarColors.Accent else CarColors.SurfaceHigh)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        val fg = if (selected) CarColors.OnAccent else CarColors.Text
        Text(st.name, color = fg, fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(listOf(st.ensemble, st.channel).filter { it.isNotBlank() }.joinToString(" · "), color = fg.copy(alpha = 0.8f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// --- Factory ---------------------------------------------------------------------------------------------------------

private val FACTORY_DIM = Color(0xFFD2D3D8)
private const val DAB_BY_PAGE = 6

@Composable
internal fun FactoryDabRadio(
    s: RadioState,
    send: (String, Int?) -> Unit,
    onSettings: () -> Unit,
    onSource: (Source) -> Unit,
) {
    val context = LocalContext.current
    val favourite = s.dab?.key in s.dabFavourites
    FactoryScreen(
        title = null,
        back = false,
        keys =
            listOf(
                SoftKey(stringResource(R.string.source_fm), Icons.Outlined.Radio) { onSource(Source.FM) },
                SoftKey(stringResource(R.string.source_web), Icons.Outlined.Public) { onSource(Source.WEB) },
                SoftKey(
                    stringResource(if (s.dabActive) R.string.pause else R.string.play),
                    if (s.dabActive) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                ) { dabToggle(context, s, send) },
                SoftKey(stringResource(R.string.dab_scan_short), Icons.Outlined.Refresh, s.dabStatus == DabStatus.SCANNING) {
                    send(FmService.ACTION_DAB_SCAN, null)
                },
                SoftKey(stringResource(R.string.dab_favourite_short), if (favourite) Icons.Outlined.Star else Icons.Outlined.StarBorder, favourite) {
                    send(FmService.ACTION_DAB_FAVOURITE, null)
                },
                null,
                SoftKey(stringResource(R.string.car_setup), Icons.Outlined.Settings, onClick = onSettings),
            ),
    ) {
        Column(Modifier.fillMaxSize()) {
            FactoryDabStation(s)
            Box(Modifier.weight(1f).fillMaxWidth()) { FactoryDabTiles(s) }
        }
    }
}

/** The station between framed ‹ › keys (previous / next station), its text or the dongle's state under it. */
@Composable
private fun FactoryDabStation(s: RadioState) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.Top) {
        FramedKey(onClick = { dabStep(context, s, false) }) {
            Icon(Icons.Outlined.ChevronLeft, stringResource(R.string.previous), tint = Color.White, modifier = Modifier.size(40.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (s.dabActive && s.dabStatus == DabStatus.TUNING) {
                    CircularProgressIndicator(color = CarColors.Accent, strokeWidth = 3.dp, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.width(14.dp))
                }
                Text(s.dab?.name ?: stringResource(R.string.dab_radio), color = Color.White, fontSize = 40.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val line = listOf(dabShownLine(s), s.dab?.let(::dabInfo).orEmpty()).filter { it.isNotBlank() }.distinct().joinToString("  ·  ")
            Text(line.ifEmpty { " " }, color = FACTORY_DIM, fontSize = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        FramedKey(onClick = { dabStep(context, s, true) }) {
            Icon(Icons.Outlined.ChevronRight, stringResource(R.string.next), tint = Color.White, modifier = Modifier.size(40.dp))
        }
    }
}

/** Pages of six station tiles, numbered, favourites first; the station playing shows its picture. */
@Composable
private fun FactoryDabTiles(s: RadioState) {
    val context = LocalContext.current
    val list = s.dabOrdered
    if (list.isEmpty() || s.dabStatus == DabStatus.SCANNING) {
        DabHelp(s, Color.White, FACTORY_DIM, Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 16.dp))
        return
    }
    val pages = (list.size + DAB_BY_PAGE - 1) / DAB_BY_PAGE
    val pager = rememberPagerState { pages }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(pager, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(DAB_BY_PAGE) { k ->
                    val i = page * DAB_BY_PAGE + k
                    val st = list.getOrNull(i)
                    val current = st != null && s.source == Source.DAB && s.dab?.key == st.key
                    FactoryTile(
                        label = st?.name.orEmpty(),
                        selected = current,
                        aspect = 1.45f,
                        modifier = Modifier.weight(1f),
                        onClick = { if (st != null) FmService.playDab(context, st.key) },
                    ) {
                        if (st != null) {
                            Text((i + 1).toString(), color = Color.White, fontSize = 30.sp, modifier = Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 12.dp))
                            val picture = s.dabSlide
                            if (current && picture != null) {
                                val image = remember(picture) { picture.asImageBitmap() }
                                Image(image, st.name, contentScale = ContentScale.Fit, modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.62f).fillMaxHeight(0.42f))
                            } else {
                                Text(st.channel, color = FACTORY_DIM, fontSize = 16.sp, modifier = Modifier.align(Alignment.BottomStart).padding(10.dp))
                            }
                        }
                    }
                }
            }
        }
        PageDots(pages, pager.currentPage, Modifier.padding(vertical = 14.dp))
    }
}

// --- LHU-Link ----------------------------------------------------------------------------------------------------------

@Composable
internal fun AmbientDabRadio(
    s: RadioState,
    send: (String, Int?) -> Unit,
    onSettings: () -> Unit,
    onSource: (Source) -> Unit,
) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val favourite = s.dab?.key in s.dabFavourites
    AmbientScreen(
        title = stringResource(R.string.dab_radio),
        keys =
            listOf(
                AmbientKey(stringResource(R.string.source_fm)) { onSource(Source.FM) },
                AmbientKey(stringResource(R.string.source_web)) { onSource(Source.WEB) },
                AmbientKey(stringResource(if (s.dabActive) R.string.pause else R.string.play), enabled = s.dab != null || s.dabStations.isNotEmpty()) {
                    dabToggle(context, s, send)
                },
            ),
        options =
            listOfNotNull(
                AmbientOption(stringResource(R.string.dab_scan), enabled = s.dabStatus != DabStatus.NO_DONGLE && s.dabStatus != DabStatus.UNSUPPORTED) {
                    send(FmService.ACTION_DAB_SCAN, null)
                },
                if (s.dab != null) {
                    AmbientOption(stringResource(if (favourite) R.string.dab_favourite_remove else R.string.dab_favourite_add)) {
                        send(FmService.ACTION_DAB_FAVOURITE, null)
                    }
                } else {
                    null
                },
                AmbientOption(stringResource(R.string.settings), onClick = onSettings),
            ),
    ) {
        val accent = CarColors.Accent
        Column(Modifier.fillMaxSize()) {
            AmbientTabs(listOf(stringResource(R.string.web_playing), stringResource(R.string.stations)), tab) { tab = it }
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
                if (tab == 0) AmbientDabPlaying(s) else AmbientDabStations(s)
            }
        }
    }
}

/** The station panel: ⏮ ⏭ on the sides, picture, name, text or state, flags (DAB+, kbit/s, signal). */
@Composable
private fun AmbientDabPlaying(s: RadioState) {
    val context = LocalContext.current
    val active = s.source == Source.DAB
    Column(Modifier.fillMaxSize().padding(14.dp)) {
        AmbientFrame(Modifier.weight(1f).fillMaxWidth(), glow = true) {
            Row(Modifier.fillMaxSize()) {
                AmbientSideKey(Icons.Outlined.SkipPrevious) { dabStep(context, s, false) }
                Box(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    val index = s.dabOrdered.indexOfFirst { it.key == s.dab?.key }
                    if (index >= 0 && active) Box(Modifier.align(Alignment.TopStart)) { NumberBadge(index + 1, strong = true) }
                    Row(Modifier.align(Alignment.TopEnd), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        val st = s.dab
                        if (active && s.dabWanted && s.dabStatus != DabStatus.NO_DONGLE) SignalBars(s.dabBars, KitLight, KitDim)
                        if (st != null) FlagBox(if (st.dabPlus) "DAB+" else "DAB")
                        if (st != null && st.bitrate > 0) FlagBox("${st.bitrate} kbit/s")
                    }
                    Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                        DabPicture(if (active) s.dabSlide else null, 132.dp, rounded = false)
                        Spacer(Modifier.width(28.dp))
                        Column(Modifier.weight(1f, fill = false)) {
                            Text(s.dab?.name ?: stringResource(R.string.dab_radio), color = KitLight, fontSize = 34.sp, fontWeight = FontWeight.Light, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(dabShownLine(s), color = KitDim, fontSize = 19.sp, fontWeight = FontWeight.Light, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (s.dabActive && s.dabStatus == DabStatus.TUNING) {
                                Spacer(Modifier.height(8.dp))
                                CircularProgressIndicator(color = KitLight, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
                AmbientSideKey(Icons.Outlined.SkipNext) { dabStep(context, s, true) }
            }
        }
    }
}

/** The stations as a numbered list on the red selection line, favourites first. */
@Composable
private fun AmbientDabStations(s: RadioState) {
    val context = LocalContext.current
    val list = s.dabOrdered
    if (list.isEmpty() || s.dabStatus == DabStatus.SCANNING) {
        DabHelp(s, KitLight, KitDim, Modifier.fillMaxWidth().padding(32.dp))
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 1.dp)) {
        itemsIndexed(list, key = { _, st -> st.key }) { i, st ->
            AmbientListRow(
                st.name,
                value = listOf(st.ensemble, st.channel).filter { it.isNotBlank() }.joinToString(" · "),
                selected = s.source == Source.DAB && s.dab?.key == st.key,
                leading = { NumberBadge(i + 1) },
            ) { FmService.playDab(context, st.key) }
        }
    }
}
