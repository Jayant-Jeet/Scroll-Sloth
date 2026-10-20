package com.reelsloth.data

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "reel_events")
data class ReelEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "timestamp_ms") val timestampMs: Long,
    val platform: String,
    @ColumnInfo(name = "dwell_ms") val dwellMs: Long,
)

data class PlatformEventCount(
    val platform: String,
    @ColumnInfo(name = "event_count") val eventCount: Int,
)
