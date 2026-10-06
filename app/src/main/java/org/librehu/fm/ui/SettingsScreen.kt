package org.librehu.fm.ui

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.librehu.fm.AudioMode
import org.librehu.fm.FmAudio
import org.librehu.fm.FmService
import org.librehu.fm.LogoLibrary
import org.librehu.fm.R
import org.librehu.fm.RadioBrowser
import org.librehu.fm.RadioSettings
import org.librehu.fm.Settings
import org.librehu.fm.StationLogos
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Radio settings: RDS, audio path, station logos (server, offline library). */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val s by RadioSettings.get(context).collectAsStateWithLifecycle()
    val radio by FmService.state.collectAsStateWithLifecycle()
    val update: ((Settings) -> Settings) -> Unit = { RadioSettings.update(context, it) }
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(CarColors.Background)
                .safeDrawingPadding()
                .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(56.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = CarColors.Text,
                    modifier = Modifier.size(32.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.settings), color = CarColors.Text, fontSize = 28.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(12.dp))
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Section(stringResource(R.string.set_rds)) {
                Hint(stringResource(R.string.set_rds_hint))
                SwitchRow(stringResource(R.string.set_rds_enable), s.rds) { on -> update { it.copy(rds = on) } }
                if (s.rds) {
                    SwitchRow(stringResource(R.string.set_radio_text), s.radioText) { on -> update { it.copy(radioText = on) } }
                    SwitchRow(stringResource(R.string.set_pty), s.pty) { on -> update { it.copy(pty = on) } }
                    SwitchRow(stringResource(R.string.set_af), s.af, stringResource(R.string.set_af_hint)) { on ->
                        update { it.copy(af = on) }
                    }
                    SwitchRow(stringResource(R.string.set_preset_names), s.rdsPresetNames) { on ->
                        update { it.copy(rdsPresetNames = on) }
                    }
                }
            }

            Section(stringResource(R.string.set_audio)) {
                Hint(stringResource(R.string.set_audio_hint))
                Choices(
                    listOf(
                        AudioMode.AUTO to stringResource(R.string.set_audio_auto),
                        AudioMode.PATCH to stringResource(R.string.set_audio_patch),
                        AudioMode.RENDER to stringResource(R.string.set_audio_render),
                    ),
                    s.audioMode,
                ) { m -> update { it.copy(audioMode = m) } }
                Hint(
                    stringResource(
                        R.string.set_audio_current,
                        when (radio.audioPath) {
                            FmAudio.Path.PATCH -> stringResource(R.string.set_audio_patch)
                            FmAudio.Path.RENDER -> stringResource(R.string.set_audio_render)
                            FmAudio.Path.NONE -> stringResource(R.string.off)
                        },
                    ),
                )
            }

            LogoSection(s, update)
        }
    }
}

