package org.librehu.fm.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat

/**
 * Follows the light / dark theme and accent of LibreHU Launcher (content provider `org.librehu.launcher.theme`
 * plus its `THEME_CHANGED` broadcast); without the launcher, follows Android's dark theme.
 */
class ThemeFollower(
    private val context: Context,
) {
    private val main = Handler(Looper.getMainLooper())

    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                c: Context,
                intent: Intent,
            ) = apply(intent.getBooleanExtra("dark", true), intent.getIntExtra("accent", 0))
        }

    private val observer =
        object : ContentObserver(main) {
            override fun onChange(selfChange: Boolean) = refresh()
        }

    fun start() {
        ContextCompat.registerReceiver(context, receiver, IntentFilter(ACTION_THEME_CHANGED), ContextCompat.RECEIVER_EXPORTED)
        try {
            context.contentResolver.registerContentObserver(URI, false, observer)
        } catch (_: SecurityException) {
        }
        refresh()
    }

    fun stop() {
        context.unregisterReceiver(receiver)
        context.contentResolver.unregisterContentObserver(observer)
    }

    /** Reads the launcher theme, or the system one when the launcher is missing. */
    fun refresh() {
        val (dark, accent) = current(context)
        apply(dark, accent)
    }

    private fun apply(
        dark: Boolean,
        accent: Int,
    ) {
        CarColors.palette = CarPalette.of(dark, if (accent != 0) Color(accent) else null)
    }

    companion object {
        /** (dark, accent ARGB or 0): LibreHU Launcher's theme, or Android's night mode without it. */
        fun current(context: Context): Pair<Boolean, Int> {
            val fromLauncher =
                try {
                    context.contentResolver.query(URI, null, null, null, null)?.use { c ->
                        if (c.moveToFirst()) (c.getInt(0) != 0) to c.getInt(1) else null
                    }
                } catch (_: Exception) {
                    null
                }
            if (fromLauncher != null) return fromLauncher
            val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            return (night != Configuration.UI_MODE_NIGHT_NO) to 0
        }

        const val ACTION_THEME_CHANGED = "org.librehu.action.THEME_CHANGED"
        val URI: Uri = Uri.parse("content://org.librehu.launcher.theme/theme")
    }
}
