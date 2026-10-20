package com.halfbloodcoder.scrollsloth.ai

import com.halfbloodcoder.scrollsloth.score.ScoreBands
import com.halfbloodcoder.scrollsloth.stats.Period
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateCommentaryEngineTest {
    private val engine = TemplateCommentaryEngine()
    private val labels = ScoreBands.DEFAULT_LABELS

    private fun allFacts(): List<StatsFacts> = buildList {
        for (band in 0 until ScoreBands.COUNT) {
            for (reels in listOf(0, 1, 2, 17, 120, 1234)) {
                for (late in listOf(0, 1, reels / 3).distinct().filter { it <= reels }) {
                    for (previous in listOf(null, 0, 1, reels, reels + 9, 3000)) {
                        add(StatsFacts(reels, minutes = reels / 4, band = labels[band], bandIndex = band, lateNightReels = late, previousPeriodReels = previous))
                    }
                }
            }
        }
    }

    @Test
    fun `every band and tone has five to eight templates`() {
        for (tone in Tone.entries) {
            assertEquals(ScoreBands.COUNT, TemplateCommentaryEngine.TEMPLATES.getValue(tone).size)
            for (band in 0 until ScoreBands.COUNT) {
                assertTrue("$tone band $band", TemplateCommentaryEngine.templatesFor(band, tone).size in 5..8)
            }
        }
    }

    @Test
    fun `every rendered template passes the number guard`() {
        for (facts in allFacts()) for (tone in Tone.entries) for (period in Period.entries) {
            for (template in TemplateCommentaryEngine.templatesFor(facts.bandIndex, tone).filter { it.fits(facts) }) {
                val text = engine.fill(template.text, CommentaryRequest(period, tone, facts))
                assertEquals(text, NumberGuard.Verdict.Valid, NumberGuard.validate(text, facts))
                assertFalse(text, text.contains('{'))
            }
        }
    }

    @Test
    fun `output is deterministic and marked as template`() = runTest {
        val request = CommentaryRequest(Period.DAY, Tone.ROAST, StatsFacts(120, 45, labels[2], 2, 30, 80))
        val first = engine.write(request)
        assertEquals(CommentarySource.TEMPLATE, first.source)
        assertEquals(0, first.retries)
        assertEquals(first, engine.write(request))
    }

    @Test
    fun `templates needing missing facts are skipped`() {
        val plain = StatsFacts(10, 3, labels[0], 0, lateNightReels = 0, previousPeriodReels = null)
        for (tone in Tone.entries) for (band in 0 until ScoreBands.COUNT) {
            val fitting = TemplateCommentaryEngine.templatesFor(band, tone).filter { it.fits(plain) }
            assertTrue(fitting.none { it.needsPrevious || it.needsLate })
            assertTrue(fitting.isNotEmpty())
        }
    }

    @Test
    fun `different days usually get different lines`() {
        val texts = (0..30).map { reels ->
            engine.render(CommentaryRequest(Period.DAY, Tone.GENTLE, StatsFacts(reels, reels / 2, labels[0], 0, 0, null)))
        }.map { it.replace(Regex("\\d+"), "#") }.toSet()
        assertTrue(texts.size >= 4)
    }
}
