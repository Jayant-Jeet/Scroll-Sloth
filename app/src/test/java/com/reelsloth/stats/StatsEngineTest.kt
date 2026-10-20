package com.reelsloth.stats

import app.cash.turbine.test
import com.reelsloth.data.PlatformEventCount
import com.reelsloth.data.ReelDao
import com.reelsloth.data.ReelEventEntity
import com.reelsloth.detect.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StatsEngineTest {
    private val zone = ZoneId.of("Europe/London")
    private val dao = InMemoryReelDao()
    private val weekStart = MutableStateFlow(DayOfWeek.MONDAY)

    // Wednesday 14 October 2026, 23:00 local.
    private val startLocal = LocalDateTime.of(2026, 10, 14, 23, 0)
    private val startMs = startLocal.atZone(zone).toInstant().toEpochMilli()

    private fun TestScope.engine() = StatsEngine(
        dao = dao,
        clock = Clock { startMs + testScheduler.currentTime },
        weekStart = weekStart,
        zone = { zone },
        dispatcher = StandardTestDispatcher(testScheduler),
    )

    private fun insert(local: LocalDateTime) =
        dao.add(ReelEventEntity(timestampMs = local.atZone(zone).toInstant().toEpochMilli(), platform = "YOUTUBE", dwellMs = 5_000))

    @Test
    fun todayUpdatesWhenReelsAreStoredAndRollsOverAtMidnight() = runTest {
        insert(startLocal.minusHours(2))
        engine().today().test {
            val first = awaitItem()
            assertEquals(LocalDate.of(2026, 10, 14).atStartOfDay(zone).toInstant().toEpochMilli(), first.stats.startMs)
            assertEquals(1, first.stats.reels)

            insert(startLocal.plusMinutes(1))
            assertEquals(2, awaitItem().stats.reels)

            advanceTimeBy(60 * 60_000L + 1_000)
            val thursday = awaitItem()
            assertEquals(LocalDate.of(2026, 10, 15).atStartOfDay(zone).toInstant().toEpochMilli(), thursday.stats.startMs)
            assertEquals(0, thursday.stats.reels)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun trackingStartIsTheLocalDayOfTheFirstReel() = runTest {
        engine().trackingStart().test {
            assertEquals(null, awaitItem())
            // 00:30 local on the 3rd is still the 2nd in UTC: the local zone must win.
            insert(LocalDateTime.of(2026, 10, 3, 0, 30))
            assertEquals(LocalDate.of(2026, 10, 3), awaitItem())
            insert(LocalDateTime.of(2026, 10, 5, 12, 0))
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun weekFollowsTheWeekStartSetting() = runTest {
        engine().thisWeek().test {
            assertEquals(LocalDate.of(2026, 10, 12), awaitItem().startDate())
            weekStart.value = DayOfWeek.SUNDAY
            val sundayWeek = awaitItem()
            assertEquals(LocalDate.of(2026, 10, 11), sundayWeek.startDate())
            assertEquals(4, sundayWeek.days.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun previousPeriodsAndMonth() = runTest {
        insert(LocalDateTime.of(2026, 10, 13, 12, 0))
        insert(LocalDateTime.of(2026, 9, 30, 12, 0))
        val engine = engine()

        val yesterday = engine.previous(Period.DAY).first()
        assertEquals(LocalDate.of(2026, 10, 13), yesterday.startDate())
        assertEquals(1, yesterday.stats.reels)

        val lastWeek = engine.previous(Period.WEEK).first()
        assertEquals(LocalDate.of(2026, 10, 5), lastWeek.startDate())
        assertEquals(7, lastWeek.days.size)

        val lastMonth = engine.previous(Period.MONTH).first()
        assertEquals(LocalDate.of(2026, 9, 1), lastMonth.startDate())
        assertEquals(1, lastMonth.stats.reels)
        assertEquals(30, lastMonth.days.size)

        val month = engine.thisMonth().first()
        assertEquals(1, month.stats.reels)
        assertEquals(14, month.days.size)
    }

    private fun PeriodReport.startDate(): LocalDate =
        java.time.Instant.ofEpochMilli(stats.startMs).atZone(zone).toLocalDate()
}

/** A [ReelDao] backed by a list, with live Flows like Room's. */
class InMemoryReelDao : ReelDao {
    private val events = MutableStateFlow<List<ReelEventEntity>>(emptyList())

    fun add(event: ReelEventEntity) {
        events.value = events.value + event.copy(id = events.value.size + 1L)
    }

    override suspend fun insert(event: ReelEventEntity): Long {
        add(event)
        return events.value.size.toLong()
    }

    override suspend fun countBetween(startMs: Long, endMs: Long) = events.value.count { it.timestampMs in startMs until endMs }

    override suspend fun countByPlatformBetween(startMs: Long, endMs: Long) =
        events.value.filter { it.timestampMs in startMs until endMs }
            .groupingBy { it.platform }.eachCount()
            .map { (platform, count) -> PlatformEventCount(platform, count) }

    override suspend fun eventsBetween(startMs: Long, endMs: Long) =
        events.value.filter { it.timestampMs in startMs until endMs }.sortedBy { it.timestampMs }

    override suspend fun allEvents() = events.value.sortedBy { it.timestampMs }

    override suspend fun deleteAll() = events.value.size.also { events.value = emptyList() }

    override fun countBetweenFlow(startMs: Long, endMs: Long): Flow<Int> =
        events.map { list -> list.count { it.timestampMs in startMs until endMs } }

    override fun eventsBetweenFlow(startMs: Long, endMs: Long): Flow<List<ReelEventEntity>> =
        events.map { list -> list.filter { it.timestampMs in startMs until endMs }.sortedBy { it.timestampMs } }

    override fun earliestTimestampFlow(): Flow<Long?> = events.map { list -> list.minOfOrNull { it.timestampMs } }
}
