package org.librehu.fm.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.librehu.fm.FmService
import org.librehu.fm.R
import org.librehu.fm.RadioState
import org.librehu.fm.Source
import org.librehu.fm.web.Playlists
import org.librehu.fm.web.WebPlayer
import org.librehu.fm.web.WebStation

/** "FM | Internet" choice at the top of the now-playing card: what the screen shows (the source changes on play). */
@Composable
internal fun SourceTabs(
    internet: Boolean,
    onInternet: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        SmallTab(stringResource(R.string.source_fm), !internet) { onInternet(false) }
        SmallTab(stringResource(R.string.source_web), internet) { onInternet(true) }
    }
}

@Composable
private fun SmallTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        label,
        color = if (selected) CarColors.OnAccent else CarColors.Text,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        modifier =
            Modifier
                .clip(RoundedCornerShape(50))
                .background(if (selected) CarColors.Accent else CarColors.SurfaceHigh)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** Now-playing card of an internet station: logo, name, song title from the stream, audio of the stream, controls. */
@Composable
internal fun WebNowPlaying(
    s: RadioState,
    send: (String, Int?) -> Unit,
    onSettings: () -> Unit,
    onInternet: (Boolean) -> Unit,
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
                WebLogo(if (active) s.webLogo else null, current?.name.orEmpty())
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
                    val audio = if (active) audioLabel(s) else ""
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
            Text(
                when {
                    !active || !s.webWanted -> stringResource(if (current == null) R.string.web_empty_hint else R.string.off)
                    s.webRetrying -> stringResource(R.string.web_reconnecting)
                    s.webBuffering && !s.webPlaying -> stringResource(R.string.web_connecting)
                    s.webTitle.isNotBlank() -> s.webTitle
                    else -> " "
                },
                color = CarColors.TextDim,
                fontSize = 20.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WebRoundButton(Icons.Default.SkipPrevious, R.string.previous) { if (active) send(FmService.ACTION_PREVIOUS, null) }
            val playing = active && s.webWanted
            Box(
                modifier =
                    Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(CarColors.Accent)
                        .clickable {
                            when {
                                playing -> send(FmService.ACTION_PAUSE, null)
                                current != null -> FmService.playWeb(context, current.id)
                            }
                        },
                contentAlignment = Alignment.Center,
            ) {
                if (playing && !s.webPlaying) {
                    CircularProgressIndicator(color = CarColors.OnAccent, modifier = Modifier.size(40.dp))
                } else {
                    Icon(
                        if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(if (playing) R.string.pause else R.string.play),
                        tint = CarColors.OnAccent,
                        modifier = Modifier.size(52.dp),
                    )
                }
            }
            WebRoundButton(Icons.Default.SkipNext, R.string.next) { if (active) send(FmService.ACTION_NEXT, null) }
        }
    }
}

/** Codec and bitrate of the stream played ("AAC 128 kbit/s"), from the stream itself. */
private fun audioLabel(s: RadioState): String =
    listOf(s.webCodec, if (s.webBitrate > 0) "${s.webBitrate} kbit/s" else "").filter { it.isNotBlank() }.joinToString(" ")

@Composable
private fun WebLogo(
    logo: android.graphics.Bitmap?,
    name: String,
) {
    Box(
        modifier =
            Modifier
                .size(112.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(if (logo != null) Color.White else CarColors.SurfaceHigh)
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
 * Internet stations saved: a touch plays one, a long press offers to remove it. "+" adds a station by its address
 * (stream, M3U or PLS link); the file button imports every station of an M3U / PLS file (USB key, internal storage).
 */
@Composable
internal fun WebPanel(
    s: RadioState,
    modifier: Modifier,
) {
    val context = LocalContext.current
    var adding by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<WebStation?>(null) }
    val import =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) importPlaylist(context, uri)
        }
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(28.dp))
                .background(CarColors.Surface)
                .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.web_stations),
                color = CarColors.OnAccent,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(CarColors.Accent)
                        .padding(horizontal = 20.dp, vertical = 12.dp),
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { adding = true }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Default.Add, stringResource(R.string.web_add), tint = CarColors.Text, modifier = Modifier.size(32.dp))
            }
            // Any type: file managers and USB keys often give playlists no MIME type, or a wrong one.
            IconButton(onClick = { import.launch(arrayOf("*/*")) }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Default.FileOpen, stringResource(R.string.web_import), tint = CarColors.Text, modifier = Modifier.size(32.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        if (s.webStations.isEmpty()) {
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
                        onLongClick = { removing = st },
                    )
                }
            }
        }
    }
    if (adding) AddWebStationDialog(onDismiss = { adding = false })
    removing?.let { st ->
        CarAlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(st.name) },
            text = { Text(stringResource(R.string.web_remove_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    FmService.removeWebStation(context, st.id)
                    removing = null
                }) { Text(stringResource(R.string.web_remove)) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(android.R.string.cancel)) } },
        )
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
        val info = listOf(st.codec, if (st.bitrate > 0) "${st.bitrate} kbit/s" else "").filter { it.isNotBlank() }.joinToString(" ")
        if (info.isNotBlank()) Text(info, color = fg, fontSize = 14.sp, maxLines = 1)
    }
}

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

@Composable
private fun WebField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    placeholder: String,
    type: KeyboardType,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = type, imeAction = ImeAction.Next),
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedTextColor = CarColors.Text,
                unfocusedTextColor = CarColors.Text,
                focusedBorderColor = CarColors.Accent,
                unfocusedBorderColor = CarColors.SurfaceHigh,
                focusedLabelColor = CarColors.Accent,
                unfocusedLabelColor = CarColors.TextDim,
                cursorColor = CarColors.Accent,
            ),
        modifier = Modifier.fillMaxWidth(),
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
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
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
