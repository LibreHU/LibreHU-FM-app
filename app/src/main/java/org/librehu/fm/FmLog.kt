package org.librehu.fm

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Journal for an FM problem (Settings → Export the log): the app and radio state, `/dev/fm`, the FM / audio lines of
 * the logcat and the kernel's FM / combo chip messages. With root the whole system's logcat and `dmesg` are read;
 * without, Android only gives an app its own lines.
 */
object FmLog {
    /** Tags and words of the FM path: this app, the AOSP FM JNI, MediaTek's FM / WMT driver, audio routing. */
    private val RELEVANT =
        Regex(
            "LibreHU-FM|FMLIB|FmService|FmRadio|FMRadio|AudioFm|FmAudio|fm_|_fm|/dev/fm|mt66|wmt|stp_|" +
                "AudioPolicy|AudioFlinger|audio_hw|AudioALSA|BroadcastRadio|RadioService",
            RegexOption.IGNORE_CASE,
        )
    private const val TAIL_LINES = 400

    fun fileName(): String = "librehu-fm-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date()) + ".log"

    /** The whole journal as text (call off the main thread: logcat and dmesg take a moment). */
    fun collect(context: Context): String =
        buildString {
            val pkg = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
            appendLine("LibreHU FM journal, ${Date()}")
            appendLine("app ${pkg?.versionName} (${pkg?.versionCode}), Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("build ${Build.FINGERPRINT}")
            appendLine()
            appendLine("== Radio ==")
            appendLine("native library: ${FmNative.loadError?.toString() ?: "loaded"}")
            val dev = File("/dev/fm")
            appendLine("/dev/fm: exists=${dev.exists()} read=${dev.canRead()} write=${dev.canWrite()}")
            appendLine("state: ${FmService.state.value}")
            appendLine("settings: ${RadioSettings.get(context).value}")
            appendLine()
            val root = run("su", "-c", "id") != null
            val logcat =
                if (root) {
                    run("su", "-c", "logcat -d -v threadtime -b main -b system -b radio -b events")
                } else {
                    run("logcat", "-d", "-v", "threadtime")
                }.orEmpty()
            val lines = logcat.lines()
            appendLine("== Logcat, FM and audio lines (${if (root) "whole system, root" else "this app only, no root"}) ==")
            lines.filter { RELEVANT.containsMatchIn(it) }.forEach { appendLine(it) }
            appendLine()
            appendLine("== Logcat, last $TAIL_LINES lines ==")
            lines.takeLast(TAIL_LINES).forEach { appendLine(it) }
            appendLine()
            appendLine("== Kernel (dmesg, FM / combo chip) ==")
            if (root) {
                run("su", "-c", "dmesg")
                    .orEmpty()
                    .lines()
                    .filter { RELEVANT.containsMatchIn(it) || it.contains("[FM]", ignoreCase = true) }
                    .takeLast(TAIL_LINES)
                    .forEach { appendLine(it) }
            } else {
                appendLine("(root needed)")
            }
            appendLine()
            appendLine("== Properties ==")
            run("getprop")
                .orEmpty()
                .lines()
                .filter { RELEVANT.containsMatchIn(it) || it.contains("fm", ignoreCase = true) || it.contains("audio", ignoreCase = true) }
                .forEach { appendLine(it) }
        }

    private fun run(vararg command: String): String? =
        try {
            val p = ProcessBuilder(*command).redirectErrorStream(true).start()
            p.outputStream.close()
            val out = p.inputStream.bufferedReader().readText()
            if (!p.waitFor(20, TimeUnit.SECONDS)) {
                p.destroy()
                null
            } else if (p.exitValue() != 0 && command.first() == "su") {
                null
            } else {
                out
            }
        } catch (_: Exception) {
            null
        }
}
