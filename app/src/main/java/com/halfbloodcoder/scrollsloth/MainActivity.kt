package com.halfbloodcoder.scrollsloth

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.halfbloodcoder.scrollsloth.data.ThemeMode
import com.halfbloodcoder.scrollsloth.data.resolveDarkTheme
import com.halfbloodcoder.scrollsloth.debug.CaptureSharer
import com.halfbloodcoder.scrollsloth.detect.AccessibilityServiceStatus
import com.halfbloodcoder.scrollsloth.detect.ReelAccessibilityService
import com.halfbloodcoder.scrollsloth.ui.OnboardingScreen
import com.halfbloodcoder.scrollsloth.ui.ScrollSlothApp
import com.halfbloodcoder.scrollsloth.ui.SplashScreen
import com.halfbloodcoder.scrollsloth.ui.theme.ScrollSlothTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val container by lazy { (application as ScrollSlothApplication).container }
    private val captureSharer by lazy { CaptureSharer(this, container.captureRepository) }
    private val accessibilityServiceEnabled = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Only the first launch of this activity shows the splash, not a rotation.
        val showSplash = savedInstanceState == null

        setContent {
            val onboardingComplete by remember { container.settingsStore.onboardingComplete }
                .collectAsState(initial = null as Boolean?)
            val themeMode by remember { container.settingsStore.themeMode }.collectAsState(initial = null as ThemeMode?)
            var minimumElapsed by remember { mutableStateOf(!showSplash) }
            LaunchedEffect(Unit) {
                delay(SPLASH_MIN_MS)
                minimumElapsed = true
            }
            val ready = minimumElapsed && onboardingComplete != null && themeMode != null
            val dark = resolveDarkTheme(themeMode ?: ThemeMode.SYSTEM, isSystemInDarkTheme())
            SystemBars(dark = dark, splash = !ready)

            ScrollSlothTheme(darkTheme = dark) {
                Crossfade(targetState = if (!ready) Screen.SPLASH else if (onboardingComplete == true) Screen.APP else Screen.ONBOARDING, label = "root") { screen ->
                    when (screen) {
                        Screen.SPLASH -> SplashScreen()
                        Screen.APP -> ScrollSlothApp(
                            container = container,
                            accessibilityServiceEnabled = accessibilityServiceEnabled.value,
                            onOpenAccessibilitySettings = ::openAccessibilitySettings,
                            onShareCaptures = captureSharer::share,
                        )
                        Screen.ONBOARDING -> {
                            val scope = rememberCoroutineScope()
                            OnboardingScreen(
                                onOpenAccessibilitySettings = ::openAccessibilitySettings,
                                onContinue = {
                                    scope.launch { container.settingsStore.setOnboardingComplete(true) }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    /** Status and navigation bar icons follow the theme; the splash is always light. */
    @Composable
    private fun SystemBars(dark: Boolean, splash: Boolean) {
        val darkIcons = splash || !dark
        DisposableEffect(darkIcons) {
            val style = if (darkIcons) {
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
            } else {
                SystemBarStyle.dark(Color.TRANSPARENT)
            }
            enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            onDispose {}
        }
    }

    override fun onResume() {
        super.onResume()
        accessibilityServiceEnabled.value = AccessibilityServiceStatus.isEnabled(
            Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
            packageName,
            ReelAccessibilityService::class.java.name,
        )
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private enum class Screen { SPLASH, ONBOARDING, APP }

    private companion object {
        const val SPLASH_MIN_MS = 650L
    }
}