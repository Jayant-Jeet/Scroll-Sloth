package com.halfbloodcoder.scrollsloth.data

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReelDao {
    @Insert
    suspend fun insert(event: ReelEventEntity): Long

    @Query("SELECT COUNT(*) FROM reel_events WHERE timestamp_ms >= :startMs AND timestamp_ms < :endMs")
    suspend fun countBetween(startMs: Long, endMs: Long): Int

    @Query(
        """
        SELECT platform, COUNT(*) AS event_count
        FROM reel_events
        WHERE timestamp_ms >= :startMs AND timestamp_ms < :endMs
        GROUP BY platform
        """,
    )
    suspend fun countByPlatformBetween(startMs: Long, endMs: Long): List<PlatformEventCount>

    @Query("SELECT * FROM reel_events WHERE timestamp_ms >= :startMs AND timestamp_ms < :endMs ORDER BY timestamp_ms")
    suspend fun eventsBetween(startMs: Long, endMs: Long): List<ReelEventEntity>

    @Query("SELECT * FROM reel_events ORDER BY timestamp_ms")
    suspend fun allEvents(): List<ReelEventEntity>

    @Query("DELETE FROM reel_events")
    suspend fun deleteAll(): Int

    @Query("SELECT COUNT(*) FROM reel_events WHERE timestamp_ms >= :startMs AND timestamp_ms < :endMs")
    fun countBetweenFlow(startMs: Long, endMs: Long): Flow<Int>

    @Query("SELECT * FROM reel_events WHERE timestamp_ms >= :startMs AND timestamp_ms < :endMs ORDER BY timestamp_ms")
    fun eventsBetweenFlow(startMs: Long, endMs: Long): Flow<List<ReelEventEntity>>

    /** Timestamp of the oldest stored event, or null when there are none. */
    @Query("SELECT MIN(timestamp_ms) FROM reel_events")
    fun earliestTimestampFlow(): Flow<Long?>
}
