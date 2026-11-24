package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val StudioDarkColorScheme = darkColorScheme(
    primary = CinemaCrimson,
    onPrimary = Color.White,
    primaryContainer = CinemaCrimsonDark,
    onPrimaryContainer = CinemaCrimsonSoft,
    secondary = AmberGold,
    onSecondary = AmberGoldDark,
    secondaryContainer = AmberGoldContainer,
    onSecondaryContainer = AmberGold,
    tertiary = TealAccent,
    onTertiary = Color(0xFF002436),
    tertiaryContainer = TealContainer,
    onTertiaryContainer = Color(0xFFBAE6FD),
    background = ObsidianBg,
    onBackground = TextPrimaryDark,
    surface = CharcoalSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = CharcoalElevated,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = CharcoalSurface,
    surfaceContainer = CharcoalElevated,
    surfaceContainerHigh = CharcoalHigh,
    outline = OutlineSubtleDark,
    outlineVariant = Color(0xFF232837),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF3E0005)
)

private val StudioLightColorScheme = lightColorScheme(
    primary = LightCrimson,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDADA),
    onPrimaryContainer = Color(0xFF410006),
    secondary = LightGold,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFEBC2),
    onSecondaryContainer = Color(0xFF3E2200),
    tertiary = Color(0xFF0284C7),
    onTertiary = Color.White,
    background = LightBg,
    onBackground = TextPrimaryLight,
    surface = LightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    outline = OutlineSubtleLight
)

val StudioShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun RecapScriptTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) StudioDarkColorScheme else StudioLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = StudioShapes,
        content = content
    )
}
