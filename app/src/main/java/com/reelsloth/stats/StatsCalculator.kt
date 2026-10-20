package com.reelsloth.stats

import com.reelsloth.data.ReelEventEntity
import com.reelsloth.detect.Platform
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max

/**
 * Pure statistics over stored reel events. No Android types and no clock: callers pass the zone,
 * the period's dates and "today".
 */
object StatsCalculator {
    /** Reels whose gap (previous end to next start) is shorter than this belong to one session. */
    const val SESSION_GAP_MS = 5 * 60_000L
    const val BASELINE_DAYS = 28L
    const val MIN_BASELINE_DAYS = 7

    /** Late night is [23:00, 05:00) local time. */
    const val LATE_NIGHT_START_HOUR = 23
    const val LATE_NIGHT_END_HOUR = 5

    private const val MS_PER_MINUTE = 60_000.0

    /** First day whose events [report] needs, for the baselines of every day in [range]. */
    fun historyStart(range: DateRange): LocalDate = range.start.minusDays(BASELINE_DAYS)

    /**
     * Stats for [range] and for each of its days up to and including [today].
     *
     * @param events every event from [historyStart] to the end of [range]; any order.
     * @param firstEventMs timestamp of the oldest event ever stored, used to tell "no reels" from "no data yet".
     */
    fun report(
        period: Period,
        range: DateRange,
        events: List<ReelEventEntity>,
        zone: ZoneId,
        firstEventMs: Long?,
        today: LocalDate,
    ): PeriodReport {
        val history = History(events.sortedBy { it.timestampMs }, zone, firstEventMs)
        val lastDayExclusive = minOf(range.endExclusive, today.plusDays(1))
        val days = range.days()
            .filter { it < lastDayExclusive }
            .map { day -> history.stats(Period.DAY, DateRange(day, day.plusDays(1))) }
        return PeriodReport(history.stats(period, range), days)
    }

    fun isLateNight(timestampMs: Long, zone: ZoneId): Boolean {
        val hour = Instant.ofEpochMilli(timestampMs).atZone(zone).hour
        return hour >= LATE_NIGHT_START_HOUR || hour < LATE_NIGHT_END_HOUR
    }

    /** Most reels in one run where each reel starts less than [SESSION_GAP_MS] after the previous ended. */
    fun longestSession(sortedEvents: List<ReelEventEntity>): Int {
        var longest = 0
        var current = 0
        var previousEnd: Long? = null
        for (event in sortedEvents) {
            current = if (previousEnd != null && event.timestampMs - previousEnd < SESSION_GAP_MS) current + 1 else 1
            longest = max(longest, current)
            previousEnd = max(previousEnd ?: Long.MIN_VALUE, event.timestampMs + event.dwellMs)
        }
        return longest
    }

    fun median(values: List<Int>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle].toDouble() else (sorted[middle - 1] + sorted[middle]) / 2.0
    }

    private class History(
        private val sorted: List<ReelEventEntity>,
        private val zone: ZoneId,
        firstEventMs: Long?,
    ) {
        private val firstDay: LocalDate? = firstEventMs?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
        private val dailyCounts: Map<LocalDate, Int> = sorted.groupingBy { it.localDate() }.eachCount()

        fun stats(period: Period, range: DateRange): PeriodStats {
            val startMs = range.startMs(zone)
            val endMs = range.endMs(zone)
            val events = sorted.subList(indexOfFirstAtOrAfter(startMs), indexOfFirstAtOrAfter(endMs))
            return PeriodStats(
                period = period,
                startMs = startMs,
                endMs = endMs,
                reels = events.size,
                minutes = events.sumOf { it.dwellMs } / MS_PER_MINUTE,
                byPlatform = events.mapNotNull { event -> Platform.entries.firstOrNull { it.name == event.platform } }
                    .groupingBy { it }
                    .eachCount(),
                lateNightReels = events.count { isLateNight(it.timestampMs, zone) },
                longestSessionReels = longestSession(events),
                activeDays = events.map { it.localDate() }.distinct().size,
                baselineDailyMedian = baselineBefore(range.start),
            )
        }

        /** Median daily count over the 28 days before [day], counting only days since the first event. */
        private fun baselineBefore(day: LocalDate): Double? {
            val first = firstDay ?: return null
            val windowStart = day.minusDays(BASELINE_DAYS)
            val dataStart = if (first.isAfter(windowStart)) first else windowStart
            if (!dataStart.isBefore(day)) return null
            val days = DateRange(dataStart, day).days()
            if (days.size < MIN_BASELINE_DAYS) return null
            return median(days.map { dailyCounts[it] ?: 0 })
        }

        private fun indexOfFirstAtOrAfter(ms: Long): Int {
            var low = 0
            var high = sorted.size
            while (low < high) {
                val mid = (low + high) ushr 1
                if (sorted[mid].timestampMs < ms) low = mid + 1 else high = mid
            }
            return low
        }

        private fun ReelEventEntity.localDate(): LocalDate = Instant.ofEpochMilli(timestampMs).atZone(zone).toLocalDate()
    }
}
