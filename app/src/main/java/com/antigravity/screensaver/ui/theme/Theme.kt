package com.antigravity.screensaver.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = PixelBlue,
    secondary = PixelGreen,
    tertiary = PixelAmber,
    background = OledBlack,
    surface = SurfaceDark,
    onPrimary = OledBlack,
    onSecondary = OledBlack,
    onTertiary = OledBlack,
    onBackground = TextPrimaryWhite,
    onSurface = TextPrimaryWhite
)

@Composable
fun ScreenSaverTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