@Composable
private fun LogoSection(
    s: Settings,
    update: ((Settings) -> Settings) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lib by LogoLibrary.state(context).collectAsStateWithLifecycle()
    var servers by remember { mutableStateOf<List<String>?>(null) }
    var loadingServers by remember { mutableStateOf(false) }
    var custom by remember { mutableStateOf(if (s.logoServer.contains("/")) s.logoServer else "") }
    var country by remember { mutableStateOf(s.country) }
    var limit by remember { mutableStateOf(LogoLibrary.LIMITS[1]) }

    Section(stringResource(R.string.set_logos)) {
        Hint(stringResource(R.string.set_logos_hint))
        SwitchRow(stringResource(R.string.set_logos_enable), s.logos) { on -> update { it.copy(logos = on) } }
        if (!s.logos) return@Section
        SwitchRow(stringResource(R.string.set_logos_offline), s.logosOffline, stringResource(R.string.set_logos_offline_hint)) { on ->
            update { it.copy(logosOffline = on) }
        }

        // Server.
        BodyText(stringResource(R.string.set_server))
        val listed = servers.orEmpty()
        val options =
            buildList {
                add("" to stringResource(R.string.set_server_auto))
                listed.forEach { add(it to it) }
                if (s.logoServer.isNotBlank() && !s.logoServer.contains("/") && s.logoServer !in listed) {
                    add(s.logoServer to s.logoServer)
                }
            }
        Choices(options, if (s.logoServer.contains("/")) null else s.logoServer) { host ->
            custom = ""
            update { it.copy(logoServer = host) }
        }
        Actions {
            Pill(stringResource(if (loadingServers) R.string.set_server_loading else R.string.set_server_list)) {
                if (loadingServers) return@Pill
                loadingServers = true
                scope.launch {
                    servers = withContext(Dispatchers.IO) { RadioBrowser.servers(RadioBrowser.Http(context)) }
                    loadingServers = false
                }
            }
        }
        if (servers?.isEmpty() == true) Hint(stringResource(R.string.set_server_list_failed))
        TextRow(
            stringResource(R.string.set_server_custom),
            custom,
            "https://…",
            { custom = it },
            KeyboardCapitalization.None,
        ) { update { it.copy(logoServer = custom.trim()) } }
        Hint(stringResource(R.string.set_server_current, s.serverUrl))

        // Country.
        BodyText(stringResource(R.string.set_country))
        Choices(
            listOf("" to stringResource(R.string.set_country_auto, Locale.getDefault().country.ifBlank { "FR" })) +
                COUNTRIES.map { it to it },
            s.country.uppercase(Locale.ROOT).takeIf { it.isEmpty() || it in COUNTRIES },
        ) { c ->
            country = c
            update { it.copy(country = c) }
        }
        TextRow(
            stringResource(R.string.set_country_other),
            country,
            "FR",
            { country = it.take(2).uppercase(Locale.ROOT) },
            KeyboardCapitalization.Characters,
        ) { update { it.copy(country = country.trim()) } }

        // Offline library.
        BodyText(stringResource(R.string.set_library))
        Hint(
            if (lib.count > 0) {
                stringResource(
                    R.string.set_library_info,
                    lib.count,
                    lib.country,
                    Formatter.formatShortFileSize(context, lib.bytes),
                    DateFormat.getDateInstance(DateFormat.SHORT).format(Date(lib.updatedAt)),
                )
            } else {
                stringResource(R.string.set_library_empty)
            },
        )
        if (lib.running) {
            val total = lib.total
            LinearProgressIndicator(
                progress = { if (total > 0) lib.done / total.toFloat() else 0f },
                color = CarColors.Accent,
                trackColor = CarColors.SurfaceHigh,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
            Hint(
                if (total > 0) {
                    stringResource(R.string.set_library_progress, lib.done, total)
                } else {
                    stringResource(R.string.set_library_listing)
                },
            )
            Actions { Pill(stringResource(R.string.cancel)) { LogoLibrary.cancel() } }
        } else {
            Choices(
                LogoLibrary.LIMITS.map { n ->
                    n to if (n == 0) stringResource(R.string.set_library_all) else stringResource(R.string.set_library_top, n)
                },
                limit,
            ) { limit = it }
            Hint(stringResource(R.string.set_library_hint, s.countryCode))
            if (lib.error.isNotEmpty()) Hint(stringResource(R.string.set_library_failed))
            Actions {
                Pill(stringResource(R.string.set_library_download)) { LogoLibrary.download(context, limit) }
                if (lib.count > 0) Pill(stringResource(R.string.set_library_delete)) { LogoLibrary.clear(context) }
                Pill(stringResource(R.string.set_logos_clear_cache)) { StationLogos.clearCache(context) }
            }
        }
    }
}

private val COUNTRIES = listOf("FR", "BE", "CH", "LU", "CA", "DE", "ES", "IT", "GB", "NL", "PT", "MA")

@Composable
private fun Section(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(CarColors.Surface)
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, color = CarColors.Text, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        content()
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, color = CarColors.TextDim, fontSize = 15.sp)
}

@Composable
private fun BodyText(text: String) {
    Text(text, color = CarColors.Text, fontSize = 18.sp, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    hint: String? = null,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable { onChange(!checked) }
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = CarColors.Text, fontSize = 18.sp)
            if (hint != null) Text(hint, color = CarColors.TextDim, fontSize = 14.sp)
        }
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors =
                SwitchDefaults.colors(
                    checkedThumbColor = CarColors.OnAccent,
                    checkedTrackColor = CarColors.Accent,
                    uncheckedTrackColor = CarColors.SurfaceHigh,
                ),
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun <T> Choices(
    options: List<Pair<T, String>>,
    selected: T?,
    onSelect: (T) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            val on = value == selected
            Text(
                label,
                color = if (on) CarColors.OnAccent else CarColors.Text,
                fontSize = 16.sp,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (on) CarColors.Accent else CarColors.SurfaceHigh)
                        .clickable { onSelect(value) }
                        .padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun Actions(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        content()
    }
}

@Composable
private fun Pill(
    label: String,
    onClick: () -> Unit,
) {
    Text(
        label,
        color = CarColors.Text,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        modifier =
            Modifier
                .clip(RoundedCornerShape(50))
                .background(CarColors.SurfaceHigh)
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

@Composable
private fun TextRow(
    label: String,
    value: String,
    placeholder: String,
    onValue: (String) -> Unit,
    capitalization: KeyboardCapitalization,
    onApply: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value,
            onValueChange = onValue,
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = capitalization, imeAction = ImeAction.Done),
            keyboardActions =
                androidx.compose.foundation.text
                    .KeyboardActions(onDone = { onApply() }),
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
            modifier = Modifier.weight(1f).widthIn(max = 520.dp),
        )
        Spacer(Modifier.width(12.dp))
        Pill(stringResource(R.string.apply), onApply)
    }
}
