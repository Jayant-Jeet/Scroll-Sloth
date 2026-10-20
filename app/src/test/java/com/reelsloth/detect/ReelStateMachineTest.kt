package com.reelsloth.detect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReelStateMachineTest {
    private var now = 0L
    private val machine = ReelStateMachine(minDwellMs = 2_000, maxDwellMs = 120_000, clock = { now })

    private fun ig(timeMs: Long, index: Int? = null, key: Int? = null, onReel: Boolean = true) =
        machine.onEvent(DetectorEvent(timeMs, Platform.INSTAGRAM.packageName, onReel, key, index))

    private fun fb(timeMs: Long, key: Int?, onReel: Boolean = true) =
        machine.onEvent(DetectorEvent(timeMs, Platform.FACEBOOK.packageName, onReel, key, null))

    @Test
    fun exactlyMinDwellCounts() {
        ig(0, index = 0)
        assertEquals(listOf(ReelViewed(Platform.INSTAGRAM, 0, 2_000)), ig(2_000, index = 1))
    }

    @Test
    fun repeatedLeaveEventsEmitOnlyOnce() {
        ig(0, index = 0)
        assertEquals(1, ig(3_000, onReel = false).size)
        assertEquals(emptyList<ReelViewed>(), ig(3_300, onReel = false))
        now = 9_000
        assertEquals(emptyList<ReelViewed>(), machine.reset(ResetReason.SCREEN_OFF))
    }

    @Test
    fun resetUsesClockAndClearsCurrentReel() {
        ig(1_000, index = 4)
        assertTrue(machine.hasCurrentReel)
        now = 6_000
        assertEquals(listOf(ReelViewed(Platform.INSTAGRAM, 1_000, 5_000)), machine.reset(ResetReason.SERVICE_DISCONNECTED))
        assertFalse(machine.hasCurrentReel)
    }

    @Test
    fun unknownIdentityAdoptsFirstKnownIdentityWithoutCounting() {
        fb(0, key = null)
        assertEquals(emptyList<ReelViewed>(), fb(500, key = 7))
        assertEquals(listOf(ReelViewed(Platform.FACEBOOK, 0, 3_000)), fb(3_000, key = 8))
    }

    @Test
    fun reelWithoutAnyIdentityIsNeverCounted() {
        fb(0, key = null)
        assertEquals(emptyList<ReelViewed>(), fb(5_000, key = null, onReel = false))
    }

    @Test
    fun keyTakesPrecedenceOverPagerIndex() {
        ig(0, index = 0, key = 5)
        assertEquals(emptyList<ReelViewed>(), ig(2_500, index = 1, key = 5))
        assertEquals(1, ig(3_000, index = 2, key = 6).size)
    }

    @Test
    fun historyRemembersOnlyTheLastTenReels() {
        var t = 0L
        (1..11).forEach { key ->
            fb(t, key = key)
            t += 2_500
        }
        // Key 1 fell out of the 10-entry history, so a revisit within 60 s counts again.
        val emitted = fb(t, key = 1) + fb(t + 2_500, key = 99)
        assertEquals(listOf(t - 2_500, t), emitted.map { it.timestampMs })
    }

    @Test
    fun revisitInsideHistoryAndWindowIsNotCounted() {
        fb(0, key = 1)
        fb(2_500, key = 2)
        val emitted = fb(5_000, key = 1) + fb(7_500, key = 3)
        assertEquals(listOf(2_500L), emitted.map { it.timestampMs })
    }

    @Test
    fun sameKeyOnTwoPlatformsIsCountedForEach() {
        fb(0, key = 42)
        val emitted = ig(3_000, key = 42) + ig(6_000, key = 43)
        assertEquals(listOf(Platform.FACEBOOK, Platform.INSTAGRAM), emitted.map { it.platform })
    }

    @Test
    fun unsupportedPackageIsIgnored() {
        ig(0, index = 0)
        val emitted = machine.onEvent(DetectorEvent(3_000, "com.example.other", true, 1, null))
        assertEquals(emptyList<ReelViewed>(), emitted)
        assertTrue(machine.hasCurrentReel)
    }
}
