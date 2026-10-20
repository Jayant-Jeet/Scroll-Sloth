package com.halfbloodcoder.scrollsloth.data

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

/**
 * Cached commentary for one completed period. Holds only generated text about counts; never reel content.
 *
 * [periodKey] is e.g. "DAY:2026-10-19", "WEEK:2026-10-12" or "MONTH:2026-09-01" (period type and first day).
 * [source] and [tone] are the names of `CommentarySource` and `Tone`.
 */
@Entity(tableName = "commentary")
data class CommentaryEntity(
    @PrimaryKey @ColumnInfo(name = "period_key") val periodKey: String,
    @ColumnInfo(name = "tone") val tone: String,
    @ColumnInfo(name = "text") val text: String,
    @ColumnInfo(name = "source") val source: String,
    @ColumnInfo(name = "retries") val retries: Int,
    @ColumnInfo(name = "created_at_ms") val createdAtMs: Long,
)