package com.reelsloth.stats

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PeriodChartsTest {
    private val zone = ZoneId.of("Europe/London")
    private val events = EventBuilder(zone)

    // Wednesday.
    private val today = LocalDate.of(2026, 10, 21)

    private fun report(period: Period, weekStart: DayOfWeek = DayOfWeek.MONDAY): PeriodReport {
        val range = PeriodCalendar.range(period, today, weekStart)
        val first = events.events.minOfOrNull { it.timestampMs }
        return StatsCalculator.report(period, range, events.events, zone, first, today)
    }

    @Test
    fun weekHasSevenBarsWithTodayMarkedAndFutureDaysEmpty() {
        events.run(LocalDate.of(2026, 10, 19).atTime(10, 0), 5)
        events.run(LocalDate.of(2026, 10, 21).atTime(10, 0), 3)

        val chart = PeriodCharts.week(report(Period.WEEK), today, LocalDate.of(2026, 10, 19), zone)

        assertEquals((19..25).map { LocalDate.of(2026, 10, it) }, chart.bars.map { it.date })
        assertEquals(listOf(5, 0, 3, null, null, null, null), chart.bars.map { it.reels })
        assertEquals(listOf(false, false, true, false, false, false, false), chart.bars.map { it.isToday })
        assertEquals(8, chart.total)
        assertEquals(3, chart.trackedDays)
        assertEquals(8 / 3.0, chart.averagePerDay!!, 1e-9)
    }

    @Test
    fun weekAverageIgnoresDaysBeforeTrackingStarted() {
        events.run(LocalDate.of(2026, 10, 20).atTime(10, 0), 6)

        val chart = PeriodCharts.week(report(Period.WEEK), today, LocalDate.of(2026, 10, 20), zone)

        assertEquals(2, chart.trackedDays)
        assertEquals(3.0, chart.averagePerDay!!, 1e-9)
    }

    @Test
    fun weekWithNoTrackingHasNoAverage() {
        val chart = PeriodCharts.week(report(Period.WEEK), today, trackingStart = null, zone = zone)

        assertEquals(0, chart.total)
        assertNull(chart.averagePerDay)
    }

    @Test
    fun weekFollowsTheConfiguredStartDay() {
        val chart = PeriodCharts.week(report(Period.WEEK, DayOfWeek.SUNDAY), today, today, zone)

        assertEquals(LocalDate.of(2026, 10, 18), chart.bars.first().date)
        assertEquals(DayOfWeek.SUNDAY, chart.bars.first().date.dayOfWeek)
        assertEquals(7, chart.bars.size)
    }

    @Test
    fun weekCrossingTheClockChangeStillHasSevenDays() {
        // UK clocks go back on Sunday 25 October 2026, so that day is 25 hours long.
        val chart = PeriodCharts.week(report(Period.WEEK), LocalDate.of(2026, 10, 25), today, zone)

        assertEquals(7, chart.bars.size)
        assertEquals(LocalDate.of(2026, 10, 25), chart.bars.last().date)
    }

    @Test
    fun monthLinesUpUnderTheWeekStartAndMarksFutureDays() {
        // 1 October 2026 is a Thursday.
        val monday = PeriodCharts.month(report(Period.MONTH), today, today, DayOfWeek.MONDAY, 200, zone)
        val sunday = PeriodCharts.month(report(Period.MONTH, DayOfWeek.SUNDAY), today, today, DayOfWeek.SUNDAY, 200, zone)

        assertEquals(3, monday.leadingBlanks)
        assertEquals(4, sunday.leadingBlanks)
        assertEquals(31, monday.cells.size)
        assertTrue(monday.cells.filter { it.date > today }.all { it.reels == null && it.level == 0 })
        assertEquals(listOf(today), monday.cells.filter { it.isToday }.map { it.date })
    }

    @Test
    fun bestAndWorstUseFinishedTrackedDaysOnly() {
        events.run(LocalDate.of(2026, 10, 18).atTime(10, 0), 40)
        events.run(LocalDate.of(2026, 10, 19).atTime(10, 0), 2)
        // 20 October has none; today's 90 does not count because the day is not over.
        events.run(today.atTime(10, 0), 90)

        val chart = PeriodCharts.month(report(Period.MONTH), today, LocalDate.of(2026, 10, 18), DayOfWeek.MONDAY, 200, zone)

        assertEquals(DayCount(LocalDate.of(2026, 10, 20), 0), chart.best)
        assertEquals(DayCount(LocalDate.of(2026, 10, 18), 40), chart.worst)
        assertEquals(132, chart.total)
    }

    @Test
    fun bestAndWorstNeedTwoFinishedDays() {
        events.run(LocalDate.of(2026, 10, 20).atTime(10, 0), 12)

        val chart = PeriodCharts.month(report(Period.MONTH), today, LocalDate.of(2026, 10, 20), DayOfWeek.MONDAY, 200, zone)

        assertNull(chart.best)
        assertNull(chart.worst)
    }

    @Test
    fun noWorstDayWhenEveryDayWasZero() {
        val chart = PeriodCharts.month(report(Period.MONTH), today, LocalDate.of(2026, 10, 18), DayOfWeek.MONDAY, 200, zone)

        assertEquals(DayCount(LocalDate.of(2026, 10, 18), 0), chart.best)
        assertNull(chart.worst)
    }

    @Test
    fun levelsAreQuartersOfAHeavyDay() {
        assertEquals(0, PeriodCharts.level(0, 200))
        assertEquals(1, PeriodCharts.level(1, 200))
        assertEquals(1, PeriodCharts.level(50, 200))
        assertEquals(2, PeriodCharts.level(51, 200))
        assertEquals(3, PeriodCharts.level(150, 200))
        assertEquals(4, PeriodCharts.level(200, 200))
        assertEquals(4, PeriodCharts.level(900, 200))
    }

    @Test
    fun chartDescriptionsReadTheNumbersOut() {
        events.run(LocalDate.of(2026, 10, 19).atTime(10, 0), 5)
        events.run(today.atTime(10, 0), 3)

        val week = PeriodCharts.describe(PeriodCharts.week(report(Period.WEEK), today, LocalDate.of(2026, 10, 19), zone), Locale.UK)
        val month = PeriodCharts.describe(
            PeriodCharts.month(report(Period.MONTH), today, LocalDate.of(2026, 10, 19), DayOfWeek.MONDAY, 200, zone),
            Locale.UK,
        )

        assertEquals(
            "Reels per day this week. Monday 5, Tuesday 0, Wednesday 3 so far, today, Thursday not yet, " +
                "Friday not yet, Saturday not yet, Sunday not yet.",
            week,
        )
        assertEquals("Heat map of reels per day in October. Reels on 2 of 21 days so far. Busiest day so far: 19 October, 5 reels.", month)
    }
}
