package com.reelsloth.stats

import com.reelsloth.detect.Platform
import com.reelsloth.score.ScoreEngine
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatsCalculatorTest {
    private val london = ZoneId.of("Europe/London")

    @Test
    fun dayTotalsMinutesPlatformsAndActiveDays() {
        val b = EventBuilder(london)
        val date = LocalDate.of(2026, 10, 14)
        b.at(date.atTime(9, 0), dwellMs = 30_000, platform = "INSTAGRAM")
        b.at(date.atTime(9, 1), dwellMs = 90_000, platform = "YOUTUBE")
        b.at(date.atTime(20, 0), dwellMs = 60_000, platform = "FACEBOOK")
        b.at(date.atTime(20, 2), dwellMs = 60_000, platform = "FACEBOOK")
        b.at(date.minusDays(1).atTime(12, 0)) // other day
        b.at(date.plusDays(1).atTime(0, 0)) // next midnight is excluded

        val stats = dayReport(b, date).stats

        assertEquals(4, stats.reels)
        assertEquals(4.0, stats.minutes, 1e-9)
        assertEquals(mapOf(Platform.INSTAGRAM to 1, Platform.YOUTUBE to 1, Platform.FACEBOOK to 2), stats.byPlatform)
        assertEquals(1, stats.activeDays)
        assertEquals(Period.DAY, stats.period)
        assertEquals(date.atStartOfDay(london).toInstant().toEpochMilli(), stats.startMs)
        assertEquals(Duration.ofHours(24).toMillis(), stats.endMs - stats.startMs)
    }

    @Test
    fun lateNightIsElevenPmToBeforeFiveAm() {
        val date = LocalDate.of(2026, 10, 14)
        fun late(time: LocalDateTime) = StatsCalculator.isLateNight(time.atZone(london).toInstant().toEpochMilli(), london)

        assertEquals(false, late(date.atTime(22, 59, 59)))
        assertEquals(true, late(date.atTime(23, 0)))
        assertEquals(true, late(date.atTime(0, 0)))
        assertEquals(true, late(date.atTime(4, 59, 59)))
        assertEquals(false, late(date.atTime(5, 0)))
    }

    @Test
    fun sessionsBreakOnAGapOfFiveMinutesFromTheEndOfTheLastReel() {
        val b = EventBuilder(london)
        val start = LocalDateTime.of(2026, 10, 14, 10, 0)
        b.at(start, dwellMs = 60_000)
        // Ends 10:01:00; next starts 4:59.999 later -> same session.
        b.at(start.plusMinutes(6).minusNanos(1_000_000), dwellMs = 60_000)
        // Previous ends 10:06:59.999; exactly 5 minutes later -> new session.
        b.at(start.plusMinutes(12).minusNanos(1_000_000), dwellMs = 10_000)
        b.at(start.plusMinutes(13))

        assertEquals(2, StatsCalculator.longestSession(b.events.sortedBy { it.timestampMs }))
        assertEquals(2, dayReport(b, start.toLocalDate()).stats.longestSessionReels)
    }

    @Test
    fun sessionAcrossMidnightIsSplitBetweenDaysButWholeInTheWeek() {
        val b = EventBuilder(london)
        // Wednesday 23:50 to Thursday 00:10, one reel a minute: 10 before midnight, 11 after.
        b.run(LocalDateTime.of(2026, 10, 14, 23, 50), count = 21)
        val thursday = LocalDate.of(2026, 10, 15)

        val week = StatsCalculator.report(
            Period.WEEK, PeriodCalendar.range(Period.WEEK, thursday, DayOfWeek.MONDAY), b.events, london, b.events.first().timestampMs,
            today = thursday,
        )
        val (wednesday, thursdayStats) = week.days.takeLast(2)

        assertEquals(10, wednesday.reels)
        assertEquals(10, wednesday.longestSessionReels)
        assertEquals(10, wednesday.lateNightReels)
        assertEquals(11, thursdayStats.reels)
        assertEquals(11, thursdayStats.longestSessionReels)
        assertEquals(11, thursdayStats.lateNightReels)
        assertEquals(21, week.stats.longestSessionReels)
        assertEquals(2, week.stats.activeDays)
    }

    @Test
    fun springForwardDayIsTwentyThreeHoursAndKeepsItsLateNightReels() {
        val b = EventBuilder(london)
        val date = LocalDate.of(2026, 3, 29) // Clocks go 01:00 -> 02:00.
        b.at(date.atTime(0, 30))
        b.at(date.atTime(2, 30)) // first wall time after the jump
        b.at(date.atTime(12, 0))
        b.at(date.atTime(23, 30))
        b.at(date.plusDays(1).atTime(0, 0)) // next day

        val stats = dayReport(b, date).stats

        assertEquals(Duration.ofHours(23).toMillis(), stats.endMs - stats.startMs)
        assertEquals(4, stats.reels)
        assertEquals(3, stats.lateNightReels)
    }

    @Test
    fun fallBackDayIsTwentyFiveHoursAndCountsBothOneThirties() {
        val b = EventBuilder(london)
        val date = LocalDate.of(2026, 10, 25) // Clocks go 02:00 BST -> 01:00 GMT.
        val oneThirty = date.atTime(1, 30)
        b.atOffset(oneThirty, ZoneOffset.ofHours(1))
        b.atOffset(oneThirty, ZoneOffset.UTC)
        b.at(date.atTime(23, 59))

        val stats = dayReport(b, date).stats

        assertEquals(Duration.ofHours(25).toMillis(), stats.endMs - stats.startMs)
        assertEquals(3, stats.reels)
        assertEquals(3, stats.lateNightReels)
        // The two 1:30s are an hour apart: separate sessions.
        assertEquals(1, stats.longestSessionReels)
    }

    @Test
    fun weekAcrossDstHasSevenDaysOfTheRightLengths() {
        val sunday = LocalDate.of(2026, 3, 29)
        val report = StatsCalculator.report(
            Period.WEEK, PeriodCalendar.range(Period.WEEK, sunday, DayOfWeek.MONDAY), emptyList(), london, null, sunday,
        )
        assertEquals(7, report.days.size)
        assertEquals(LocalDate.of(2026, 3, 23).atStartOfDay(london).toInstant().toEpochMilli(), report.stats.startMs)
        assertEquals(Duration.ofHours(7 * 24 - 1).toMillis(), report.stats.endMs - report.stats.startMs)
        assertEquals(Duration.ofHours(23).toMillis(), report.days.last().let { it.endMs - it.startMs })
        report.days.zipWithNext().forEach { (a, b) -> assertEquals(a.endMs, b.startMs) }
    }

    @Test
    fun baselineIsNullWithoutSevenDaysOfHistory() {
        val today = LocalDate.of(2026, 10, 14)
        val b = EventBuilder(london)
        assertNull(dayReport(b, today).stats.baselineDailyMedian)

        // Six days of history: still null.
        (1..6).forEach { b.run(today.minusDays(it.toLong()).atTime(12, 0), count = 10) }
        assertNull(dayReport(b, today).stats.baselineDailyMedian)

        // Seventh day: a baseline.
        b.run(today.minusDays(7).atTime(12, 0), count = 10)
        assertEquals(10.0, dayReport(b, today).stats.baselineDailyMedian!!, 1e-9)
    }

    @Test
    fun baselineCountsQuietDaysSinceTheFirstEventAsZero() {
        val today = LocalDate.of(2026, 10, 14)
        val b = EventBuilder(london)
        // First event 8 days ago; reels on 3 of the 8 days: 0,0,0,0,0,30,40,50 -> median 0.
        b.run(today.minusDays(8).atTime(12, 0), count = 30)
        b.run(today.minusDays(5).atTime(12, 0), count = 40)
        b.run(today.minusDays(1).atTime(12, 0), count = 50)

        assertEquals(0.0, dayReport(b, today).stats.baselineDailyMedian!!, 1e-9)
    }

    @Test
    fun baselineUsesOnlyThePrevious28DaysAndAveragesTheMiddlePair() {
        val today = LocalDate.of(2026, 10, 14)
        val b = EventBuilder(london)
        b.run(today.minusDays(40).atTime(12, 0), count = 999) // outside the window, but marks history
        (1..28).forEach { daysAgo -> b.run(today.minusDays(daysAgo.toLong()).atTime(12, 0), count = if (daysAgo <= 14) 20 else 10) }
        b.run(today.atTime(9, 0), count = 500) // today itself is not part of its own baseline

        // 14 days of 10 and 14 of 20: middle pair is 10 and 20.
        assertEquals(15.0, dayReport(b, today, firstEventMs = b.events.minOf { it.timestampMs }).stats.baselineDailyMedian!!, 1e-9)
    }

    @Test
    fun medianOfOddAndEvenAndEmpty() {
        assertEquals(3.0, StatsCalculator.median(listOf(5, 1, 3))!!, 1e-9)
        assertEquals(2.5, StatsCalculator.median(listOf(4, 1, 3, 2))!!, 1e-9)
        assertNull(StatsCalculator.median(emptyList()))
    }

    @Test
    fun currentPeriodListsOnlyDaysSoFarAndPreviousPeriodListsAll() {
        val wednesday = LocalDate.of(2026, 10, 14)
        val week = PeriodCalendar.range(Period.WEEK, wednesday, DayOfWeek.MONDAY)
        val lastMonth = PeriodCalendar.range(Period.MONTH, wednesday, DayOfWeek.MONDAY, offset = -1)

        assertEquals(3, StatsCalculator.report(Period.WEEK, week, emptyList(), london, null, wednesday).days.size)
        assertEquals(30, StatsCalculator.report(Period.MONTH, lastMonth, emptyList(), london, null, wednesday).days.size)
    }

    @Test
    fun fakeWeekProducesTheExpectedStatsAndScores() {
        val zone = london
        val monday = LocalDate.of(2026, 10, 5)
        val b = EventBuilder(zone)
        (1..28).forEach { b.run(monday.minusDays(it.toLong()).atTime(12, 0), count = 100, dwellMs = 10_000) }
        data class Plan(val reels: Int, val late: Int, val session: Int, val minutes: Int)
        val plan = listOf(
            Plan(100, 0, 40, 30),
            Plan(0, 0, 0, 0),
            Plan(200, 100, 100, 120),
            Plan(150, 30, 75, 60),
            Plan(50, 0, 10, 15),
            Plan(300, 150, 120, 240),
            Plan(20, 20, 20, 6),
        )
        plan.forEachIndexed { i, p -> b.day(monday.plusDays(i.toLong()), p.reels, p.late, p.session, p.minutes) }
        val sunday = monday.plusDays(6)

        val report = StatsCalculator.report(
            Period.WEEK,
            PeriodCalendar.range(Period.WEEK, sunday, DayOfWeek.MONDAY),
            b.events,
            zone,
            b.events.minOf { it.timestampMs },
            today = sunday,
        )

        assertEquals(plan.map { it.reels }, report.days.map { it.reels })
        assertEquals(plan.map { it.late }, report.days.map { it.lateNightReels })
        assertEquals(plan.map { it.session }, report.days.map { it.longestSessionReels })
        plan.zip(report.days).forEach { (p, d) -> assertEquals(p.minutes.toDouble(), d.minutes, 1e-9) }
        report.days.forEach { assertEquals(100.0, it.baselineDailyMedian!!, 1e-9) }
        with(report.stats) {
            assertEquals(820, reels)
            assertEquals(300, lateNightReels)
            assertEquals(120, longestSessionReels)
            assertEquals(6, activeDays)
            assertEquals(471.0, minutes, 1e-9)
            assertEquals(100.0, baselineDailyMedian!!, 1e-9)
        }

        val scores = ScoreEngine().scoreDays(report.days)
        assertEquals(48, scores.laziness.score)
        assertEquals("Sofa sloth", scores.laziness.band.label)
        assertEquals(42, scores.unproductivity.score)
        assertEquals("Sofa sloth", scores.unproductivity.band.label)
    }

    private fun dayReport(b: EventBuilder, date: LocalDate, firstEventMs: Long? = b.events.minOfOrNull { it.timestampMs }) =
        StatsCalculator.report(
            Period.DAY, PeriodCalendar.range(Period.DAY, date, DayOfWeek.MONDAY), b.events, london, firstEventMs, date,
        )
}
