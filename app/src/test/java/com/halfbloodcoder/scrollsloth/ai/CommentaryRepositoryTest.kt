package com.halfbloodcoder.scrollsloth.ai

import app.cash.turbine.test
import com.halfbloodcoder.scrollsloth.data.CommentaryDao
import com.halfbloodcoder.scrollsloth.data.CommentaryEntity
import com.halfbloodcoder.scrollsloth.detect.Clock
import com.halfbloodcoder.scrollsloth.score.ScoreConfig
import com.halfbloodcoder.scrollsloth.stats.EventBuilder
import com.halfbloodcoder.scrollsloth.stats.InMemoryReelDao
import com.halfbloodcoder.scrollsloth.stats.Period
import com.halfbloodcoder.scrollsloth.stats.StatsEngine
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
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CommentaryRepositoryTest {
    private val zone = ZoneId.of("Europe/London")
    // Monday 1 June 2026, 00:30: yesterday, last week and last month have all just ended.
    private val nowMs = LocalDateTime.of(2026, 6, 1, 0, 30).atZone(zone).toInstant().toEpochMilli()
    private val reelDao = InMemoryReelDao()
    private val commentaryDao = MemoryCommentaryDao()
    private val tone = MutableStateFlow(Tone.GENTLE)
    private val engine = RecordingEngine()

    private class MemoryCommentaryDao : CommentaryDao {
        val rows = MutableStateFlow<Map<String, CommentaryEntity>>(emptyMap())
        override suspend fun upsert(commentary: CommentaryEntity) { rows.value = rows.value + (commentary.periodKey to commentary) }
        override suspend fun get(periodKey: String) = rows.value[periodKey]
        override fun observe(periodKey: String): Flow<CommentaryEntity?> = rows.map { it[periodKey] }
        override suspend fun deleteAll() { rows.value = emptyMap() }
    }

    private class RecordingEngine : CommentaryEngine {
        val requests = mutableListOf<CommentaryRequest>()
        var source = CommentarySource.GEMMA
        override suspend fun write(request: CommentaryRequest): CommentaryResult {
            requests += request
            return CommentaryResult("Text for ${request.period}", source, retries = 0)
        }
    }

    private fun TestScope.repository() = CommentaryRepository(
        stats = StatsEngine(
            dao = reelDao,
            clock = Clock { nowMs + testScheduler.currentTime },
            weekStart = MutableStateFlow(DayOfWeek.MONDAY),
            zone = { zone },
            dispatcher = StandardTestDispatcher(testScheduler),
        ),
        reelDao = reelDao,
        commentaryDao = commentaryDao,
        scoreConfig = MutableStateFlow(ScoreConfig()),
        tone = tone,
        engine = engine,
        templates = TemplateCommentaryEngine(),
        clock = Clock { nowMs },
        zone = { zone },
    )

    /** May 2026: 20 reels a day from the 18th, 30 on Saturday 30th, 50 on Sunday 31st (10 after midnight). */
    private fun seedMay() {
        val events = EventBuilder(zone)
        for (day in 18..29) events.day(LocalDate.of(2026, 5, day), reels = 20, late = 0, session = 20, minutes = 10)
        events.day(LocalDate.of(2026, 5, 30), reels = 30, late = 0, session = 30, minutes = 15)
        events.day(LocalDate.of(2026, 5, 31), reels = 50, late = 10, session = 40, minutes = 60)
        events.events.forEach(reelDao::add)
    }

    @Test
    fun `writes yesterday, last week and last month with keys and facts`() = runTest {
        seedMay()
        val written = repository().writeMissing(modelReady = true)

        assertEquals(listOf("DAY:2026-05-31", "WEEK:2026-05-25", "MONTH:2026-05-01"), written.map { it.periodKey })
        assertTrue(written.all { it.source == "GEMMA" && it.tone == "GENTLE" && it.createdAtMs == nowMs })
        val (day, week, month) = engine.requests.map { it.facts }
        assertEquals(listOf(50, 60, 10, 30), listOf(day.reels, day.minutes, day.lateNightReels, day.previousPeriodReels))
        assertEquals(ScoreConfig().bands.bands[day.bandIndex].label, day.band)
        assertEquals(20 * 5 + 30 + 50, week.reels)
        assertEquals(20 * 7, week.previousPeriodReels)
        assertEquals(20 * 12 + 30 + 50, month.reels)
        // Tracking started in May, so April is not a real "previous month".
        assertNull(month.previousPeriodReels)
        assertEquals(commentaryDao.rows.value.keys, written.map { it.periodKey }.toSet())
    }

    @Test
    fun `second run writes nothing and a tone change rewrites everything`() = runTest {
        seedMay()
        val repository = repository()
        repository.writeMissing(modelReady = true)
        assertTrue(repository.writeMissing(modelReady = true).isEmpty())

        tone.value = Tone.ROAST
        val rewritten = repository.writeMissing(modelReady = true)
        assertEquals(3, rewritten.size)
        assertTrue(rewritten.all { it.tone == "ROAST" })
        assertEquals(Tone.ROAST, engine.requests.last().tone)
    }

    @Test
    fun `templates are upgraded once the model is ready, failed model attempts are not retried`() = runTest {
        seedMay()
        val repository = repository()
        engine.source = CommentarySource.TEMPLATE
        repository.writeMissing(modelReady = false)
        assertTrue(repository.writeMissing(modelReady = false).isEmpty())

        val failed = commentaryDao.rows.value.getValue("DAY:2026-05-31").copy(retries = 1)
        commentaryDao.upsert(failed)
        engine.source = CommentarySource.GEMMA
        val upgraded = repository.writeMissing(modelReady = true)
        assertEquals(listOf("WEEK:2026-05-25", "MONTH:2026-05-01"), upgraded.map { it.periodKey })
    }

    @Test
    fun `periods that ended before tracking started are skipped`() = runTest {
        reelDao.add(EventBuilder(zone).at(LocalDateTime.of(2026, 6, 1, 0, 10)))
        assertTrue(repository().writeMissing(modelReady = true).isEmpty())
        assertTrue(engine.requests.isEmpty())
    }

    @Test
    fun `verdict shows a template until the cache has commentary in the current tone`() = runTest {
        seedMay()
        val repository = repository()
        val facts = repository.completedFacts(Period.DAY).first()!!.facts
        repository.verdict(Period.DAY).test {
            val template = awaitItem()!!
            assertEquals(CommentarySource.TEMPLATE, template.source)
            assertEquals(NumberGuard.Verdict.Valid, NumberGuard.validate(template.text, facts))

            commentaryDao.upsert(CommentaryEntity("DAY:2026-05-31", "GENTLE", "Fifty is a lot.", "GEMMA", 1, nowMs))
            assertEquals(Verdict(Period.DAY, "Fifty is a lot.", CommentarySource.GEMMA, 1), awaitItem())

            tone.value = Tone.ROAST
            val roast = awaitItem()!!
            assertEquals(CommentarySource.TEMPLATE, roast.source)
            assertFalse(roast.text == template.text)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `verdict is null before any tracking`() = runTest {
        repository().verdict(Period.WEEK).test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `stale entries need writing`() {
        val entry = CommentaryEntity("DAY:2026-05-31", "GENTLE", "Hi.", "TEMPLATE", 0, 0)
        assertTrue(CommentaryRepository.needsWriting(null, Tone.GENTLE, modelReady = false))
        assertFalse(CommentaryRepository.needsWriting(entry, Tone.GENTLE, modelReady = false))
        assertTrue(CommentaryRepository.needsWriting(entry, Tone.GENTLE, modelReady = true))
        assertTrue(CommentaryRepository.needsWriting(entry, Tone.ROAST, modelReady = false))
        assertFalse(CommentaryRepository.needsWriting(entry.copy(retries = 1), Tone.GENTLE, modelReady = true))
        assertFalse(CommentaryRepository.needsWriting(entry.copy(source = "GEMMA"), Tone.GENTLE, modelReady = true))
        assertEquals("WEEK:2026-05-25", CommentaryRepository.key(Period.WEEK, LocalDate.of(2026, 5, 25)))
    }
}
