package com.reelsloth.score

import com.reelsloth.stats.Period
import com.reelsloth.stats.PeriodStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ScoreEngineTest {
    private val engine = ScoreEngine()

    @Test
    fun zeroReelDayScoresZeroAndIsFullyAwake() {
        val scores = engine.scoreDay(day(reels = 0, baseline = 120.0))

        assertEquals(0, scores.laziness.score)
        assertEquals("Fully awake", scores.laziness.band.label)
        assertEquals(0, scores.unproductivity.score)
        scores.laziness.components.forEach { assertEquals(0.0, it.value, DELTA) }
    }

    @Test
    fun exactlyHeavyDailyMaxesVolumeOnly() {
        val scores = engine.scoreDay(day(reels = 200))

        assertEquals(100.0, scores.laziness.component(ScoreComponentId.VOLUME).value, DELTA)
        assertEquals(45.0, scores.laziness.component(ScoreComponentId.VOLUME).contribution, DELTA)
        assertEquals(45, scores.laziness.score)
        assertEquals("Sofa sloth", scores.laziness.band.label)
    }

    @Test
    fun volumeIsClampedAboveHeavyDaily() {
        val scores = engine.scoreDay(day(reels = 450))
        assertEquals(100.0, scores.laziness.component(ScoreComponentId.VOLUME).value, DELTA)
    }

    @Test
    fun doubleTheBaselineMaxesRelative() {
        val scores = engine.scoreDay(day(reels = 100, baseline = 50.0))

        assertEquals(100.0, scores.laziness.component(ScoreComponentId.RELATIVE).value, DELTA)
        // 0.45 * 50 + 0.15 * 100 = 37.5, rounds half up.
        assertEquals(37.5, scores.laziness.exactScore, DELTA)
        assertEquals(38, scores.laziness.score)
        assertEquals("Casual scroller", scores.laziness.band.label)
    }

    @Test
    fun relativeScalesBetweenBaselineAndDoubleAndIgnoresQuietDays() {
        assertEquals(50.0, relative(reels = 75, baseline = 50.0), DELTA)
        assertEquals(0.0, relative(reels = 50, baseline = 50.0), DELTA)
        assertEquals(0.0, relative(reels = 10, baseline = 50.0), DELTA)
        assertEquals(100.0, relative(reels = 500, baseline = 50.0), DELTA)
    }

    @Test
    fun nullBaselineGivesZeroRelative() {
        assertEquals(0.0, relative(reels = 180, baseline = null), DELTA)
    }

    @Test
    fun zeroBaselineCountsAnyReelsAsFullyAboveUsual() {
        assertEquals(100.0, relative(reels = 1, baseline = 0.0), DELTA)
        assertEquals(0.0, relative(reels = 0, baseline = 0.0), DELTA)
    }

    @Test
    fun lateNightShareIsMeasuredAgainstLateShareHeavy() {
        assertEquals(100.0, late(reels = 100, late = 50), DELTA)
        assertEquals(50.0, late(reels = 100, late = 25), DELTA)
        assertEquals(100.0, late(reels = 10, late = 10), DELTA)
        assertEquals(0.0, late(reels = 0, late = 0), DELTA)
    }

    @Test
    fun sessionIsMeasuredAgainstSessionHeavy() {
        val component = engine.scoreDay(day(reels = 60, session = 50)).laziness.component(ScoreComponentId.SESSION)
        assertEquals(50.0, component.value, DELTA)
        assertEquals(10.0, component.contribution, DELTA)
        assertEquals(
            100.0,
            engine.scoreDay(day(reels = 300, session = 250)).laziness.component(ScoreComponentId.SESSION).value,
            DELTA,
        )
    }

    @Test
    fun unproductivityMaxesAtFourTimesTheMinutesBudget() {
        assertEquals(0, engine.scoreDay(day(minutes = 0.0)).unproductivity.score)
        assertEquals(25, engine.scoreDay(day(minutes = 30.0)).unproductivity.score)
        assertEquals(100, engine.scoreDay(day(minutes = 120.0)).unproductivity.score)
        assertEquals(100, engine.scoreDay(day(minutes = 300.0)).unproductivity.score)
        assertEquals("Fossilised", engine.scoreDay(day(minutes = 120.0)).unproductivity.band.label)
    }

    @Test
    fun everythingMaxedIsOneHundredAndFossilised() {
        val scores = engine.scoreDay(day(reels = 400, baseline = 100.0, late = 400, session = 400, minutes = 500.0))
        assertEquals(100, scores.laziness.score)
        assertEquals("Fossilised", scores.laziness.band.label)
    }

    @Test
    fun configChangesTheThresholds() {
        val custom = ScoreEngine(
            ScoreConfig(heavyDaily = 50, dailyMinutesBudget = 10, sessionHeavy = 20, lateShareHeavy = 0.25),
        )
        val scores = custom.scoreDay(day(reels = 25, late = 5, session = 10, minutes = 20.0))

        assertEquals(50.0, scores.laziness.component(ScoreComponentId.VOLUME).value, DELTA)
        assertEquals(80.0, scores.laziness.component(ScoreComponentId.LATE_NIGHT).value, DELTA)
        assertEquals(50.0, scores.laziness.component(ScoreComponentId.SESSION).value, DELTA)
        assertEquals(50, scores.unproductivity.score)
    }

    @Test
    fun customBandLabelsAreUsed() {
        val labels = listOf("A", "B", "C", "D", "E")
        val scores = ScoreEngine(ScoreConfig(bandLabels = labels)).scoreDay(day(reels = 200))
        assertEquals("C", scores.laziness.band.label)
    }

    @Test
    fun bandBoundaries() {
        val bands = ScoreBands()
        mapOf(
            0 to "Fully awake", 19 to "Fully awake",
            20 to "Casual scroller", 39 to "Casual scroller",
            40 to "Sofa sloth", 59 to "Sofa sloth",
            60 to "Reel hibernator", 79 to "Reel hibernator",
            80 to "Fossilised", 100 to "Fossilised",
            -5 to "Fully awake", 140 to "Fossilised",
        ).forEach { (score, label) -> assertEquals("score $score", label, bands.bandFor(score).label) }
        assertEquals(listOf(0 to 19, 20 to 39, 40 to 59, 60 to 79, 80 to 100), bands.bands.map { it.min to it.max })
    }

    @Test
    fun bandComesFromTheRoundedScore() {
        val roundedUp = engine.scoreDays(listOf(day(reels = 200), day(reels = 0), day(reels = 200), day(reels = 0)))
            .laziness
        // Mean of 45, 0, 45, 0 is 22.5 -> 23 -> Casual scroller.
        assertEquals(22.5, roundedUp.exactScore, DELTA)
        assertEquals(23, roundedUp.score)
        assertEquals("Casual scroller", roundedUp.band.label)
    }

    @Test
    fun invalidConfigIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { ScoreConfig(heavyDaily = 0) }
        assertThrows(IllegalArgumentException::class.java) { ScoreConfig(dailyMinutesBudget = 0) }
        assertThrows(IllegalArgumentException::class.java) { ScoreConfig(sessionHeavy = -1) }
        assertThrows(IllegalArgumentException::class.java) { ScoreConfig(lateShareHeavy = 0.0) }
        assertThrows(IllegalArgumentException::class.java) { ScoreConfig(lateShareHeavy = 1.5) }
        assertThrows(IllegalArgumentException::class.java) { ScoreConfig(bandLabels = listOf("a", "b")) }
        assertThrows(IllegalArgumentException::class.java) { ScoreConfig(bandLabels = listOf("a", "b", " ", "d", "e")) }
    }

    @Test
    fun aFakeWeekAveragesDailyScoresIncludingAZeroDay() {
        // Baseline 100 every day. Hand-worked laziness and unproductivity per day:
        val week = listOf(
            day(reels = 100, baseline = 100.0, late = 0, session = 40, minutes = 30.0), // 30.5, 25
            day(reels = 0, baseline = 100.0), //                                           0, 0
            day(reels = 200, baseline = 100.0, late = 100, session = 100, minutes = 120.0), // 100, 100
            day(reels = 150, baseline = 100.0, late = 30, session = 75, minutes = 60.0), // 64.25, 50
            day(reels = 50, baseline = 100.0, late = 0, session = 10, minutes = 15.0), // 13.25, 12.5
            day(reels = 300, baseline = 100.0, late = 150, session = 120, minutes = 240.0), // 100, 100
            day(reels = 20, baseline = 100.0, late = 20, session = 20, minutes = 6.0), // 28.5, 5
        )
        val daily = week.map { engine.scoreDay(it) }
        assertDoubles(listOf(30.5, 0.0, 100.0, 64.25, 13.25, 100.0, 28.5), daily.map { it.laziness.exactScore })
        assertDoubles(listOf(25.0, 0.0, 100.0, 50.0, 12.5, 100.0, 5.0), daily.map { it.unproductivity.exactScore })
        assertEquals(
            listOf("Casual scroller", "Fully awake", "Fossilised", "Reel hibernator", "Fully awake", "Fossilised", "Casual scroller"),
            daily.map { it.laziness.band.label },
        )

        val scores = engine.scoreDays(week)
        assertEquals(336.5 / 7, scores.laziness.exactScore, DELTA)
        assertEquals(48, scores.laziness.score)
        assertEquals("Sofa sloth", scores.laziness.band.label)
        assertEquals(292.5 / 7, scores.unproductivity.exactScore, DELTA)
        assertEquals(42, scores.unproductivity.score)
        assertEquals(7, scores.laziness.daysAveraged)
        assertEquals(360.0 / 7, scores.laziness.component(ScoreComponentId.VOLUME).value, DELTA)
        assertEquals(
            scores.laziness.exactScore,
            scores.laziness.components.sumOf { it.contribution },
            DELTA,
        )
    }

    @Test
    fun aMonthAveragesOverEveryDayInIt() {
        val month = List(30) { index ->
            if (index < 2) day(reels = 400, baseline = 100.0, late = 400, session = 400, minutes = 999.0) else day()
        }
        val scores = engine.scoreDays(month)
        assertEquals(200.0 / 30, scores.laziness.exactScore, DELTA)
        assertEquals(7, scores.laziness.score)
        assertEquals(7, scores.unproductivity.score)
        assertEquals(30, scores.unproductivity.daysAveraged)
    }

    @Test
    fun noDaysScoresZero() {
        val scores = engine.scoreDays(emptyList())
        assertEquals(0, scores.laziness.score)
        assertEquals(0, scores.laziness.daysAveraged)
    }

    private fun relative(reels: Int, baseline: Double?) =
        engine.scoreDay(day(reels = reels, baseline = baseline)).laziness.component(ScoreComponentId.RELATIVE).value

    private fun late(reels: Int, late: Int) =
        engine.scoreDay(day(reels = reels, late = late)).laziness.component(ScoreComponentId.LATE_NIGHT).value

    private companion object {
        const val DELTA = 1e-9

        fun assertDoubles(expected: List<Double>, actual: List<Double>) {
            assertEquals(expected.size, actual.size)
            expected.zip(actual).forEachIndexed { i, (e, a) -> assertEquals("index $i", e, a, DELTA) }
        }

        fun day(
            reels: Int = 0,
            baseline: Double? = null,
            late: Int = 0,
            session: Int = 0,
            minutes: Double = 0.0,
        ) = PeriodStats(
            period = Period.DAY,
            startMs = 0,
            endMs = 86_400_000,
            reels = reels,
            minutes = minutes,
            byPlatform = emptyMap(),
            lateNightReels = late,
            longestSessionReels = session,
            activeDays = if (reels > 0) 1 else 0,
            baselineDailyMedian = baseline,
        )
    }
}
