package com.halfbloodcoder.scrollsloth.data

/** The Appearance setting. System follows the phone's dark-mode switch. */
enum class ThemeMode { LIGHT, DARK, SYSTEM }

/** Whether to draw the dark palette for [mode], given the phone's own setting. */
fun resolveDarkTheme(mode: ThemeMode, systemDark: Boolean): Boolean = when (mode) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.SYSTEM -> systemDark
}