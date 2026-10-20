package com.reelsloth.stats

import com.reelsloth.data.ReelDao
import com.reelsloth.detect.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Live [PeriodReport]s read from [dao]. Each Flow re-emits when events are stored, when the local
 * day changes (so "today" rolls over at midnight), and when the week start setting changes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StatsEngine(
    private val dao: ReelDao,
    private val clock: Clock,
    private val weekStart: Flow<DayOfWeek>,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    fun today(): Flow<PeriodReport> = report(Period.DAY)

    fun thisWeek(): Flow<PeriodReport> = report(Period.WEEK)

    fun thisMonth(): Flow<PeriodReport> = report(Period.MONTH)

    /** Yesterday, last week, or last month, for comparison. */
    fun previous(period: Period): Flow<PeriodReport> = report(period, offset = -1)

    fun report(period: Period, offset: Int = 0): Flow<PeriodReport> =
        combine(localDays(), weekStart.distinctUntilChanged()) { day, start -> Triple(day.first, day.second, start) }
            .flatMapLatest { (today, zone, start) ->
                val range = PeriodCalendar.range(period, today, start, offset)
                combine(
                    dao.eventsBetweenFlow(StatsCalculator.historyStart(range).startMs(zone), range.endMs(zone)),
                    dao.earliestTimestampFlow(),
                ) { events, firstEventMs ->
                    StatsCalculator.report(period, range, events, zone, firstEventMs, today)
                }
            }
            .distinctUntilChanged()
            .flowOn(dispatcher)

    /** The local day of the first stored reel, or null before anything was counted. */
    fun trackingStart(): Flow<LocalDate?> =
        dao.earliestTimestampFlow()
            .map { ms -> ms?.let { Instant.ofEpochMilli(it).atZone(zone()).toLocalDate() } }
            .distinctUntilChanged()
            .flowOn(dispatcher)

    /** The current local date and zone, re-emitted just after each local midnight. */
    private fun localDays(): Flow<Pair<LocalDate, ZoneId>> = flow {
        while (true) {
            val zone = zone()
            val now = clock.nowMs()
            emit(Instant.ofEpochMilli(now).atZone(zone).toLocalDate() to zone)
            delay((DayWindow.containing(now, zone).endMs - now).coerceAtLeast(0) + MIDNIGHT_MARGIN_MS)
        }
    }.distinctUntilChanged()

    private companion object {
        const val MIDNIGHT_MARGIN_MS = 1L
    }
}
