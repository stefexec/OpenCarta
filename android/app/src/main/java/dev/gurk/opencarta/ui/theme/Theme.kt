package dev.gurk.opencarta.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object CartaColors {
    val Background = Color(0xFF000000)
    val Surface = Color(0xFF111314)
    val SurfaceElevated = Color(0xFF181B1D)
    val Border = Color(0xFF23272A)
    val Mint = Color(0xFF1ED7A6)
    val MintDim = Color(0xFF138D6D)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFF8A9094)
    val TextMuted = Color(0xFF555B60)
    val Danger = Color(0xFFFF5252)
    val Warning = Color(0xFFFFB020)
    val Blue = Color(0xFF58A6FF)
}

private val DarkColorScheme = darkColorScheme(
    primary = CartaColors.Mint,
    onPrimary = Color.Black,
    secondary = CartaColors.MintDim,
    onSecondary = Color.White,
    background = CartaColors.Background,
    onBackground = CartaColors.TextPrimary,
    surface = CartaColors.Surface,
    onSurface = CartaColors.TextPrimary,
    surfaceVariant = CartaColors.SurfaceElevated,
    onSurfaceVariant = CartaColors.TextSecondary,
    outline = CartaColors.Border,
    error = CartaColors.Danger
)

@Composable
fun OpenCartaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}

