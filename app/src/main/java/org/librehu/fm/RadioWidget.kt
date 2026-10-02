package org.librehu.fm

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

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
        ): RemoteViews =
            RemoteViews(context.packageName, R.layout.widget_radio).apply {
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
                    setInt(R.id.widget_logo, "setColorFilter", 0xFF8AB4F8.toInt())
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
