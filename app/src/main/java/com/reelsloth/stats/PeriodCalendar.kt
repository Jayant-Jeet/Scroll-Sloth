package com.reelsloth.stats

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Local calendar days [start, endExclusive). Converted to epoch ms in a zone, so DST days are 23 or 25 hours. */
data class DateRange(val start: LocalDate, val endExclusive: LocalDate) {
    init {
        require(start.isBefore(endExclusive)) { "Empty date range." }
    }

    fun startMs(zone: ZoneId): Long = start.startMs(zone)

    fun endMs(zone: ZoneId): Long = endExclusive.startMs(zone)

    fun days(): List<LocalDate> = generateSequence(start) { it.plusDays(1) }.takeWhile { it < endExclusive }.toList()
}

internal fun LocalDate.startMs(zone: ZoneId): Long = atStartOfDay(zone).toInstant().toEpochMilli()

object PeriodCalendar {
    /** The [period] containing [today], moved by [offset] periods (-1 is the previous one). */
    fun range(period: Period, today: LocalDate, weekStart: DayOfWeek, offset: Int = 0): DateRange {
        val start = when (period) {
            Period.DAY -> today.plusDays(offset.toLong())
            Period.WEEK -> today.with(TemporalAdjusters.previousOrSame(weekStart)).plusWeeks(offset.toLong())
            Period.MONTH -> today.withDayOfMonth(1).plusMonths(offset.toLong())
        }
        val end = when (period) {
            Period.DAY -> start.plusDays(1)
            Period.WEEK -> start.plusWeeks(1)
            Period.MONTH -> start.plusMonths(1)
        }
        return DateRange(start, end)
    }
}
