package org.librehu.fm

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import org.librehu.fm.ui.ThemeFollower

/** Home screen / launcher widget: station, RDS text and previous / play / next. */
class RadioWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val s = FmService.state.value.let { if (it.presets.isEmpty() && !it.poweredOn) withStore(context, it) else it }
        manager.updateAppWidget(appWidgetIds, views(context, s))
    }

    companion object {
        /** Redraws the widgets with the last known state (theme change while the radio is off). */
        fun refresh(context: Context) {
            val s = FmService.state.value
            updateAll(context, if (!s.poweredOn) withStore(context, s) else s)
        }

        fun updateAll(
            context: Context,
            s: RadioState,
        ) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, RadioWidget::class.java))
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, views(context, s))
        }

        /** Before the service ever ran, show the last frequency saved. */
        private fun withStore(
            context: Context,
            s: RadioState,
        ): RadioState {
            val store = RadioStore(context)
            return s.copy(frequency = store.frequency, presets = store.presets)
        }

        private fun views(
            context: Context,
            s: RadioState,
        ): RemoteViews {
            val dark = ThemeFollower.current(context).first
            val text = if (dark) 0xFFE8EAED.toInt() else 0xFF202124.toInt()
            val dim = if (dark) 0xFF9AA0A6.toInt() else 0xFF5F6368.toInt()
            val accent = if (dark) 0xFF8AB4F8.toInt() else 0xFF1A73E8.toInt()
            val onAccent = if (dark) 0xFF062E6F.toInt() else 0xFFFFFFFF.toInt()
            return RemoteViews(context.packageName, R.layout.widget_radio).apply {
                setInt(
                    R.id.widget_root,
                    "setBackgroundResource",
                    if (dark) R.drawable.widget_background else R.drawable.widget_background_light,
                )
                setTextColor(R.id.widget_title, text)
                setTextColor(R.id.widget_frequency, text)
                setTextColor(R.id.widget_text, dim)
                val button = if (dark) R.drawable.widget_button else R.drawable.widget_button_light
                setInt(R.id.widget_previous, "setBackgroundResource", button)
                setInt(R.id.widget_next, "setBackgroundResource", button)
                setInt(R.id.widget_previous, "setColorFilter", text)
                setInt(R.id.widget_next, "setColorFilter", text)
                setInt(
                    R.id.widget_toggle,
                    "setBackgroundResource",
                    if (dark) R.drawable.widget_button_accent else R.drawable.widget_button_accent_light,
                )
                setInt(R.id.widget_toggle, "setColorFilter", onAccent)
                setTextViewText(R.id.widget_frequency, Band.format(s.frequency))
                setTextViewText(R.id.widget_title, s.title.ifBlank { context.getString(R.string.app_name) })
                setTextViewText(
                    R.id.widget_text,
                    when {
                        s.radioText.isNotBlank() -> s.radioText
                        s.poweredOn -> "FM ${Band.format(s.frequency)} MHz"
                        else -> context.getString(R.string.off)
                    },
                )
                if (s.logo != null) {
                    setImageViewBitmap(R.id.widget_logo, s.logo)
                    setInt(R.id.widget_logo, "setColorFilter", 0)
                } else {
                    setImageViewResource(R.id.widget_logo, R.drawable.ic_radio)
                    setInt(R.id.widget_logo, "setColorFilter", accent)
                }
                setImageViewResource(R.id.widget_toggle, if (s.poweredOn) R.drawable.ic_pause else R.drawable.ic_play)
                setOnClickPendingIntent(R.id.widget_previous, FmService.servicePending(context, FmService.ACTION_PREVIOUS))
                setOnClickPendingIntent(R.id.widget_toggle, FmService.servicePending(context, FmService.ACTION_TOGGLE))
                setOnClickPendingIntent(R.id.widget_next, FmService.servicePending(context, FmService.ACTION_NEXT))
                setOnClickPendingIntent(
                    R.id.widget_root,
                    PendingIntent.getActivity(
                        context,
                        0,
                        Intent(context, MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
            }
        }
    }
}
