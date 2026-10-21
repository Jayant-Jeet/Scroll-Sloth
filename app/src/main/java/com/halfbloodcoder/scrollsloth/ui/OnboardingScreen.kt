package com.halfbloodcoder.scrollsloth.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halfbloodcoder.scrollsloth.R
import com.halfbloodcoder.scrollsloth.ui.theme.SlothTheme
import com.halfbloodcoder.scrollsloth.ui.theme.slothText

@Composable
fun OnboardingScreen(
    onOpenAccessibilitySettings: () -> Unit,
    onContinue: () -> Unit,
) {
    val colors = SlothTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BrandHeader("A gentler look at your scrolling")
        Text(
            "Scroll Sloth counts the reels you watch in Instagram, YouTube Shorts, and Facebook Reels.",
            style = slothText(15.sp, lineHeight = 1.45f),
            color = colors.muted,
        )

        InfoCard(
            icon = R.drawable.ic_check_circle,
            tint = colors.mintText,
            title = "What is counted",
            body = "For each reel you watch for at least 2 seconds: when it started, which app it was in, and how long it stayed on screen. Nothing else.",
        )
        InfoCard(
            icon = R.drawable.ic_shield_check,
            tint = colors.lavenderText,
            title = "What is not stored",
            body = "No captions, usernames, video links, screen text, or reel content. Your activity stays on this device.",
        )
        StepsCard(
            title = "Turn on the accessibility service",
            steps = listOf(
                "Tap Open accessibility settings.",
                "Find Scroll Sloth under downloaded or installed apps.",
                "Open it and switch on the service.",
                "Review Android's prompt, then return here.",
            ),
        )
        InfoCard(
            icon = R.drawable.ic_shield,
            tint = colors.muted,
            title = "Android 13 and newer",
            body = "If you installed this app outside the Play Store, Android may block the service at first. Open Scroll Sloth's App info, tap the menu, choose Allow restricted settings, then return to Accessibility settings and enable the service.",
        )

        Column(
            modifier = Modifier.padding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            WideButton("Open accessibility settings", filled = false, onClick = onOpenAccessibilitySettings)
            WideButton("Continue", filled = true, onClick = onContinue)
        }
    }
}

@Composable
private fun InfoCard(@DrawableRes icon: Int, tint: Color, title: String, body: String) {
    val colors = SlothTheme.colors
    SlothCard(spacing = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBox(icon, tint, boxSize = 36.dp, iconSize = 18.dp)
            Text(
                title,
                style = slothText(16.sp, FontWeight.SemiBold),
                color = colors.text,
                modifier = Modifier.semantics { heading() },
            )
        }
        Text(body, style = slothText(14.sp, lineHeight = 1.45f), color = colors.muted)
    }
}

@Composable
private fun StepsCard(title: String, steps: List<String>) {
    val colors = SlothTheme.colors
    SlothCard(spacing = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBox(R.drawable.ic_sparkles, colors.mintText, boxSize = 36.dp, iconSize = 18.dp)
            Text(
                title,
                style = slothText(16.sp, FontWeight.SemiBold),
                color = colors.text,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
        }
        steps.forEachIndexed { index, step ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier.size(24.dp).clip(CircleShape).background(colors.cardRaised),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${index + 1}", style = slothText(12.sp, FontWeight.SemiBold), color = colors.mintText)
                }
                Text(
                    step,
                    style = slothText(14.sp, lineHeight = 1.45f),
                    color = colors.muted,
                    modifier = Modifier.weight(1f).padding(top = 2.dp),
                )
            }
        }
    }
}

/** Full-width 52dp pill: mint when [filled], outlined otherwise. */
@Composable
private fun WideButton(text: String, filled: Boolean, onClick: () -> Unit) {
    val colors = SlothTheme.colors
    PressableBox(onClick = onClick, modifier = Modifier.fillMaxWidth()) { pressed ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .alpha(if (pressed) 0.75f else 1f)
                .clip(CircleShape)
                .then(
                    if (filled) Modifier.background(colors.mint)
                    else Modifier.border(1.dp, colors.divider, CircleShape).background(colors.card),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text,
                style = slothText(15.sp, FontWeight.SemiBold),
                color = if (filled) colors.mintInk else colors.mintText,
            )
        }
    }
}
