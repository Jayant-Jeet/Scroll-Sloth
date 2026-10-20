package com.halfbloodcoder.scrollsloth.stats

import com.halfbloodcoder.scrollsloth.data.ReelEventEntity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Builds reel events at local wall-clock times, the way a phone in [zone] would store them. */
class EventBuilder(val zone: ZoneId) {
    val events = mutableListOf<ReelEventEntity>()

    fun at(local: LocalDateTime, dwellMs: Long = 10_000, platform: String = "INSTAGRAM"): ReelEventEntity =
        ReelEventEntity(timestampMs = local.atZone(zone).toInstant().toEpochMilli(), platform = platform, dwellMs = dwellMs)
            .also { events += it }

    /** An event at an explicit instant, for wall-clock times that occur twice on DST fall-back days. */
    fun atOffset(local: LocalDateTime, offset: ZoneOffset, dwellMs: Long = 10_000): ReelEventEntity =
        ReelEventEntity(timestampMs = local.toInstant(offset).toEpochMilli(), platform = "INSTAGRAM", dwellMs = dwellMs)
            .also { events += it }

    /** [count] reels starting at [start], one every [spacingMs], each watched for [dwellMs]. */
    fun run(start: LocalDateTime, count: Int, spacingMs: Long = 60_000, dwellMs: Long = 10_000, platform: String = "INSTAGRAM") {
        val first = start.atZone(zone).toInstant().toEpochMilli()
        repeat(count) { i ->
            events += ReelEventEntity(timestampMs = first + i * spacingMs, platform = platform, dwellMs = dwellMs)
        }
    }

    /**
     * One day with [reels] reels, [late] of them between 00:00 and 05:00 and the rest from 06:00,
     * in sessions of at most [session] reels separated by 10-minute breaks, totalling [minutes].
     * The longest session is exactly [session] as long as either part has at least that many reels.
     */
    fun day(date: LocalDate, reels: Int, late: Int, session: Int, minutes: Int) {
        if (reels == 0) return
        val dwellMs = minutes * 60_000L / reels
        sessions(date.atTime(LocalTime.MIDNIGHT), late, session, dwellMs)
        sessions(date.atTime(6, 0), reels - late, session, dwellMs)
    }

    private fun sessions(start: LocalDateTime, count: Int, session: Int, dwellMs: Long) {
        var remaining = count
        var cursor = start
        while (remaining > 0) {
            val size = minOf(session, remaining)
            run(cursor, size, spacingMs = 60_000, dwellMs = dwellMs)
            cursor = cursor.plusMinutes(size + 10L)
            remaining -= size
        }
    }
}
