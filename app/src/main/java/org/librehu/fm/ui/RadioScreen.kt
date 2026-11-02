package org.librehu.fm.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.librehu.fm.Band
import org.librehu.fm.FmService
import org.librehu.fm.R
import org.librehu.fm.RadioState
import org.librehu.fm.Source
import org.librehu.fm.Station
import org.librehu.fm.StationLogos
import kotlin.math.roundToInt

/**
 * Car-style radio screen: now-playing card with transport controls on the left, presets / stations on the right.
 * "FM | Internet" at the top switches between the FM radio and the internet stations ([WebNowPlaying], [WebPanel]).
 */
@Composable
fun RadioScreen(
    send: (String, Int?) -> Unit,
    onSettings: () -> Unit,
) {
    val s by FmService.state.collectAsStateWithLifecycle()
    // LibreHU Launcher's car styles: the launcher asks the app for them (see RetroLook).
    if (RetroLook.factory) return FactoryRadio(s, send, onSettings)
    if (RetroLook.ambient) return AmbientRadio(s, send, onSettings)
    var internet by rememberSaveable { mutableStateOf(s.source == Source.WEB) }
    Row(
        modifier =
            Modifier
                .fillMaxSize()
                .background(CarColors.Background)
                .safeDrawingPadding()
                .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (internet) {
            WebNowPlaying(s, send, onSettings, { internet = it }, Modifier.weight(1.1f).fillMaxHeight())
            WebPanel(s, Modifier.weight(1f).fillMaxHeight())
        } else {
            NowPlaying(s, send, onSettings, { internet = it }, Modifier.weight(1.1f).fillMaxHeight())
            StationPanel(s, send, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun NowPlaying(
    s: RadioState,
    send: (String, Int?) -> Unit,
    onSettings: () -> Unit,
    onInternet: (Boolean) -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(28.dp))
                .background(CarColors.Surface)
                .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SourceTabs(false, onInternet, Modifier.weight(1f))
                IconButton(onClick = onSettings, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = stringResource(R.string.settings),
                        tint = CarColors.TextDim,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                StationLogo(s)
                Spacer(Modifier.width(20.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(Band.format(s.frequency), color = CarColors.Text, fontSize = 80.sp, fontWeight = FontWeight.Light)
                    Spacer(Modifier.width(8.dp))
                    Text("MHz", color = CarColors.TextDim, fontSize = 24.sp, modifier = Modifier.padding(bottom = 16.dp))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    s.title.ifBlank { " " },
                    color = CarColors.Text,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                RdsBadges(s)
            }
            Text(
                when {
                    !s.available || s.error.isNotEmpty() -> s.error.ifEmpty { stringResource(R.string.error_driver) }
                    s.alarm -> stringResource(R.string.alarm_on_air)
                    s.announcing -> stringResource(R.string.ta_announcing)
                    s.scanning -> stringResource(R.string.scanning)
                    s.standby -> stringResource(R.string.ta_standby)
                    s.nowPlaying.isNotBlank() -> s.nowPlaying
                    !s.poweredOn -> stringResource(R.string.off)
                    else -> " "
                },
                color = if (s.error.isNotEmpty() || !s.available || s.alarm) MaterialTheme.colorScheme.error else CarColors.TextDim,
                fontSize = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // The station is also on the internet and the internet works: offer to switch to its stream.
            val offer = s.fmWeb
            if (s.poweredOn && s.online && offer != null) {
                val context = LocalContext.current
                val audio = listOf(offer.codec, if (offer.bitrate > 0) "${offer.bitrate} kbit/s" else "").filter { it.isNotBlank() }.joinToString(" ")
                Spacer(Modifier.height(8.dp))
                OfferChip(
                    androidx.compose.material.icons.Icons.Default.Public,
                    if (audio.isBlank()) stringResource(R.string.web_offer) else stringResource(R.string.web_offer_audio, audio),
                ) { FmService.playWebStation(context, offer, s.frequency) }
            }
        }

        TunerBar(s.frequency) { send(FmService.ACTION_TUNE, it) }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundButton(Icons.Default.SkipPrevious, R.string.seek_down) { send(FmService.ACTION_SEEK_DOWN, null) }
            RoundButton(Icons.Default.ChevronLeft, R.string.step_down) { send(FmService.ACTION_STEP_DOWN, null) }
            Box(
                modifier =
                    Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(CarColors.Accent)
                        // FM screen: play means FM, even when an internet station played last.
                        .clickable { send(if (s.poweredOn) FmService.ACTION_PAUSE else FmService.ACTION_PLAY_FM, null) },
                contentAlignment = Alignment.Center,
            ) {
                if (s.busy) {
                    CircularProgressIndicator(color = CarColors.OnAccent, modifier = Modifier.size(40.dp))
                } else {
                    Icon(
                        if (s.poweredOn) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(if (s.poweredOn) R.string.pause else R.string.play),
                        tint = CarColors.OnAccent,
                        modifier = Modifier.size(52.dp),
                    )
                }
            }
            RoundButton(Icons.Default.ChevronRight, R.string.step_up) { send(FmService.ACTION_STEP_UP, null) }
            RoundButton(Icons.Default.SkipNext, R.string.seek_up) { send(FmService.ACTION_SEEK_UP, null) }
        }
    }
}

/** Call letters (US), RDS programme type and traffic flags (TP: traffic programme, TA: traffic announcement on air). */
@Composable
private fun RdsBadges(s: RadioState) {
    s.bars?.let {
        Spacer(Modifier.width(10.dp))
        SignalBars(it, CarColors.Text, CarColors.SurfaceHigh)
    }
    if (s.stereo) Badge("ST", false)
    if (s.callSign.isNotBlank() && s.callSign != s.title) Badge(s.callSign, false)
    val pty = stringArrayResource(Band.region.ptyNames)
    if (s.pty in 1 until pty.size && pty[s.pty].isNotBlank()) Badge(pty[s.pty], false)
    if (s.ta) {
        Badge("TA", true)
    } else if (s.tp) {
        Badge("TP", false)
    }
}

/** Signal of the station: four bars of growing height, [bars] of them lit. */
@Composable
fun SignalBars(
    bars: Int,
    on: Color,
    off: Color,
    modifier: Modifier = Modifier,
) {
    Row(modifier.height(18.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        for (i in 1..4) {
            Box(
                Modifier
                    .width(4.dp)
                    .height((4 * i + 2).dp)
                    .background(if (i <= bars) on else off),
            )
        }
    }
}

@Composable
private fun Badge(
    label: String,
    strong: Boolean,
) {
    Spacer(Modifier.width(10.dp))
    Text(
        label,
        color = if (strong) CarColors.OnAccent else CarColors.TextDim,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        modifier =
            Modifier
                .clip(RoundedCornerShape(50))
                .background(if (strong) CarColors.Accent else CarColors.SurfaceHigh)
                .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/**
 * Station logo when found, else a radio icon, on a light tile (most logos are made for light backgrounds). A touch
 * lets choose a picture for this frequency, or go back to the automatic logo.
 */
@Composable
private fun StationLogo(s: RadioState) {
    val context = LocalContext.current
    val logos = remember { StationLogos(context.applicationContext) }
    var menu by remember { mutableStateOf(false) }
    val pick =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) Thread({ logos.setManual(s.frequency, uri) }, "logo").start()
        }
    val logo = s.logo
    Box(
        modifier =
            Modifier
                .size(112.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(if (logo != null) Color.White else CarColors.SurfaceHigh)
                .clickable { menu = true }
                .padding(if (logo != null) 8.dp else 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (logo != null) {
            val image = remember(logo) { logo.asImageBitmap() }
            Image(image, contentDescription = s.title, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Default.Radio, contentDescription = null, tint = CarColors.Accent, modifier = Modifier.fillMaxSize())
        }
    }
    if (menu) {
        val manual = remember(s.frequency, s.logo) { logos.hasManual(s.frequency) }
        CarAlertDialog(
            onDismissRequest = { menu = false },
            title = { Text(stringResource(R.string.logo_title, Band.format(s.frequency))) },
            text = { Text(stringResource(R.string.logo_hint)) },
            confirmButton = {
                TextButton(onClick = {
                    menu = false
                    pick.launch("image/*")
                }) { Text(stringResource(R.string.logo_pick)) }
            },
            dismissButton = {
                if (manual) {
                    TextButton(onClick = {
                        menu = false
                        logos.clearManual(s.frequency)
                    }) { Text(stringResource(R.string.logo_auto)) }
                } else {
                    TextButton(onClick = { menu = false }) { Text(stringResource(android.R.string.cancel)) }
                }
            },
        )
    }
}

/** Band slider (limits of the region's band, on its channels); the frequency is sent when the finger is lifted. */
@Composable
private fun TunerBar(
    frequency: Int,
    onTune: (Int) -> Unit,
) {
    var dragging by remember { mutableFloatStateOf(-1f) }
    val shown = if (dragging >= 0) Band.clamp(dragging.roundToInt()) else frequency
    Column {
        Slider(
            value = shown.toFloat(),
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                if (dragging >= 0) onTune(Band.clamp(dragging.roundToInt()))
                dragging = -1f
            },
            valueRange = Band.MIN.toFloat()..Band.MAX.toFloat(),
            colors =
                SliderDefaults.colors(
                    thumbColor = CarColors.Accent,
                    activeTrackColor = CarColors.Accent,
                    inactiveTrackColor = CarColors.SurfaceHigh,
                ),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(Band.format(Band.MIN), color = CarColors.TextDim, fontSize = 14.sp)
            if (dragging >= 0) Text(Band.format(shown), color = CarColors.Accent, fontSize = 14.sp)
            Text(Band.format(Band.MAX), color = CarColors.TextDim, fontSize = 14.sp)
        }
    }
}

@Composable
private fun StationPanel(
    s: RadioState,
    send: (String, Int?) -> Unit,
    modifier: Modifier,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(28.dp))
                .background(CarColors.Surface)
                .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Tab(stringResource(R.string.favorites), tab == 0) { tab = 0 }
            Spacer(Modifier.width(8.dp))
            Tab(stringResource(R.string.stations), tab == 1) { tab = 1 }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { send(FmService.ACTION_TOGGLE_PRESET, null) }, modifier = Modifier.size(56.dp)) {
                Icon(
                    if (s.isPreset) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = stringResource(R.string.toggle_favorite),
                    tint = if (s.isPreset) CarColors.Accent else CarColors.Text,
                    modifier = Modifier.size(32.dp),
                )
            }
            IconButton(
                onClick = { send(if (s.scanning) FmService.ACTION_STOP_SCAN else FmService.ACTION_SCAN, null) },
                enabled = s.poweredOn,
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    if (s.scanning) Icons.Default.Stop else Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.scan),
                    tint = if (s.poweredOn) CarColors.Text else CarColors.TextDim,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        val list = if (tab == 0) s.presets else s.stations
        if (list.isEmpty()) {
            Text(
                stringResource(if (tab == 0) R.string.no_favorites else R.string.no_stations),
                color = CarColors.TextDim,
                fontSize = 18.sp,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    list,
                    key = { it.frequency },
                ) { st -> StationTile(st, st.frequency == s.frequency) { send(FmService.ACTION_TUNE, st.frequency) } }
            }
        }
    }
}

@Composable
private fun StationTile(
    st: Station,
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
                .padding(16.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        val fg = if (selected) CarColors.OnAccent else CarColors.Text
        Text(Band.format(st.frequency), color = fg, fontSize = 28.sp, fontWeight = FontWeight.Medium)
        if (st.name.isNotBlank()) {
            Text(st.name, color = fg, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Tab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        label,
        color = if (selected) CarColors.OnAccent else CarColors.Text,
        fontSize = 18.sp,
        fontWeight = FontWeight.Medium,
        modifier =
            Modifier
                .clip(RoundedCornerShape(50))
                .background(if (selected) CarColors.Accent else CarColors.SurfaceHigh)
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

@Composable
private fun RoundButton(
    icon: ImageVector,
    label: Int,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(CarColors.SurfaceHigh)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = stringResource(label), tint = CarColors.Text, modifier = Modifier.size(36.dp))
    }
}
