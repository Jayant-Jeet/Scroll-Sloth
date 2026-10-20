package com.reelsloth.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun OnboardingScreen(
    onOpenAccessibilitySettings: () -> Unit,
    onContinue: () -> Unit,
) {
    Scaffold { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("A gentler look at your scrolling", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Scroll Sloth will count short-form video sessions in Instagram, YouTube Shorts, and Facebook Reels.",
                style = MaterialTheme.typography.bodyLarge,
            )

            InfoCard(
                title = "What is counted",
                body = "Only a session timestamp, the app (platform), and how long that session lasted. Detection is not active yet in this first version.",
            )
            InfoCard(
                title = "What is not stored",
                body = "No captions, usernames, video links, screen text, or reel content. Your activity stays on this device.",
            )
            InfoCard(
                title = "Turn on the accessibility service",
                body = "1. Tap Open accessibility settings.\n2. Find Scroll Sloth under downloaded or installed apps.\n3. Open it and switch on the service.\n4. Review Android's prompt, then return here.",
            )
            InfoCard(
                title = "Android 13 and newer",
                body = "If you installed this app outside the Play Store, Android may block the service at first. Open Scroll Sloth's App info, tap the menu, choose Allow restricted settings, then return to Accessibility settings and enable the service.",
            )

            Spacer(Modifier.height(4.dp))
            OutlinedButton(
                onClick = onOpenAccessibilitySettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Open accessibility settings")
            }
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                Text("Continue")
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
