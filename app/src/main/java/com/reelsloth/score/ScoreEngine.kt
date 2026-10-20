package com.reelsloth.score

import com.reelsloth.stats.PeriodStats
import kotlin.math.max
import kotlin.math.roundToInt

enum class ScoreKind { LAZINESS, UNPRODUCTIVITY }

enum class ScoreComponentId { VOLUME, RELATIVE, LATE_NIGHT, SESSION, MINUTES }

/** One part of a score: its 0..100 value, its weight, and what it adds to the score. */
data class ScoreComponent(val id: ScoreComponentId, val value: Double, val weight: Double) {
    val contribution: Double get() = value * weight
}

/**
 * A score and everything needed to explain it. [exactScore] is the unrounded 0..100 value;
 * [score] is it rounded, and [band] comes from [score]. For a week or month, every value is the
 * mean over [daysAveraged] days.
 */
data class ScoreResult(
    val kind: ScoreKind,
    val exactScore: Double,
    val score: Int,
    val band: ScoreBand,
    val components: List<ScoreComponent>,
    val daysAveraged: Int,
) {
    fun component(id: ScoreComponentId): ScoreComponent = components.first { it.id == id }
}

data class Scores(val laziness: ScoreResult, val unproductivity: ScoreResult)

/**
 * Deterministic scoring. Per day:
 * - volume = clamp(reels / heavyDaily) * 100
 * - relative = baseline == null ? 0 : clamp(reels / baseline - 1) * 100
 * - late = clamp((lateNightReels / max(reels, 1)) / lateShareHeavy) * 100
 * - session = clamp(longestSessionReels / sessionHeavy) * 100
 * - laziness = 0.45 volume + 0.15 relative + 0.20 late + 0.20 session
 * - unproductivity = clamp(minutes / (dailyMinutesBudget * 4)) * 100
 *
 * A baseline of 0 with reels watched counts as fully above baseline. Week and month scores are the
 * mean of the daily scores, so a zero-reel day pulls the mean down.
 */
class ScoreEngine(val config: ScoreConfig = ScoreConfig()) {
    private val bands = config.bands

    fun scoreDay(day: PeriodStats): Scores = scoreDays(listOf(day))

    /** Mean of the daily scores of [days]. An empty list scores 0. */
    fun scoreDays(days: List<PeriodStats>): Scores {
        val daily = days.map(::componentsFor)
        fun mean(pick: (DayComponents) -> Double) = if (daily.isEmpty()) 0.0 else daily.sumOf(pick) / daily.size

        val laziness = listOf(
            ScoreComponent(ScoreComponentId.VOLUME, mean { it.volume }, VOLUME_WEIGHT),
            ScoreComponent(ScoreComponentId.RELATIVE, mean { it.relative }, RELATIVE_WEIGHT),
            ScoreComponent(ScoreComponentId.LATE_NIGHT, mean { it.late }, LATE_WEIGHT),
            ScoreComponent(ScoreComponentId.SESSION, mean { it.session }, SESSION_WEIGHT),
        )
        val unproductivity = listOf(ScoreComponent(ScoreComponentId.MINUTES, mean { it.minutes }, 1.0))
        return Scores(
            laziness = result(ScoreKind.LAZINESS, laziness, days.size),
            unproductivity = result(ScoreKind.UNPRODUCTIVITY, unproductivity, days.size),
        )
    }

    private fun result(kind: ScoreKind, components: List<ScoreComponent>, days: Int): ScoreResult {
        val exact = components.sumOf { it.contribution }.coerceIn(0.0, ScoreBands.MAX_SCORE.toDouble())
        val rounded = exact.roundToInt()
        return ScoreResult(kind, exact, rounded, bands.bandFor(rounded), components, days)
    }

    private class DayComponents(
        val volume: Double,
        val relative: Double,
        val late: Double,
        val session: Double,
        val minutes: Double,
    )

    private fun componentsFor(day: PeriodStats): DayComponents {
        val reels = day.reels.toDouble()
        val baseline = day.baselineDailyMedian
        val relative = when {
            baseline == null -> 0.0
            baseline <= 0.0 -> if (reels > 0) 1.0 else 0.0
            else -> clamp((reels / baseline - 1.0) / RELATIVE_HEAVY_RATIO)
        }
        val lateShare = day.lateNightReels / max(day.reels, 1).toDouble()
        return DayComponents(
            volume = clamp(reels / config.heavyDaily) * 100,
            relative = relative * 100,
            late = clamp(lateShare / config.lateShareHeavy) * 100,
            session = clamp(day.longestSessionReels.toDouble() / config.sessionHeavy) * 100,
            minutes = clamp(day.minutes / (config.dailyMinutesBudget * UNPRODUCTIVE_BUDGET_MULTIPLE)) * 100,
        )
    }

    private fun clamp(value: Double) = value.coerceIn(0.0, 1.0)

    companion object {
        const val VOLUME_WEIGHT = 0.45
        const val RELATIVE_WEIGHT = 0.15
        const val LATE_WEIGHT = 0.20
        const val SESSION_WEIGHT = 0.20

        /** Reels this far above baseline (as a ratio) max out the relative component: double = 100. */
        const val RELATIVE_HEAVY_RATIO = 1.0
        const val UNPRODUCTIVE_BUDGET_MULTIPLE = 4
    }
}
