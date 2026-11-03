package org.librehu.fm.dab

import android.content.Context
import io.github.jvspier.dabplay.data.ServiceKey
import org.librehu.fm.R
import org.librehu.fm.RadioState

/** A station's key as the screens keep it ("E3.83C7") back to the engine's. */
fun serviceKey(key: String): ServiceKey? =
    runCatching { key.split(".").let { ServiceKey(it[0].toInt(16), it[1].toLong(16)) } }.getOrNull()

/** Ensemble, channel and audio of a DAB station: "Paris étendu · 6A · 96 kbit/s DAB+". */
fun dabInfo(st: DabStation): String =
    listOf(st.ensemble, st.channel, if (st.bitrate > 0) "${st.bitrate} kbit/s ${if (st.dabPlus) "DAB+" else "DAB"}" else "")
        .filter { it.isNotBlank() }
        .joinToString(" · ")

/** What the DAB part does, in words: the line under the station when it sends no text. */
fun dabLine(
    context: Context,
    s: RadioState,
): String =
    when (s.dabStatus) {
        DabStatus.NO_DONGLE -> context.getString(R.string.dab_no_dongle)
        DabStatus.UNSUPPORTED -> context.getString(R.string.dab_unsupported)
        DabStatus.NOT_RESPONDING -> context.getString(R.string.dab_not_responding)
        DabStatus.SCANNING ->
            s.dabScan?.let { context.getString(R.string.dab_scanning, it.channel, it.index + 1, it.total, it.services) }
                ?: context.getString(R.string.dab_scan)
        DabStatus.TUNING -> context.getString(R.string.dab_tuning)
        DabStatus.NO_SIGNAL -> context.getString(R.string.dab_no_signal)
        DabStatus.WEAK -> context.getString(R.string.dab_weak)
        DabStatus.UNPLAYABLE -> context.getString(R.string.dab_unplayable)
        DabStatus.READY, DabStatus.PLAYING ->
            if (s.dabStations.isEmpty()) context.getString(R.string.dab_no_stations) else s.dab?.let(::dabInfo).orEmpty()
    }
