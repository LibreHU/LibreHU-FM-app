package org.librehu.fm.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Dark, high-contrast car UI palette shared by the LibreHU apps. */
object CarColors {
    val Background = Color(0xFF000000)
    val Surface = Color(0xFF1E1F22)
    val SurfaceHigh = Color(0xFF2B2D31)
    val Accent = Color(0xFF8AB4F8)
    val OnAccent = Color(0xFF062E6F)
    val Text = Color(0xFFE8EAED)
    val TextDim = Color(0xFF9AA0A6)
}

@Composable
fun RadioTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme =
            darkColorScheme(
                background = CarColors.Background,
                surface = CarColors.Surface,
                surfaceVariant = CarColors.SurfaceHigh,
                primary = CarColors.Accent,
                onPrimary = CarColors.OnAccent,
                primaryContainer = CarColors.Accent,
                onPrimaryContainer = CarColors.OnAccent,
                onBackground = CarColors.Text,
                onSurface = CarColors.Text,
                onSurfaceVariant = CarColors.TextDim,
            ),
        content = content,
    )
}
