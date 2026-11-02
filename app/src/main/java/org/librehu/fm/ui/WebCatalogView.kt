package org.librehu.fm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.librehu.fm.FmService
import org.librehu.fm.R
import org.librehu.fm.RadioSettings
import org.librehu.fm.RadioState
import org.librehu.fm.web.CatalogStation
import org.librehu.fm.web.CatalogStore
import org.librehu.fm.web.WebCatalog
import java.util.Locale

/**
 * Radio Browser catalogue of the country of the settings: region, genre (tag) and name filters, one line per station
 * with the stream chosen for the quality setting (codec and bitrate shown). A touch plays it, the star saves it.
 * Downloaded once, kept a week (refresh button); shown from the kept copy without network.
 */
@Composable
internal fun WebCatalogView(
    s: RadioState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val settings by RadioSettings.get(context).collectAsStateWithLifecycle()
    val country = settings.countryCode
    val countryName = remember(country) { Locale("", country).getDisplayCountry(Locale.ENGLISH) }
    val store = remember { CatalogStore(context.applicationContext) }
    var stations by remember(country) { mutableStateOf<List<CatalogStation>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var region by rememberSaveable(country) { mutableStateOf<String?>(null) }
    var genre by rememberSaveable(country) { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var picker by remember { mutableStateOf(0) }

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
    val entries =
        remember(list, region, genre, query, settings.webQuality) {
            WebCatalog.entries(list, settings.webQuality, region, genre, query, countryName)
        }
    val saved = s.webStations.map { it.url }.toSet()

    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                when (region) {
                    null -> stringResource(R.string.web_region_all)
                    WebCatalog.NO_REGION -> stringResource(R.string.web_region_none)
                    else -> regions.firstOrNull { it.key == region }?.label ?: region.orEmpty()
                },
                region != null,
                Modifier.weight(1f),
            ) { picker = 1 }
            FilterChip(genre ?: stringResource(R.string.web_genre_all), genre != null, Modifier.weight(1f)) { picker = 2 }
            IconButton(onClick = { refresh++ }, enabled = !loading, modifier = Modifier.size(48.dp)) {
                if (loading) {
                    CircularProgressIndicator(color = CarColors.Accent, modifier = Modifier.size(24.dp))
                } else {
                    Icon(Icons.Default.Refresh, stringResource(R.string.web_catalog_refresh), tint = CarColors.Text)
                }
            }
        }
        Spacer(Modifier.size(8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.web_search)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedTextColor = CarColors.Text,
                    unfocusedTextColor = CarColors.Text,
                    focusedBorderColor = CarColors.Accent,
                    unfocusedBorderColor = CarColors.SurfaceHigh,
                    cursorColor = CarColors.Accent,
                ),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            when {
                failed -> stringResource(R.string.web_catalog_failed)
                stations == null -> stringResource(R.string.web_catalog_loading)
                else -> stringResource(R.string.web_catalog_count, entries.size, country)
            },
            color = CarColors.TextDim,
            fontSize = 14.sp,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(entries, key = { it.station.uuid.ifBlank { it.station.url } }) { e ->
                val st = e.station
                CatalogRow(
                    st,
                    e.streams,
                    regionLabel = if (WebCatalog.regionKey(st.state, countryName).isEmpty()) "" else st.state,
                    playing = s.web?.url == st.url && s.webWanted,
                    saved = st.url in saved,
                    onPlay = { FmService.playWebStation(context, st.toWebStation()) },
                    onSave = { FmService.addWebStations(context, listOf(st.toWebStation())) },
                )
            }
        }
    }

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

@Composable
private fun FilterChip(
    label: String,
    active: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(50))
                .background(if (active) CarColors.Accent else CarColors.SurfaceHigh)
                .clickable(onClick = onClick)
                .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (active) CarColors.OnAccent else CarColors.Text
        Text(label, color = fg, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = fg)
    }
}

@Composable
private fun CatalogRow(
    st: CatalogStation,
    streams: Int,
    regionLabel: String,
    playing: Boolean,
    saved: Boolean,
    onPlay: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (playing) CarColors.Accent else CarColors.SurfaceHigh)
                .clickable(onClick = onPlay)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (playing) CarColors.OnAccent else CarColors.Text
        Column(Modifier.weight(1f)) {
            Text(st.name, color = fg, fontSize = 18.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = (listOf(regionLabel) + st.tags.take(2)).filter { it.isNotBlank() }.joinToString(" · ")
            if (sub.isNotBlank()) Text(sub, color = fg.copy(alpha = 0.75f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        val codec = st.codec.takeUnless { it.equals("UNKNOWN", true) }.orEmpty()
        val audio = listOf(codec, if (st.bitrate > 0) "${st.bitrate}" else "").filter { it.isNotBlank() }.joinToString(" ")
        Column(horizontalAlignment = Alignment.End) {
            if (audio.isNotBlank()) Text(audio, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            if (streams > 1) Text(stringResource(R.string.web_streams, streams), color = fg.copy(alpha = 0.75f), fontSize = 12.sp)
        }
        IconButton(onClick = onSave, enabled = !saved, modifier = Modifier.size(48.dp)) {
            Icon(
                if (saved) Icons.Default.Star else Icons.Default.StarBorder,
                stringResource(R.string.web_save),
                tint = fg,
            )
        }
    }
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
                        color = CarColors.Text,
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
