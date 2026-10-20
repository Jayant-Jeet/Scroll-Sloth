package com.halfbloodcoder.scrollsloth.stats

import com.halfbloodcoder.scrollsloth.detect.Platform

enum class Period { DAY, WEEK, MONTH }

/**
 * Totals for one local-calendar period, [startMs, endMs).
 *
 * Reels are attributed to the period their viewing started in. [minutes] is total dwell time.
 * [longestSessionReels] counts only the reels of a session that fall inside the period, so a
 * session crossing midnight is split between the two days. [baselineDailyMedian] is the median
 * daily count over the 28 days before [startMs], or null with fewer than 7 days of history.
 */
data class PeriodStats(
    val period: Period,
    val startMs: Long,
    val endMs: Long,
    val reels: Int,
    val minutes: Double,
    val byPlatform: Map<Platform, Int>,
    val lateNightReels: Int,
    val longestSessionReels: Int,
    val activeDays: Int,
    val baselineDailyMedian: Double?,
)

/**
 * A period's totals plus one [Period.DAY] entry per day that has started, in order.
 * For the current week or month, days after today are left out; past periods list every day.
 */
data class PeriodReport(val stats: PeriodStats, val days: List<PeriodStats>)
