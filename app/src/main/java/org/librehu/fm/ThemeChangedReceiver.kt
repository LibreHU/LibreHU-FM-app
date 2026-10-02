package org.librehu.fm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * LibreHU Launcher sends THEME_CHANGED to this package when its light / dark theme changes: redraw the widget,
 * even when the radio (and its service) is off.
 */
class ThemeChangedReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) = RadioWidget.refresh(context)
}
