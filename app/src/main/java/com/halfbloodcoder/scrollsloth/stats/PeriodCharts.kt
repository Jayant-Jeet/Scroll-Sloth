package com.halfbloodcoder.scrollsloth.stats

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.ceil

/** One bar of the week chart. [reels] is null for days that have not happened yet. */
data class DayBar(val date: LocalDate, val reels: Int?, val isToday: Boolean)

data class WeekChart(
    val bars: List<DayBar>,
    val total: Int,
    /** Days so far that were tracked: from the first stored reel (or the week start) to today. */
    val trackedDays: Int,
    /** [total] / [trackedDays], or null before anything was tracked this week. */
    val averagePerDay: Double?,
)

/** One square of the month heat map. [reels] is null for future days; [level] is 0..[PeriodCharts.MAX_LEVEL]. */
data class MonthCell(val date: LocalDate, val reels: Int?, val level: Int, val isToday: Boolean)

data class DayCount(val date: LocalDate, val reels: Int)

data class MonthChart(
    /** Empty squares before the 1st so it lines up under the week-start column. */
    val leadingBlanks: Int,
    val weekStart: DayOfWeek,
    val cells: List<MonthCell>,
    val total: Int,
    /** Fewest reels among finished, tracked days. Null until there are [PeriodCharts.MIN_DAYS_FOR_BEST_WORST]. */
    val best: DayCount?,
    /** Most reels among finished, tracked days. Null under the same rule as [best], or when every day was 0. */
    val worst: DayCount?,
)

/**
 * Turns a [PeriodReport] into what the Week and Month charts draw. Pure: dates come from the
 * report's day starts in [zone], and [trackingStart] (the day of the first stored reel) keeps
 * days before tracking began out of averages and best/worst picks.
 */
object PeriodCharts {
    const val MAX_LEVEL = 4
    const val MIN_DAYS_FOR_BEST_WORST = 2

    fun week(report: PeriodReport, today: LocalDate, trackingStart: LocalDate?, zone: ZoneId): WeekChart {
        val start = report.stats.startMs.toDate(zone)
        val end = report.stats.endMs.toDate(zone)
        val counts = report.days.associate { it.startMs.toDate(zone) to it.reels }
        val bars = generateSequence(start) { it.plusDays(1) }.takeWhile { it < end }.map { date ->
            DayBar(date, if (date > today) null else counts[date] ?: 0, date == today)
        }.toList()
        val tracked = trackedDates(start, minOf(today, end.minusDays(1)), trackingStart)
        val total = report.stats.reels
        return WeekChart(bars, total, tracked.size, if (tracked.isEmpty()) null else total.toDouble() / tracked.size)
    }

    fun month(
        report: PeriodReport,
        today: LocalDate,
        trackingStart: LocalDate?,
        weekStart: DayOfWeek,
        heavyDaily: Int,
        zone: ZoneId,
    ): MonthChart {
        val start = report.stats.startMs.toDate(zone)
        val end = report.stats.endMs.toDate(zone)
        val counts = report.days.associate { it.startMs.toDate(zone) to it.reels }
        val cells = generateSequence(start) { it.plusDays(1) }.takeWhile { it < end }.map { date ->
            val reels = if (date > today) null else counts[date] ?: 0
            MonthCell(date, reels, level(reels ?: 0, heavyDaily), date == today)
        }.toList()

        val finished = trackedDates(start, minOf(today.minusDays(1), end.minusDays(1)), trackingStart)
            .map { DayCount(it, counts[it] ?: 0) }
        val enough = finished.size >= MIN_DAYS_FOR_BEST_WORST
        // minBy/maxBy keep the first of equal values, so ties go to the earlier day.
        val best = if (enough) finished.minBy { it.reels } else null
        val worst = if (enough) finished.maxBy { it.reels }.takeIf { it.reels > 0 } else null
        return MonthChart(
            leadingBlanks = (start.dayOfWeek.value - weekStart.value + 7) % 7,
            weekStart = weekStart,
            cells = cells,
            total = report.stats.reels,
            best = best,
            worst = worst,
        )
    }

    /** 0 for no reels; otherwise quarters of a heavy day, so [heavyDaily] or more is the darkest. */
    fun level(reels: Int, heavyDaily: Int): Int {
        if (reels <= 0) return 0
        return ceil(reels * MAX_LEVEL / heavyDaily.coerceAtLeast(1).toDouble()).toInt().coerceIn(1, MAX_LEVEL)
    }

    /** Spoken summary of the week chart for screen readers. */
    fun describe(chart: WeekChart, locale: Locale = Locale.getDefault()): String {
        val days = chart.bars.joinToString(", ") { bar ->
            val name = bar.date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
            when {
                bar.reels == null -> "$name not yet"
                bar.isToday -> "$name ${bar.reels} so far, today"
                else -> "$name ${bar.reels}"
            }
        }
        return "Reels per day this week. $days."
    }

    /** Spoken summary of the month heat map for screen readers. */
    fun describe(chart: MonthChart, locale: Locale = Locale.getDefault()): String {
        val month = chart.cells.firstOrNull()?.date?.month?.getDisplayName(TextStyle.FULL, locale).orEmpty()
        val counted = chart.cells.filter { it.reels != null }
        val withReels = counted.count { (it.reels ?: 0) > 0 }
        val busiest = counted.maxByOrNull { it.reels ?: 0 }?.takeIf { (it.reels ?: 0) > 0 }
        val busiestText = busiest?.let { " Busiest day so far: ${it.date.dayOfMonth} $month, ${it.reels} reels." }.orEmpty()
        return "Heat map of reels per day in $month. Reels on $withReels of ${counted.size} days so far.$busiestText"
    }

    private fun trackedDates(from: LocalDate, to: LocalDate, trackingStart: LocalDate?): List<LocalDate> {
        if (trackingStart == null) return emptyList()
        val first = maxOf(from, trackingStart)
        return generateSequence(first) { it.plusDays(1) }.takeWhile { it <= to }.toList()
    }

    private fun Long.toDate(zone: ZoneId): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()
}
