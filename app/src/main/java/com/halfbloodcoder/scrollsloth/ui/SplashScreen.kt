package com.halfbloodcoder.scrollsloth.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.halfbloodcoder.scrollsloth.R
import com.halfbloodcoder.scrollsloth.ui.theme.SlothColors

/**
 * Launch screen from the design: cream background, the sloth artwork at full width, and a small
 * loading ring near the bottom. Positions are proportional to the 390x844 reference frame.
 */
@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(SlothColors.Splash)
            .testTag(Tags.SPLASH)
            .semantics { contentDescription = "Scroll Sloth is starting" },
    ) {
        val artworkHeight = maxWidth * 3f / 4f
        // The artwork sits slightly above centre; never let it start above the top edge.
        val artworkTop = (maxHeight * (235f / 844f)).coerceAtMost(maxHeight - artworkHeight).coerceAtLeast(0.dp)
        Image(
            painter = painterResource(R.drawable.sloth_splash),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .offset(y = artworkTop),
        )
        val ringTop = (maxHeight * (738f / 844f))
            .coerceAtLeast(artworkTop + artworkHeight + 12.dp)
            .coerceAtMost(maxHeight - 24.dp)
        CircularProgressIndicator(
            color = SlothColors.SplashRingIndicator,
            trackColor = SlothColors.SplashRingTrack,
            strokeWidth = 3.dp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = ringTop - 12.dp)
                .size(24.dp),
        )
    }
}
