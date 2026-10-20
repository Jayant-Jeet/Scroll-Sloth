package com.reelsloth.stats

import java.time.Instant
import java.time.ZoneId

/** Half-open [startMs, endMs) range of one local calendar day. */
data class DayWindow(val startMs: Long, val endMs: Long) {
    companion object {
        /** The local day containing [instantMs]. Handles 23 h and 25 h days around DST changes. */
        fun containing(instantMs: Long, zone: ZoneId): DayWindow {
            val date = Instant.ofEpochMilli(instantMs).atZone(zone).toLocalDate()
            return DayWindow(
                startMs = date.atStartOfDay(zone).toInstant().toEpochMilli(),
                endMs = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
            )
        }
    }
}
