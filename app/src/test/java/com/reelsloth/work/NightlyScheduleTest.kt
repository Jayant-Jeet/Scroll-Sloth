package com.reelsloth.work

import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class NightlyScheduleTest {
    private val london = ZoneId.of("Europe/London")

    private fun ms(local: LocalDateTime, zone: ZoneId = london) = local.atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `before half past midnight runs the same night`() {
        assertEquals(Duration.ofMinutes(30), NightlySchedule.delayUntilNext(ms(LocalDateTime.of(2026, 10, 14, 0, 0)), london))
    }

    @Test
    fun `during the day runs the next night`() {
        assertEquals(Duration.ofHours(10).plusMinutes(30), NightlySchedule.delayUntilNext(ms(LocalDateTime.of(2026, 10, 14, 14, 0)), london))
    }

    @Test
    fun `exactly at the time schedules the next day`() {
        assertEquals(Duration.ofHours(24), NightlySchedule.delayUntilNext(ms(LocalDateTime.of(2026, 10, 14, 0, 30)), london))
    }

    @Test
    fun `DST days are 23 and 25 hours long`() {
        // Clocks go forward at 01:00 on 29 March 2026 and back at 02:00 on 25 October 2026.
        assertEquals(Duration.ofHours(23), NightlySchedule.delayUntilNext(ms(LocalDateTime.of(2026, 3, 29, 0, 30)), london))
        assertEquals(Duration.ofHours(25), NightlySchedule.delayUntilNext(ms(LocalDateTime.of(2026, 10, 25, 0, 30)), london))
    }

    @Test
    fun `a skipped half past midnight moves to just after the gap`() {
        // Chile springs forward at midnight on 6 September 2026, so 00:30 that night does not exist.
        val santiago = ZoneId.of("America/Santiago")
        val delay = NightlySchedule.delayUntilNext(ms(LocalDateTime.of(2026, 9, 5, 23, 0), santiago), santiago)
        assertEquals(Duration.ofMinutes(90), delay)
    }
}