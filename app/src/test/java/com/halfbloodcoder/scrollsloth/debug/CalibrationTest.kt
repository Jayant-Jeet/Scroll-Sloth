package com.halfbloodcoder.scrollsloth.debug

import com.halfbloodcoder.scrollsloth.detect.AppStatus
import com.halfbloodcoder.scrollsloth.detect.Platform
import com.halfbloodcoder.scrollsloth.detect.ReelViewed
import java.io.File
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CalibrationTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun result(auto: Int, manual: Int, version: String? = "412.0.0.35.87") = CalibrationResult(
        platform = Platform.INSTAGRAM,
        startedAtMs = 1_760_000_000_000,
        durationSec = 300,
        autoCount = auto,
        manualCount = manual,
        rulesVersion = 2026102002,
        appVersionName = version,
    )

    // --- Math ---

    @Test
    fun exactMatchHasNoError() {
        val r = result(auto = 20, manual = 20)
        assertEquals(0, r.difference)
        assertEquals(0, r.absoluteError)
        assertEquals(0.0, r.percentError!!, 0.0)
        assertEquals("0.0%", r.formatPercentError())
        assertEquals("Spot on.", r.verdict())
    }

    @Test
    fun overCountIsPositiveDifference() {
        val r = result(auto = 22, manual = 20)
        assertEquals(2, r.difference)
        assertEquals(2, r.absoluteError)
        assertEquals(10.0, r.percentError!!, 1e-9)
        assertEquals("The detector counted 2 more than you did.", r.verdict())
    }

    @Test
    fun underCountIsNegativeDifferenceWithPositiveError() {
        val r = result(auto = 17, manual = 20)
        assertEquals(-3, r.difference)
        assertEquals(3, r.absoluteError)
        assertEquals(15.0, r.percentError!!, 1e-9)
        assertEquals("The detector missed 3 that you counted.", r.verdict())
    }

    @Test
    fun percentIsRoundedToOneDecimal() {
        assertEquals("33.3%", result(auto = 2, manual = 3).formatPercentError())
        assertEquals("66.7%", result(auto = 1, manual = 3).formatPercentError())
        assertEquals("200.0%", result(auto = 3, manual = 1).formatPercentError())
    }

    @Test
    fun zeroByHand() {
        assertEquals(0.0, result(auto = 0, manual = 0).percentError!!, 0.0)
        assertNull(result(auto = 4, manual = 0).percentError)
        assertEquals("n/a", result(auto = 4, manual = 0).formatPercentError())
        assertEquals(4, result(auto = 4, manual = 0).absoluteError)
    }

    // --- Gate ---

    @Test
    fun gateExplainsWhyARunCannotStart() {
        assertNull(CalibrationGate.reasonNotReady(AppStatus.WORKING, trackingPaused = false))
        assertTrue(CalibrationGate.reasonNotReady(AppStatus.WORKING, trackingPaused = true)!!.contains("paused"))
        assertTrue(CalibrationGate.reasonNotReady(AppStatus.NEEDS_CALIBRATION, false)!!.contains("no working rules"))
        assertTrue(CalibrationGate.reasonNotReady(AppStatus.PERMISSION_MISSING, false)!!.contains("accessibility"))
    }

    // --- Session ---

    private var now = 1_000_000L
    private val pendingAtFlush = mutableListOf<ReelViewed>()
    private var flushes = 0
    private lateinit var session: CalibrationSession

    private fun newSession() = CalibrationSession(
        clock = { now },
        flushDetector = {
            flushes++
            pendingAtFlush.forEach(session::onReelViewed)
            pendingAtFlush.clear()
        },
        rulesVersion = { 7 },
        appVersionName = { "v-${it.name.lowercase()}" },
    ).also { session = it }

    private fun reel(platform: Platform) = ReelViewed(platform, now, 3_000)

    @Test
    fun countsOnlyTheChosenPlatformWhileRunning() = runTest {
        newSession()
        session.onReelViewed(reel(Platform.INSTAGRAM)) // Before start: ignored.
        session.plusOne() // Before start: ignored.

        assertTrue(session.start(Platform.INSTAGRAM))
        session.onReelViewed(reel(Platform.INSTAGRAM))
        session.onReelViewed(reel(Platform.YOUTUBE))
        session.onReelViewed(reel(Platform.INSTAGRAM))
        repeat(3) { session.plusOne() }
        now += 125_900

        val r = session.stop()!!

        assertEquals(Platform.INSTAGRAM, r.platform)
        assertEquals(2, r.autoCount)
        assertEquals(3, r.manualCount)
        assertEquals(125, r.durationSec)
        assertEquals(7, r.rulesVersion)
        assertEquals("v-instagram", r.appVersionName)
        assertEquals(CalibrationState.Idle(r), session.state.value)

        session.onReelViewed(reel(Platform.INSTAGRAM)) // After stop: ignored.
        assertEquals(CalibrationState.Idle(r), session.state.value)
    }

    @Test
    fun reelOpenBeforeStartIsNotCountedButReelOpenAtStopIs() = runTest {
        newSession()
        pendingAtFlush += reel(Platform.YOUTUBE) // Still open when Start is tapped.
        session.start(Platform.YOUTUBE)
        assertEquals(0, (session.state.value as CalibrationState.Running).autoCount)

        pendingAtFlush += reel(Platform.YOUTUBE) // Still open when Stop is tapped.
        val r = session.stop()!!

        assertEquals(1, r.autoCount)
        assertEquals(2, flushes)
    }

    @Test
    fun undoNeverGoesBelowZero() = runTest {
        newSession()
        session.start(Platform.FACEBOOK)
        session.plusOne()
        session.minusOne()
        session.minusOne()

        assertEquals(0, session.stop()!!.manualCount)
    }

    @Test
    fun startTwiceAndStopWhenIdleAreRefused() = runTest {
        newSession()
        assertNull(session.stop())
        assertTrue(session.start(Platform.INSTAGRAM))
        assertFalse(session.start(Platform.YOUTUBE))
        assertEquals(Platform.INSTAGRAM, (session.state.value as CalibrationState.Running).platform)
    }

    @Test
    fun cancelDropsTheRunWithoutAResult() = runTest {
        newSession()
        session.start(Platform.INSTAGRAM)
        session.plusOne()

        session.cancel()

        assertEquals(CalibrationState.Idle(), session.state.value)
        assertNull(session.stop())
    }

    // --- Log ---

    private fun log() = CalibrationLog(
        File(folder.root, "calibration/${CalibrationLog.FILE_NAME}"),
        zone = { ZoneOffset.UTC },
        ioDispatcher = Dispatchers.Unconfined,
    )

    @Test
    fun appendsRowsUnderOneHeader() = runTest {
        val log = log()
        log.append(result(auto = 18, manual = 20))
        log.append(result(auto = 5, manual = 5, version = null))

        val lines = File(folder.root, "calibration/${CalibrationLog.FILE_NAME}").readLines()
        assertEquals(
            listOf(
                "started_at,platform,auto,manual,duration_sec,rules_version,app_version_name",
                "2025-10-09T08:53:20Z,INSTAGRAM,18,20,300,2026102002,412.0.0.35.87",
                "2025-10-09T08:53:20Z,INSTAGRAM,5,5,300,2026102002,",
            ),
            lines,
        )
        assertEquals(2, log.count())
    }

    @Test
    fun odditiesInAVersionNameCannotBreakTheCsv() {
        val row = CalibrationLog.row(result(1, 1, version = "=1+2,\"beta\""), ZoneOffset.UTC)
        assertTrue(row.endsWith(",\"'=1+2,\"\"beta\"\"\""))
    }

    @Test
    fun exportCopiesTheLogAndDeleteRemovesIt() = runTest {
        val log = log()
        val exports = File(folder.root, "exports")
        assertNull(log.exportTo(exports))
        assertEquals(0, log.count())

        log.append(result(auto = 1, manual = 2))
        val exported = log.exportTo(exports)!!

        assertEquals(CalibrationLog.EXPORT_NAME, exported.name)
        assertEquals(2, exported.readLines().size)

        log.delete()
        assertEquals(0, log.count())
        assertNull(log.exportTo(exports))
    }
}