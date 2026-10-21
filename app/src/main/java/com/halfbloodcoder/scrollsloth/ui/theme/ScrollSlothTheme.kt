package com.halfbloodcoder.scrollsloth.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/** The app theme. [darkTheme] comes from the user's Appearance setting, resolved by [com.halfbloodcoder.scrollsloth.data.resolveDarkTheme]. */
@Composable
fun ScrollSlothTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkSlothColors else LightSlothColors
    CompositionLocalProvider(LocalSlothColors provides colors) {
        MaterialTheme(colorScheme = colors.toColorScheme(), typography = SlothTypography, content = content)
    }
}

object SlothTheme {
    val colors: SlothColors
        @Composable
        @ReadOnlyComposable
        get() = LocalSlothColors.current
}

private fun SlothColors.toColorScheme(): ColorScheme {
    val scheme = if (isDark) darkColorScheme() else lightColorScheme()
    return scheme.copy(
        primary = mintText,
        onPrimary = if (isDark) mintInk else card,
        primaryContainer = mint,
        onPrimaryContainer = mintInk,
        secondary = lavenderText,
        onSecondary = if (isDark) lavenderInk else card,
        secondaryContainer = lavender,
        onSecondaryContainer = lavenderInk,
        tertiary = lavenderText,
        tertiaryContainer = cardRaised,
        onTertiaryContainer = text,
        background = background,
        onBackground = text,
        surface = background,
        onSurface = text,
        surfaceVariant = cardRaised,
        onSurfaceVariant = muted,
        surfaceContainerLowest = background,
        surfaceContainerLow = card,
        surfaceContainer = card,
        surfaceContainerHigh = card,
        surfaceContainerHighest = card,
        surfaceBright = cardRaised,
        surfaceDim = background,
        inverseSurface = text,
        inverseOnSurface = background,
        outline = muted,
        outlineVariant = divider,
        error = destructive,
        onError = if (isDark) lavenderInk else card,
        errorContainer = cardRaised,
        onErrorContainer = text,
    )
}