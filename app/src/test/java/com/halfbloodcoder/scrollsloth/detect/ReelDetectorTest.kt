package com.halfbloodcoder.scrollsloth.detect

import com.halfbloodcoder.scrollsloth.debug.CollectionItemInfoSnapshot
import com.halfbloodcoder.scrollsloth.debug.FakeNode
import com.halfbloodcoder.scrollsloth.debug.NodeBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReelDetectorTest {
    private val matcher = NodeMatcher(viewIdContains = "id/pager")
    private val ig = Platform.INSTAGRAM.packageName

    private fun rules(minDwellMs: Long = 2_000) = DetectionRulesLoader.validate(
        DetectionRules(
            schemaVersion = 1,
            rulesVersion = 1,
            minDwellMs = minDwellMs,
            apps = mapOf(ig to AppRule(Platform.INSTAGRAM, listOf(matcher), DetectionStrategy.ITEM_CHANGED, matcher)),
        ),
        RulesSource.OVERRIDE,
    )

    private fun reel(index: Int) = FakeNode(
        bounds = NodeBounds(0, 0, 1000, 2000),
        children = listOf(
            FakeNode(
                viewIdResourceName = "app:id/pager",
                bounds = NodeBounds(0, 0, 1000, 2000),
                children = listOf(
                    FakeNode(
                        bounds = NodeBounds(0, 0, 1000, 2000),
                        collectionItemInfo = CollectionItemInfoSnapshot(index, 1, 0, 1, false),
                    ),
                ),
            ),
        ),
    )

    private var now = 0L
    private var current = rules()
    private val viewed = mutableListOf<ReelViewed>()
    private val detector = ReelDetector({ current }, { now }, viewed::add)

    @Test
    fun windowsAreTurnedIntoCountedViews() {
        detector.onWindow(ig, reel(0))
        now = 3_000
        val event = detector.onWindow(ig, reel(1))

        assertEquals(1, event?.pagerIndex)
        assertEquals(listOf(ReelViewed(Platform.INSTAGRAM, 0, 3_000)), viewed)
        assertTrue(detector.hasCurrentReel)
    }

    @Test
    fun leavingForAnotherAppClosesTheReel() {
        detector.onWindow(ig, reel(0))
        now = 2_500
        detector.onOtherAppInForeground()

        assertEquals(listOf(ReelViewed(Platform.INSTAGRAM, 0, 2_500)), viewed)
        assertFalse(detector.hasCurrentReel)
    }

    @Test
    fun changedRulesCloseTheReelAndApplyNewDwell() {
        detector.onWindow(ig, reel(0))
        now = 2_500
        current = rules(minDwellMs = 5_000)
        detector.onWindow(ig, reel(0))
        now = 6_000
        detector.onWindow(ig, reel(1))

        // The first reel was closed by the rules change; the second view (3.5 s) is under the new 5 s minimum.
        assertEquals(listOf(ReelViewed(Platform.INSTAGRAM, 0, 2_500)), viewed)
    }

    @Test
    fun pausingClosesTheReelAndIgnoresWindowsUntilResumed() {
        detector.onWindow(ig, reel(0))
        now = 3_000
        detector.setPaused(true)

        now = 4_000
        assertNull(detector.onWindow(ig, reel(1)))
        now = 9_000
        assertNull(detector.onWindow(ig, reel(2)))
        assertFalse(detector.hasCurrentReel)
        assertEquals(listOf(ReelViewed(Platform.INSTAGRAM, 0, 3_000)), viewed)

        detector.setPaused(false)
        detector.onWindow(ig, reel(3))
        now = 12_000
        detector.onWindow(ig, reel(4))

        assertEquals(listOf(ReelViewed(Platform.INSTAGRAM, 0, 3_000), ReelViewed(Platform.INSTAGRAM, 9_000, 3_000)), viewed)
    }

    @Test
    fun resetClosesTheReel() {
        detector.onWindow(ig, reel(0))
        now = 4_000
        detector.reset(ResetReason.SCREEN_OFF)

        assertEquals(listOf(ReelViewed(Platform.INSTAGRAM, 0, 4_000)), viewed)
    }
}
