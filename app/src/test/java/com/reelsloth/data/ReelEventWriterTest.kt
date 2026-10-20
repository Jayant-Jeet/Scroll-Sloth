package com.reelsloth.data

import com.reelsloth.detect.Platform
import com.reelsloth.detect.ReelViewed
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReelEventWriterTest {
    private class FakeReelDao(private val failFirst: Boolean = false) : ReelDao {
        val inserted = mutableListOf<ReelEventEntity>()
        private var calls = 0

        override suspend fun insert(event: ReelEventEntity): Long {
            calls++
            if (failFirst && calls == 1) error("disk full")
            inserted += event
            return inserted.size.toLong()
        }

        override suspend fun countBetween(startMs: Long, endMs: Long) = inserted.size
        override suspend fun countByPlatformBetween(startMs: Long, endMs: Long) = emptyList<PlatformEventCount>()
        override suspend fun eventsBetween(startMs: Long, endMs: Long) = inserted.toList()
        override suspend fun allEvents() = inserted.toList()
        override suspend fun deleteAll() = inserted.size.also { inserted.clear() }
        override fun countBetweenFlow(startMs: Long, endMs: Long): Flow<Int> = flowOf(inserted.size)
        override fun eventsBetweenFlow(startMs: Long, endMs: Long) = flowOf(inserted.toList())
        override fun earliestTimestampFlow() = flowOf(inserted.minOfOrNull { it.timestampMs })
    }

    @Test
    fun insertsEventsInOrderWithOnlyTimestampPlatformAndDwell() = runTest {
        val dao = FakeReelDao()
        val writer = ReelEventWriter(dao, backgroundScope)

        writer.submit(ReelViewed(Platform.INSTAGRAM, 1_000, 3_000))
        writer.submit(ReelViewed(Platform.YOUTUBE, 5_000, 2_500))
        runCurrent()

        assertEquals(
            listOf(
                ReelEventEntity(id = 0, timestampMs = 1_000, platform = "INSTAGRAM", dwellMs = 3_000),
                ReelEventEntity(id = 0, timestampMs = 5_000, platform = "YOUTUBE", dwellMs = 2_500),
            ),
            dao.inserted,
        )
    }

    @Test
    fun failedInsertDoesNotStopTheWriter() = runTest {
        val dao = FakeReelDao(failFirst = true)
        val writer = ReelEventWriter(dao, backgroundScope)

        writer.submit(ReelViewed(Platform.FACEBOOK, 1, 2_000))
        writer.submit(ReelViewed(Platform.FACEBOOK, 2, 2_000))
        runCurrent()

        assertEquals(listOf(2L), dao.inserted.map { it.timestampMs })
    }

    @Test
    fun closedWriterRejectsNewEvents() = runTest {
        val writer = ReelEventWriter(FakeReelDao(), backgroundScope)

        assertTrue(writer.submit(ReelViewed(Platform.FACEBOOK, 1, 2_000)))
        writer.close()
        assertFalse(writer.submit(ReelViewed(Platform.FACEBOOK, 2, 2_000)))
    }
}
