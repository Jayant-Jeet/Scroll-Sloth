package com.halfbloodcoder.scrollsloth.data

import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import app.cash.turbine.test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReelDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: ReelDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.reelDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun queriesUseStartInclusiveEndExclusiveTimeRange() = runBlocking {
        dao.insert(ReelEventEntity(timestampMs = 10, platform = "INSTAGRAM", dwellMs = 100))
        dao.insert(ReelEventEntity(timestampMs = 19, platform = "YOUTUBE", dwellMs = 200))
        dao.insert(ReelEventEntity(timestampMs = 20, platform = "FACEBOOK", dwellMs = 300))

        assertEquals(2, dao.countBetween(startMs = 10, endMs = 20))
        assertEquals(2, dao.eventsBetween(startMs = 10, endMs = 20).size)
        assertEquals(
            setOf("INSTAGRAM" to 1, "YOUTUBE" to 1),
            dao.countByPlatformBetween(startMs = 10, endMs = 20)
                .map { it.platform to it.eventCount }
                .toSet(),
        )
        assertEquals(2, dao.countBetweenFlow(startMs = 10, endMs = 20).first())
        assertEquals(3, dao.deleteAll())
        assertEquals(0, dao.countBetween(startMs = 10, endMs = 20))
    }

    @Test
    fun countFlowEmitsWhenEventsAreInserted() = runBlocking {
        dao.countBetweenFlow(startMs = 10, endMs = 20).test {
            assertEquals(0, awaitItem())
            dao.insert(ReelEventEntity(timestampMs = 15, platform = "INSTAGRAM", dwellMs = 100))
            assertEquals(1, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun eventsFlowIsOrderedAndHalfOpenAndEarliestTracksTheOldestEvent() = runBlocking {
        assertEquals(null, dao.earliestTimestampFlow().first())
        dao.insert(ReelEventEntity(timestampMs = 18, platform = "YOUTUBE", dwellMs = 200))
        dao.insert(ReelEventEntity(timestampMs = 12, platform = "INSTAGRAM", dwellMs = 100))
        dao.insert(ReelEventEntity(timestampMs = 20, platform = "FACEBOOK", dwellMs = 300))
        dao.insert(ReelEventEntity(timestampMs = 5, platform = "FACEBOOK", dwellMs = 300))

        assertEquals(listOf(12L, 18L), dao.eventsBetweenFlow(startMs = 10, endMs = 20).first().map { it.timestampMs })
        assertEquals(5L, dao.earliestTimestampFlow().first())
    }
}
