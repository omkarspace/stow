package com.cleansaver.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = StowLavender,
    onPrimary = StowPineDark,
    primaryContainer = StowPineElevated,
    onPrimaryContainer = StowInkOnDark,
    secondary = StowLavenderSoft,
    onSecondary = StowPineDark,
    secondaryContainer = StowPineMuted,
    onSecondaryContainer = StowInkOnDark,
    background = StowNightCanvas,
    onBackground = StowInkOnDark,
    surface = StowNightBlock,
    onSurface = StowInkOnDark,
    surfaceVariant = StowPineElevated,
    onSurfaceVariant = StowInkMutedOnDark,
    outline = StowNightBorder,
    outlineVariant = StowPineMuted,
    error = ErrorRedDark,
    errorContainer = ErrorContainerDark,
    onError = StowPineDark,
    onErrorContainer = ErrorRedDark
)

// Signature BRIK-inspired Light Shell + Deep Pine Modular Blocks + Lavender Accent
private val LightColorScheme = lightColorScheme(
    primary = StowPineDark,
    onPrimary = StowInkOnDark,
    primaryContainer = StowLavender,
    onPrimaryContainer = StowPineDark,
    secondary = StowLavender,
    onSecondary = StowPineDark,
    secondaryContainer = StowPineMuted,
    onSecondaryContainer = StowInkOnDark,
    background = StowBoneCanvas,
    onBackground = StowInkOnLight,
    surface = StowPineDark,
    onSurface = StowInkOnDark,
    surfaceVariant = StowPineElevated,
    onSurfaceVariant = StowInkMutedOnDark,
    outline = StowPineMuted,
    outlineVariant = StowPineMuted,
    error = ErrorRedLight,
    errorContainer = ErrorContainerLight,
    onError = StowInkOnDark,
    onErrorContainer = ErrorRedLight
)

@Composable
fun CleanSaverTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
