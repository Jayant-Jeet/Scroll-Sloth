package com.reelsloth.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Writes reel events as CSV. The rows hold exactly what the database holds: when, which app,
 * and for how long. There is no reel content to export because none is ever stored.
 */
object CsvExporter {
    const val HEADER = "timestamp_ms,local_time,platform,dwell_ms"

    fun write(events: List<ReelEventEntity>, zone: ZoneId, out: Appendable) {
        out.append(HEADER).append('\n')
        events.forEach { event ->
            val local = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(Instant.ofEpochMilli(event.timestampMs).atZone(zone))
            out.append(event.timestampMs.toString()).append(',')
                .append(local).append(',')
                .append(cell(event.platform)).append(',')
                .append(event.dwellMs.toString()).append('\n')
        }
    }

    /** Quotes when needed and defuses spreadsheet formulas, in case a stored value is ever unexpected. */
    internal fun cell(value: String): String {
        val safe = if (value.firstOrNull() in FORMULA_STARTS) "'$value" else value
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + safe.replace("\"", "\"\"") + "\""
        } else {
            safe
        }
    }

    private val FORMULA_STARTS = setOf('=', '+', '-', '@', '\t', '\r')
}
