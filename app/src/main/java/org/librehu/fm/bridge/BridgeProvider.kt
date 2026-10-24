package org.librehu.fm.bridge

import android.content.Context
import android.content.Intent
import org.librehu.fm.AudioMode
import org.librehu.fm.Band
import org.librehu.fm.FmService
import org.librehu.fm.LogoLibrary
import org.librehu.fm.R
import org.librehu.fm.RadioSettings
import org.librehu.fm.StationLogos

/**
 * The radio for LibreHU's phone app (through LibreHU-service's bridge, [BridgeKitProvider]): the radio itself (on /
 * off, frequency, stations, seek, scan) and every setting (RDS, audio path, station logos). Same labels and same
 * settings as the app's screens.
 */
class BridgeProvider : BridgeKitProvider() {
    override fun pages(context: Context): List<KitPage> = listOf(radio(context), settings(context))

    private fun send(
        c: Context,
        action: String,
        extra: Intent.() -> Unit = {},
    ) {
        runCatching { c.startForegroundService(Intent(c, FmService::class.java).setAction(action).apply(extra)) }
    }

    private fun radio(c: Context): KitPage {
        val r = FmService.state.value
        return page(c, "radio", c.getString(R.string.app_name), "radio") {
            switch("on", s(R.string.play), r.poweredOn) { on -> send(c, if (on) FmService.ACTION_PLAY else FmService.ACTION_PAUSE) }
            slider("frequency", "MHz", r.frequency, Band.MIN, Band.MAX, Band.STEP, display = { Band.format(it) }) { f ->
                send(c, FmService.ACTION_TUNE) { putExtra(FmService.EXTRA_FREQUENCY, f) }
            }
            button("previous", s(R.string.previous)) { send(c, FmService.ACTION_PREVIOUS) }
            button("next", s(R.string.next)) { send(c, FmService.ACTION_NEXT) }
            button("seek_down", s(R.string.seek_down)) { send(c, FmService.ACTION_SEEK_DOWN) }
            button("seek_up", s(R.string.seek_up)) { send(c, FmService.ACTION_SEEK_UP) }
            button("scan", s(R.string.scan), confirm = true) { send(c, FmService.ACTION_SCAN) }
        }
    }

    private fun settings(c: Context): KitPage {
        val st = RadioSettings.get(c).value
        val update: ((org.librehu.fm.Settings) -> org.librehu.fm.Settings) -> Unit = { RadioSettings.update(c, it) }
        return page(c, "settings", c.getString(R.string.settings), "settings") {
            section(s(R.string.set_rds))
            switch("rds", s(R.string.set_rds_enable), st.rds, s(R.string.set_rds_hint)) { v -> update { it.copy(rds = v) } }
            if (st.rds) {
                switch("radio_text", s(R.string.set_radio_text), st.radioText) { v -> update { it.copy(radioText = v) } }
                switch("pty", s(R.string.set_pty), st.pty) { v -> update { it.copy(pty = v) } }
                switch("af", s(R.string.set_af), st.af, s(R.string.set_af_hint)) { v -> update { it.copy(af = v) } }
                switch("preset_names", s(R.string.set_preset_names), st.rdsPresetNames) { v -> update { it.copy(rdsPresetNames = v) } }
            }
            section(s(R.string.set_audio))
            pick("audio_mode", s(R.string.set_audio), st.audioMode, s(R.string.set_audio_hint), label = {
                s(
                    when (it) {
                        AudioMode.AUTO -> R.string.set_audio_auto
                        AudioMode.PATCH -> R.string.set_audio_patch
                        AudioMode.RENDER -> R.string.set_audio_render
                    },
                )
            }) { v -> update { it.copy(audioMode = v) } }
            section(s(R.string.set_logos))
            switch("logos", s(R.string.set_logos_enable), st.logos, s(R.string.set_logos_hint)) { v -> update { it.copy(logos = v) } }
            switch("logos_offline", s(R.string.set_logos_offline), st.logosOffline, s(R.string.set_logos_offline_hint)) { v ->
                update { it.copy(logosOffline = v) }
            }
            text("server", s(R.string.set_server), st.logoServer, 200, s(R.string.set_server_auto)) { v -> update { it.copy(logoServer = v.trim()) } }
            text("country", s(R.string.set_country), st.country, 2) { v -> update { it.copy(country = v.trim().uppercase()) } }
            for (n in LogoLibrary.LIMITS) {
                button("library_$n", s(R.string.set_library_download) + " · " + if (n == 0) s(R.string.set_library_all) else c.getString(R.string.set_library_top, n)) {
                    LogoLibrary.download(c, n)
                }
            }
            button("library_delete", s(R.string.set_library_delete), confirm = true) { LogoLibrary.clear(c) }
            button("logos_cache", s(R.string.set_logos_clear_cache)) { StationLogos.clearCache(c) }
        }
    }
}
