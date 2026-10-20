package com.halfbloodcoder.scrollsloth.score

import com.halfbloodcoder.scrollsloth.stats.PeriodStats
import com.halfbloodcoder.scrollsloth.stats.StatsCalculator
import java.util.Locale
import kotlin.math.roundToInt

/** One line of "why this score": a component, its 0..100 value, what it adds, and the inputs behind it. */
data class ExplanationLine(val label: String, val value: Int, val adds: Int, val detail: String)

/**
 * Builds plain-language reasons for a [ScoreResult] from the same numbers the score used.
 * Every number in the text comes from [PeriodStats], [ScoreConfig] or the result itself.
 */
object ScoreExplainer {
    fun explain(result: ScoreResult, stats: PeriodStats, config: ScoreConfig): List<ExplanationLine> {
        val days = result.daysAveraged
        val perDay = if (days > 1) " Averaged over $days days." else ""
        return result.components.map { component ->
            val detail = when (component.id) {
                ScoreComponentId.VOLUME ->
                    "${stats.reels} reels. A heavy day is ${config.heavyDaily}."
                ScoreComponentId.RELATIVE -> stats.baselineDailyMedian?.let {
                    "Your usual day is ${it.formatCount()} reels. Double that maxes this out."
                } ?: "Not enough history yet. It needs ${StatsCalculator.MIN_BASELINE_DAYS} days."
                ScoreComponentId.LATE_NIGHT ->
                    "${stats.lateNightReels} of ${stats.reels} reels between 23:00 and 05:00. " +
                        "Maxes out at ${(config.lateShareHeavy * 100).roundToInt()}%."
                ScoreComponentId.SESSION ->
                    "Longest run: ${stats.longestSessionReels} reels. A heavy session is ${config.sessionHeavy}."
                ScoreComponentId.MINUTES ->
                    "${stats.minutes.roundToInt()} minutes. Budget is ${config.dailyMinutesBudget} a day; " +
                        "${config.dailyMinutesBudget * ScoreEngine.UNPRODUCTIVE_BUDGET_MULTIPLE} maxes this out."
            }
            ExplanationLine(
                label = component.id.label,
                value = component.value.roundToInt(),
                adds = component.contribution.roundToInt(),
                detail = detail + perDay,
            )
        }
    }

    private fun Double.formatCount(): String =
        if (this % 1.0 == 0.0) toLong().toString() else "%.1f".format(Locale.ROOT, this)

    val ScoreComponentId.label: String
        get() = when (this) {
            ScoreComponentId.VOLUME -> "Volume"
            ScoreComponentId.RELATIVE -> "Above your usual"
            ScoreComponentId.LATE_NIGHT -> "Late night"
            ScoreComponentId.SESSION -> "Longest session"
            ScoreComponentId.MINUTES -> "Time spent"
        }
}
