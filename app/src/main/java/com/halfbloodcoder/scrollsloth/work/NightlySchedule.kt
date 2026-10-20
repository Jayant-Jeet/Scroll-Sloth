package com.halfbloodcoder.scrollsloth.work

import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** When the nightly commentary job should next run: the next [at] local time, 00:30 by default. */
object NightlySchedule {
    val DEFAULT_TIME: LocalTime = LocalTime.of(0, 30)

    /**
     * Time from [nowMs] until the next [at] in [zone], strictly in the future. If [at] does not exist
     * on a day because of a DST jump, the first valid moment after the gap is used.
     */
    fun delayUntilNext(nowMs: Long, zone: ZoneId, at: LocalTime = DEFAULT_TIME): Duration {
        val now = Instant.ofEpochMilli(nowMs).atZone(zone)
        var next = ZonedDateTime.of(now.toLocalDate(), at, zone)
        if (!next.isAfter(now)) next = ZonedDateTime.of(now.toLocalDate().plusDays(1), at, zone)
        return Duration.between(now, next)
    }
}