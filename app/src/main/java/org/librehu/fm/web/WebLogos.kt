package org.librehu.fm.web

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import org.librehu.fm.RadioBrowser
import java.io.File

/**
 * Logos of the internet stations: a picture chosen by hand first (stations added by hand have no logo address), then
 * the one downloaded from the station's logo address (catalogue), kept per station. Blocking: background thread.
 */
object WebLogos {
    private const val PX = 256

    /** Bumped when a logo is chosen or removed by hand: the service shows it again. */
    val changed = MutableStateFlow(0)

    private fun dir(context: Context) = File(context.filesDir, "web_logos").apply { mkdirs() }

    private fun safe(id: String) = id.replace(Regex("[^A-Za-z0-9._-]"), "_")

    private fun auto(
        context: Context,
        id: String,
    ) = File(dir(context), safe(id) + ".png")

    private fun manual(
        context: Context,
        id: String,
    ) = File(dir(context), safe(id) + ".manual.png")

    fun hasManual(
        context: Context,
        id: String,
    ) = manual(context, id).exists()

    /** Logo of [st]: the one chosen by hand, the kept one, or downloaded when [download] (null: none). */
    fun load(
        context: Context,
        st: WebStation,
        download: Boolean,
    ): Bitmap? {
        manual(context, st.id).takeIf { it.exists() }?.let { return BitmapFactory.decodeFile(it.path) }
        val f = auto(context, st.id)
        if (f.exists()) return BitmapFactory.decodeFile(f.path)
        if (!download || st.logoUrl.isBlank()) return null
        val bitmap = runCatching { RadioBrowser.Http(context).logo(st.logoUrl) }.getOrNull() ?: return null
        f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bitmap
    }

    /** A picture of the head unit as the logo of the station [id], scaled down to [PX] at most. */
    fun setManual(
        context: Context,
        id: String,
        uri: Uri,
    ): Boolean {
        val bitmap =
            try {
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            } catch (e: Exception) {
                Log.w("LibreHU-Web", "logo: ${e.message}")
                null
            } ?: return false
        val k = minOf(1f, PX.toFloat() / maxOf(bitmap.width, bitmap.height))
        val scaled =
            if (k < 1f) {
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * k).toInt().coerceAtLeast(1), (bitmap.height * k).toInt().coerceAtLeast(1), true)
            } else {
                bitmap
            }
        manual(context, id).outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
        changed.value++
        return true
    }

    fun clearManual(
        context: Context,
        id: String,
    ) {
        manual(context, id).delete()
        changed.value++
    }

    /** A station removed from the list: its logos go too. */
    fun forget(
        context: Context,
        id: String,
    ) {
        auto(context, id).delete()
        manual(context, id).delete()
    }
}
