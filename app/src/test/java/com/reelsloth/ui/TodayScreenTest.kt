package com.reelsloth.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.reelsloth.ai.CommentarySource
import com.reelsloth.ai.ModelState
import com.reelsloth.ai.Tone
import com.reelsloth.ai.Verdict
import com.reelsloth.detect.Platform
import com.reelsloth.score.ScoreConfig
import com.reelsloth.score.ScoreEngine
import com.reelsloth.score.ScoreKind
import com.reelsloth.stats.Period
import com.reelsloth.stats.PeriodReport
import com.reelsloth.stats.PeriodStats
import com.reelsloth.ui.theme.ScrollSlothTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The four Today states from the brief: no permission, no data, normal, model downloading. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TodayScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val config = ScoreConfig()
    private val ready = TrackingState(permissionGranted = true, paused = false, anyAppReady = true)

    private fun day(reels: Int, minutes: Double = 0.0, byPlatform: Map<Platform, Int> = emptyMap()) = PeriodStats(
        period = Period.DAY,
        startMs = 0,
        endMs = 86_400_000,
        reels = reels,
        minutes = minutes,
        byPlatform = byPlatform,
        lateNightReels = 0,
        longestSessionReels = reels,
        activeDays = if (reels > 0) 1 else 0,
        baselineDailyMedian = null,
    )

    private fun state(
        tracking: TrackingState = ready,
        today: PeriodStats = day(0),
        verdict: Verdict? = null,
        modelState: ModelState = ModelState.NotDownloaded,
    ): TodayUiState {
        val report = PeriodReport(today, listOf(today))
        return TodayUiState(
            tracking = tracking,
            report = report,
            previous = PeriodReport(day(12), listOf(day(12))),
            scores = ScoreEngine(config).scoreDays(report.days),
            config = config,
            verdict = verdict,
            tone = Tone.GENTLE,
            modelState = modelState,
        )
    }

    private fun show(state: TodayUiState, onOpenSettings: () -> Unit = {}, onToneChange: (Tone) -> Unit = {}) {
        compose.setContent {
            ScrollSlothTheme {
                TodayScreen(state, onToneChange = onToneChange, onOpenAccessibilitySettings = onOpenSettings)
            }
        }
    }

    @Test
    fun noPermissionShowsBannerThatOpensSettings() {
        var opened = 0
        show(state(tracking = ready.copy(permissionGranted = false)), onOpenSettings = { opened++ })

        compose.onNodeWithTag(Tags.PERMISSION_BANNER).assertIsDisplayed()
        compose.onNodeWithText("Turn it on").performClick()
        assertEquals(1, opened)
        compose.onNodeWithText("Once counting is on", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun noDataShowsFriendlyEmptyStateAndNoScores() {
        show(state())

        compose.onNodeWithTag(Tags.PERMISSION_BANNER).assertDoesNotExist()
        compose.onNodeWithTag(Tags.COUNT, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag(Tags.EMPTY_STATE).assertIsDisplayed()
        compose.onNodeWithText("The sloth is awake and counting", substring = true).assertIsDisplayed()
        compose.onNodeWithTag(Tags.score(ScoreKind.LAZINESS)).assertDoesNotExist()
        compose.onNodeWithTag(Tags.PLATFORM_SPLIT).assertDoesNotExist()
        compose.onNodeWithText("The first verdict arrives", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun normalDayShowsCountScoresWhyAndSplit() {
        val today = day(
            reels = 120,
            minutes = 75.0,
            byPlatform = mapOf(Platform.INSTAGRAM to 80, Platform.YOUTUBE to 40),
        )
        var tone: Tone? = null
        show(
            state(
                today = today,
                verdict = Verdict(Period.DAY, "Yesterday you watched 12 reels.", CommentarySource.GEMMA, retries = 0),
                modelState = ModelState.Ready,
            ),
            onToneChange = { tone = it },
        )

        compose.onNodeWithText("120", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("1 h 15 min", substring = true, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag(Tags.EMPTY_STATE).assertDoesNotExist()

        val laziness = ScoreEngine(config).scoreDays(listOf(today)).laziness
        compose.onNodeWithTag(Tags.score(ScoreKind.LAZINESS)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(laziness.band.label, useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(Tags.score(ScoreKind.UNPRODUCTIVITY)).performScrollTo().assertIsDisplayed()

        compose.onNodeWithText("Laziness =", substring = true).assertDoesNotExist()
        compose.onAllNodes(hasText("Why this score")).onFirst().performScrollTo().performClick()
        compose.onNodeWithText("Laziness =", substring = true).performScrollTo().assertIsDisplayed()

        compose.onNodeWithTag(Tags.PLATFORM_SPLIT).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("80 · 67%", useUnmergedTree = true).assertExists()

        compose.onNodeWithTag(Tags.COMMENTARY_BADGE).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Gemma", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(Tags.MODEL_PROGRESS).assertDoesNotExist()

        compose.onNodeWithText("Roast").performScrollTo().performClick()
        assertEquals(Tone.ROAST, tone)
    }

    @Test
    fun modelDownloadingKeepsTemplateBadgeAndShowsProgress() {
        show(
            state(
                today = day(30, minutes = 10.0, byPlatform = mapOf(Platform.FACEBOOK to 30)),
                verdict = Verdict(Period.DAY, "A calm 30 reels yesterday.", CommentarySource.TEMPLATE, retries = 0),
                modelState = ModelState.Downloading(bytesSoFar = 250, totalBytes = 1_000),
            ),
        )

        compose.onNodeWithTag(Tags.MODEL_PROGRESS).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Gemma is downloading (25%)", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Template", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Gemma", useUnmergedTree = true).assertDoesNotExist()
    }
}
