package com.halfbloodcoder.scrollsloth.debug

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventLogTest {
    private fun event(time: Long) = EventRecord(eventTimeMs = time, type = "TYPE_VIEW_SCROLLED")

    @Test
    fun keepsOnlyTheLatestThirtyInOrder() {
        val log = EventLog()
        (1L..45L).forEach { log.add(event(it)) }

        val events = log.snapshot()
        assertEquals(30, events.size)
        assertEquals((16L..45L).toList(), events.map { it.eventTimeMs })
    }

    @Test
    fun clearEmptiesTheLog() {
        val log = EventLog(capacity = 3)
        log.add(event(1))
        log.clear()
        assertTrue(log.snapshot().isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonPositiveCapacity() {
        EventLog(capacity = 0)
    }

    @Test
    fun createMapsUnknownIndicesToNull() {
        val record = EventRecord.create(1, "TYPE_WINDOW_CONTENT_CHANGED", "pkg", null, null, null, -1, -1, -1)
        assertNull(record.fromIndex)
        assertNull(record.toIndex)
        assertNull(record.itemCount)

        val known = EventRecord.create(1, "TYPE_VIEW_SCROLLED", "pkg", "cls", 0, 120, 0, 2, 5)
        assertEquals(0, known.fromIndex)
        assertEquals(2, known.toIndex)
        assertEquals(5, known.itemCount)
        assertEquals(120, known.scrollDeltaY)
    }
}
