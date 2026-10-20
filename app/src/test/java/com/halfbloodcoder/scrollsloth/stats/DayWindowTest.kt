package com.halfbloodcoder.scrollsloth.stats

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class DayWindowTest {
    private val london = ZoneId.of("Europe/London")

    private fun ms(text: String) = ZonedDateTime.parse(text).toInstant().toEpochMilli()

    @Test
    fun `covers the local calendar day`() {
        val window = DayWindow.containing(ms("2026-10-20T15:30:00+01:00[Europe/London]"), london)

        assertEquals(ms("2026-10-20T00:00:00+01:00[Europe/London]"), window.startMs)
        assertEquals(ms("2026-10-21T00:00:00+01:00[Europe/London]"), window.endMs)
    }

    @Test
    fun `midnight belongs to the day it starts`() {
        val midnight = ms("2026-10-21T00:00:00+01:00[Europe/London]")

        assertEquals(midnight, DayWindow.containing(midnight, london).startMs)
    }

    @Test
    fun `day when clocks go back lasts 25 hours`() {
        val window = DayWindow.containing(ms("2026-10-25T12:00:00Z[Europe/London]"), london)

        assertEquals(25 * 3_600_000L, window.endMs - window.startMs)
    }

    @Test
    fun `day when clocks go forward lasts 23 hours`() {
        val window = DayWindow.containing(ms("2026-03-29T12:00:00+01:00[Europe/London]"), london)

        assertEquals(23 * 3_600_000L, window.endMs - window.startMs)
    }
}
