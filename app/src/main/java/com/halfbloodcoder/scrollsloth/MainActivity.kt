package com.halfbloodcoder.scrollsloth

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import com.halfbloodcoder.scrollsloth.debug.CaptureSharer
import com.halfbloodcoder.scrollsloth.detect.AccessibilityServiceStatus
import com.halfbloodcoder.scrollsloth.detect.ReelAccessibilityService
import com.halfbloodcoder.scrollsloth.ui.OnboardingScreen
import com.halfbloodcoder.scrollsloth.ui.ScrollSlothApp
import com.halfbloodcoder.scrollsloth.ui.theme.ScrollSlothTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val container by lazy { (application as ScrollSlothApplication).container }
    private val captureSharer by lazy { CaptureSharer(this, container.captureRepository) }
    private val accessibilityServiceEnabled = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val onboardingComplete by container.settingsStore.onboardingComplete.collectAsState(initial = false)
            val scope = rememberCoroutineScope()

            ScrollSlothTheme {
                if (onboardingComplete) {
                    ScrollSlothApp(
                        container = container,
                        accessibilityServiceEnabled = accessibilityServiceEnabled.value,
                        onOpenAccessibilitySettings = ::openAccessibilitySettings,
                        onShareCaptures = captureSharer::share,
                    )
                } else {
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
}
