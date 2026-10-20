package com.reelsloth.ai

import com.reelsloth.data.CommentaryDao
import com.reelsloth.data.CommentaryEntity
import com.reelsloth.data.ReelDao
import com.reelsloth.detect.Clock
import com.reelsloth.score.ScoreConfig
import com.reelsloth.score.ScoreEngine
import com.reelsloth.stats.Period
import com.reelsloth.stats.PeriodReport
import com.reelsloth.stats.StatsEngine
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** The facts for one completed period and the cache key they are stored under. */
data class PeriodFacts(val period: Period, val key: String, val facts: StatsFacts)

/** Commentary ready to show: from the cache, or a template written on the spot. */
data class Verdict(val period: Period, val text: String, val source: CommentarySource, val retries: Int)

/**
 * Commentary for the latest completed day, week and month ("yesterday", "last week", "last month").
 *
 * [verdict] is what the UI shows. It reads the cache and falls back to an instant template, so the
 * UI never waits for the model. [writeMissing] is the nightly job that fills the cache.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CommentaryRepository(
    private val stats: StatsEngine,
    private val reelDao: ReelDao,
    private val commentaryDao: CommentaryDao,
    private val scoreConfig: Flow<ScoreConfig>,
    private val tone: Flow<Tone>,
    private val engine: CommentaryEngine,
    private val templates: TemplateCommentaryEngine,
    private val clock: Clock,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    /** Facts for the latest completed [period], or null when tracking started after it ended. */
    fun completedFacts(period: Period): Flow<PeriodFacts?> = combine(
        stats.report(period, offset = -1),
        stats.report(period, offset = -2),
        reelDao.earliestTimestampFlow(),
        scoreConfig,
    ) { last, before, firstEventMs, config -> factsFor(period, last, before, firstEventMs, config) }
        .distinctUntilChanged()

    fun verdict(period: Period): Flow<Verdict?> =
        combine(completedFacts(period), tone) { facts, tone -> facts to tone }
            .flatMapLatest { (facts, tone) ->
                if (facts == null) {
                    flowOf(null)
                } else {
                    commentaryDao.observe(facts.key).map { cached ->
                        cached?.takeIf { it.tone == tone.name }?.toVerdict(period)
                            ?: Verdict(period, templates.render(CommentaryRequest(period, tone, facts.facts)), CommentarySource.TEMPLATE, 0)
                    }
                }
            }
            .distinctUntilChanged()

    /**
     * Writes commentary for each completed period that has none yet, was written in another tone,
     * or is a plain template while the model is now [modelReady]. Returns what was stored.
     * Because it always looks at the latest completed periods, a missed night is caught up.
     */
    suspend fun writeMissing(modelReady: Boolean): List<CommentaryEntity> {
        val tone = tone.first()
        return Period.entries.mapNotNull { period ->
            val facts = completedFacts(period).first() ?: return@mapNotNull null
            if (!needsWriting(commentaryDao.get(facts.key), tone, modelReady)) return@mapNotNull null
            val result = engine.write(CommentaryRequest(period, tone, facts.facts))
            CommentaryEntity(
                periodKey = facts.key,
                tone = tone.name,
                text = result.text,
                source = result.source.name,
                retries = result.retries,
                createdAtMs = clock.nowMs(),
            ).also { commentaryDao.upsert(it) }
        }
    }

    private fun factsFor(
        period: Period,
        last: PeriodReport,
        before: PeriodReport,
        firstEventMs: Long?,
        config: ScoreConfig,
    ): PeriodFacts? {
        if (firstEventMs == null || firstEventMs >= last.stats.endMs) return null
        val band = ScoreEngine(config).scoreDays(last.days).laziness.band
        val previous = before.stats.takeIf { firstEventMs < it.endMs }
        val start = Instant.ofEpochMilli(last.stats.startMs).atZone(zone()).toLocalDate()
        return PeriodFacts(period, key(period, start), StatsFacts.of(last.stats, previous, band))
    }

    private fun CommentaryEntity.toVerdict(period: Period) = Verdict(
        period = period,
        text = text,
        source = CommentarySource.entries.firstOrNull { it.name == source } ?: CommentarySource.TEMPLATE,
        retries = retries,
    )

    companion object {
        fun key(period: Period, start: LocalDate): String = "${period.name}:$start"

        fun needsWriting(existing: CommentaryEntity?, tone: Tone, modelReady: Boolean): Boolean =
            existing == null ||
                existing.tone != tone.name ||
                (modelReady && existing.source == CommentarySource.TEMPLATE.name && existing.retries == 0)
    }
}
