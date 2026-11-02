package org.librehu.fm.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.librehu.fm.Band
import org.librehu.fm.FmService
import org.librehu.fm.R
import org.librehu.fm.RadioState
import org.librehu.fm.Source
import org.librehu.fm.web.WebStation

/** "FM | Internet" choice at the top of the now-playing card: what the screen shows (the source changes on play). */
@Composable
internal fun SourceTabs(
    internet: Boolean,
    onInternet: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        PillTab(stringResource(R.string.source_fm), !internet, 16) { onInternet(false) }
        PillTab(stringResource(R.string.source_web), internet, 16) { onInternet(true) }
    }
}

@Composable
private fun PillTab(
    label: String,
    selected: Boolean,
    size: Int,
    onClick: () -> Unit,
) {
    Text(
        label,
        color = if (selected) CarColors.OnAccent else CarColors.Text,
        fontSize = size.sp,
        fontWeight = FontWeight.Medium,
        modifier =
            Modifier
                .clip(RoundedCornerShape(50))
                .background(if (selected) CarColors.Accent else CarColors.SurfaceHigh)
                .clickable(onClick = onClick)
                .padding(horizontal = if (size > 16) 20.dp else 16.dp, vertical = if (size > 16) 12.dp else 8.dp),
    )
}

/** The modern look's internet radio: now-playing card on the left, saved stations or the catalogue on the right. */
@Composable
internal fun WebRadioScreen(
    s: RadioState,
    send: (String, Int?) -> Unit,
    onSettings: () -> Unit,
    onInternet: (Boolean) -> Unit,
    modifier: Modifier,
) {
    val ui = rememberWebUi()
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        WebNowPlaying(s, send, onSettings, onInternet, ui, Modifier.weight(1.1f).fillMaxSize())
        WebPanel(s, ui, Modifier.weight(1f).fillMaxSize())
    }
    WebDialogs(ui)
}

/** Now-playing card of an internet station: logo, name, song title from the stream, audio of the stream, controls. */
@Composable
private fun WebNowPlaying(
    s: RadioState,
    send: (String, Int?) -> Unit,
    onSettings: () -> Unit,
    onInternet: (Boolean) -> Unit,
    ui: WebUi,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val current = s.web
    val active = s.source == Source.WEB
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
                SourceTabs(true, onInternet, Modifier.weight(1f))
                IconButton(onClick = onSettings, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Settings, stringResource(R.string.settings), tint = CarColors.TextDim, modifier = Modifier.size(28.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // A touch: choose a picture as the station's logo (or go back to the automatic one).
                WebLogo(if (active) s.webLogo else null, current?.name.orEmpty()) { if (current != null) ui.logoFor = current }
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        current?.name ?: stringResource(R.string.web_none),
                        color = CarColors.Text,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (s.webCanSave && current != null) {
                        TextButton(onClick = { FmService.addWebStations(context, listOf(current)) }) {
                            Icon(Icons.Default.StarBorder, contentDescription = null, tint = CarColors.Accent)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.web_save), color = CarColors.Accent, fontSize = 16.sp)
                        }
                    }
                    val audio = webAudio(s)
                    if (audio.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            audio,
                            color = CarColors.TextDim,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier =
                                Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(CarColors.SurfaceHigh)
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(webStatus(s).ifBlank { " " }, color = CarColors.TextDim, fontSize = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            // Switched from an FM station (its internet stream): one touch goes back to it.
            if (active && s.webFromFm != 0) {
                Spacer(Modifier.height(8.dp))
                OfferChip(Icons.Default.Radio, stringResource(R.string.web_back_to_fm, Band.format(s.webFromFm))) {
                    send(FmService.ACTION_TUNE, s.webFromFm)
                    send(FmService.ACTION_PLAY_FM, null)
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WebRoundButton(Icons.Default.SkipPrevious, R.string.previous) { if (active) send(FmService.ACTION_PREVIOUS, null) }
            Box(
                modifier =
                    Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(CarColors.Accent)
                        .clickable { webToggle(context, s, send) },
                contentAlignment = Alignment.Center,
            ) {
                if (s.webActive && !s.webPlaying) {
                    CircularProgressIndicator(color = CarColors.OnAccent, modifier = Modifier.size(40.dp))
                } else {
                    Icon(
                        if (s.webActive) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(if (s.webActive) R.string.pause else R.string.play),
                        tint = CarColors.OnAccent,
                        modifier = Modifier.size(52.dp),
                    )
                }
            }
            WebRoundButton(Icons.Default.SkipNext, R.string.next) { if (active) send(FmService.ACTION_NEXT, null) }
        }
    }
}

@Composable
private fun WebLogo(
    logo: android.graphics.Bitmap?,
    name: String,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(112.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(if (logo != null) Color.White else CarColors.SurfaceHigh)
                .clickable(onClick = onClick)
                .padding(if (logo != null) 8.dp else 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (logo != null) {
            val image = remember(logo) { logo.asImageBitmap() }
            Image(image, contentDescription = name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Default.Public, contentDescription = null, tint = CarColors.Accent, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun WebRoundButton(
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

/**
 * Internet stations saved (a touch plays one, a long press offers to remove it; "+" adds one by its address, the file
 * button imports an M3U / PLS file), or the catalogue.
 */
@Composable
private fun WebPanel(
    s: RadioState,
    ui: WebUi,
    modifier: Modifier,
) {
    val context = LocalContext.current
    var catalogTab by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(28.dp))
                .background(CarColors.Surface)
                .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PillTab(stringResource(R.string.web_stations), !catalogTab, 18) { catalogTab = false }
            Spacer(Modifier.width(8.dp))
            PillTab(stringResource(R.string.web_catalog), catalogTab, 18) { catalogTab = true }
            Spacer(Modifier.weight(1f))
            if (!catalogTab) {
                IconButton(onClick = { ui.adding = true }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Default.Add, stringResource(R.string.web_add), tint = CarColors.Text, modifier = Modifier.size(32.dp))
                }
                IconButton(onClick = { ui.import() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Default.FileOpen, stringResource(R.string.web_import), tint = CarColors.Text, modifier = Modifier.size(32.dp))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (catalogTab) {
            WebCatalogView(s)
        } else if (s.webStations.isEmpty()) {
            Text(stringResource(R.string.web_no_stations), color = CarColors.TextDim, fontSize = 18.sp, modifier = Modifier.padding(16.dp))
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(170.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(s.webStations, key = { it.id }) { st ->
                    WebTile(
                        st,
                        selected = s.source == Source.WEB && s.web?.id == st.id,
                        onClick = { FmService.playWeb(context, st.id) },
                        onLongClick = { ui.removing = st },
                    )
                }
            }
        }
    }
}

@Composable
private fun WebTile(
    st: WebStation,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(96.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(if (selected) CarColors.Accent else CarColors.SurfaceHigh)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(16.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        val fg = if (selected) CarColors.OnAccent else CarColors.Text
        Text(st.name, color = fg, fontSize = 20.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val info = stationAudio(st.codec, st.bitrate).let { if (it.isNotBlank() && st.bitrate > 0) "$it kbit/s" else it }
        if (info.isNotBlank()) Text(info, color = fg, fontSize = 14.sp, maxLines = 1)
    }
}

/** A line with an icon and a text in the accent colour, touched to act: offer of a stream, back to FM. */
@Composable
internal fun OfferChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(50))
                .background(CarColors.SurfaceHigh)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = CarColors.Accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = CarColors.Text, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
