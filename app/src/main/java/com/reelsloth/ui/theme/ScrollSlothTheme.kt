package com.reelsloth.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF386A55),
    secondary = Color(0xFF52645A),
    tertiary = Color(0xFF806044),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DD2B5),
    secondary = Color(0xFFB9CCBE),
    tertiary = Color(0xFFE5C09B),
)

@Composable
fun ScrollSlothTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
