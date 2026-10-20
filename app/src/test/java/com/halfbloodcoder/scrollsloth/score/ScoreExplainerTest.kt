package com.halfbloodcoder.scrollsloth.score

import com.halfbloodcoder.scrollsloth.stats.Period
import com.halfbloodcoder.scrollsloth.stats.PeriodStats
import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreExplainerTest {
    private val config = ScoreConfig()
    private val engine = ScoreEngine(config)

    private val day = PeriodStats(
        period = Period.DAY,
        startMs = 0,
        endMs = 86_400_000,
        reels = 150,
        minutes = 60.0,
        byPlatform = emptyMap(),
        lateNightReels = 30,
        longestSessionReels = 75,
        activeDays = 1,
        baselineDailyMedian = 100.0,
    )

    @Test
    fun explainsEachLazinessComponentWithItsInputs() {
        val lines = ScoreExplainer.explain(engine.scoreDay(day).laziness, day, config)

        assertEquals(listOf("Volume", "Above your usual", "Late night", "Longest session"), lines.map { it.label })
        assertEquals(listOf(75, 50, 40, 75), lines.map { it.value })
        assertEquals(listOf(34, 8, 8, 15), lines.map { it.adds })
        assertEquals("150 reels. A heavy day is 200.", lines[0].detail)
        assertEquals("Your usual day is 100 reels. Double that maxes this out.", lines[1].detail)
        assertEquals("30 of 150 reels between 23:00 and 05:00. Maxes out at 50%.", lines[2].detail)
        assertEquals("Longest run: 75 reels. A heavy session is 100.", lines[3].detail)
    }

    @Test
    fun explainsUnproductivityAndMissingBaselineAndAveraging() {
        val noHistory = day.copy(baselineDailyMedian = null, period = Period.WEEK)
        val scores = engine.scoreDays(listOf(day.copy(baselineDailyMedian = null), day.copy(baselineDailyMedian = null)))

        val relative = ScoreExplainer.explain(scores.laziness, noHistory, config)[1]
        assertEquals("Not enough history yet. It needs 7 days. Averaged over 2 days.", relative.detail)

        val minutes = ScoreExplainer.explain(scores.unproductivity, noHistory, config).single()
        assertEquals("Time spent", minutes.label)
        assertEquals(50, minutes.value)
        assertEquals("60 minutes. Budget is 30 a day; 120 maxes this out. Averaged over 2 days.", minutes.detail)
    }

    @Test
    fun fractionalBaselineShowsOneDecimal() {
        val lines = ScoreExplainer.explain(engine.scoreDay(day).laziness, day.copy(baselineDailyMedian = 42.5), config)
        assertEquals("Your usual day is 42.5 reels. Double that maxes this out.", lines[1].detail)
    }
}
