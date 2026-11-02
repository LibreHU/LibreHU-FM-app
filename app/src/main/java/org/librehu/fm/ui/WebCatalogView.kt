package org.librehu.fm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import org.librehu.fm.web.CatalogStation

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
    val cat = rememberCatalogUi()
    if (cat.pick != CatalogPick.NONE) return CatalogGrid(cat, modifier.fillMaxSize(), 150.dp)
    val saved = s.webStations.map { it.url }.toSet()
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(cat.countryLabel(), cat.country != cat.settings.countryCode, Modifier.weight(1f)) { cat.open(CatalogPick.COUNTRY) }
            FilterChip(cat.regionLabel(), cat.region != null, Modifier.weight(1f)) { cat.open(CatalogPick.REGION) }
            FilterChip(cat.genreLabel(), cat.genre != null, Modifier.weight(1f)) { cat.open(CatalogPick.GENRE) }
            IconButton(onClick = cat.refresh, enabled = !cat.loading, modifier = Modifier.size(48.dp)) {
                if (cat.loading) {
                    CircularProgressIndicator(color = CarColors.Accent, modifier = Modifier.size(24.dp))
                } else {
                    Icon(Icons.Default.Refresh, stringResource(R.string.web_catalog_refresh), tint = CarColors.Text)
                }
            }
        }
        Spacer(Modifier.size(8.dp))
        WebField(cat.query, cat.setQuery, null, stringResource(R.string.web_search), KeyboardType.Text, imeAction = ImeAction.Search)
        Text(cat.countLine(), color = CarColors.TextDim, fontSize = 14.sp, modifier = Modifier.padding(vertical = 6.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(cat.entries, key = { it.station.uuid.ifBlank { it.station.url } }) { e ->
                val st = e.station
                CatalogRow(
                    st,
                    e.streams,
                    regionLabel = cat.regionOf(st),
                    playing = s.web?.url == st.url && s.webActive,
                    saved = st.url in saved,
                    onPlay = { FmService.playWebStation(context, st.toWebStation()) },
                    onSave = { FmService.addWebStations(context, listOf(st.toWebStation())) },
                )
            }
        }
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
