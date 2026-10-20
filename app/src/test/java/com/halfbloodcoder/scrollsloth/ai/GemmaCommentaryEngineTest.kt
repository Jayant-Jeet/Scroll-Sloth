package com.halfbloodcoder.scrollsloth.ai

import com.halfbloodcoder.scrollsloth.score.ScoreBands
import com.halfbloodcoder.scrollsloth.stats.Period
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GemmaCommentaryEngineTest {
    private val facts = StatsFacts(120, 45, "Sofa sloth", 2, lateNightReels = 30, previousPeriodReels = 80)
    private val request = CommentaryRequest(Period.DAY, Tone.ROAST, facts)
    private val templates = TemplateCommentaryEngine()

    /** Replies with [replies] in order; records every message and how often the model was loaded. */
    private class FakeModel(
        private val available: Boolean = true,
        private val replies: List<String> = emptyList(),
        private val failWith: Throwable? = null,
    ) : LanguageModel {
        val sent = mutableListOf<String>()
        var loads = 0

        override suspend fun isAvailable() = available

        override suspend fun <T> withChat(block: suspend (LanguageModelChat) -> T): T {
            loads++
            return block(LanguageModelChat { message ->
                failWith?.let { throw it }
                sent += message
                replies.getOrElse(sent.size - 1) { replies.last() }
            })
        }
    }

    private fun engine(model: LanguageModel) = GemmaCommentaryEngine(model, templates)

    @Test
    fun `valid first reply is used as Gemma commentary`() = runTest {
        val model = FakeModel(replies = listOf("  **\"120 reels in 45 minutes? The sofa salutes you.\"**\n"))
        val result = engine(model).write(request)
        assertEquals(CommentaryResult("120 reels in 45 minutes? The sofa salutes you.", CommentarySource.GEMMA, 0), result)
        assertEquals(listOf(PromptBuilder().build(request)), model.sent)
        assertEquals(1, model.loads)
    }

    @Test
    fun `rejected reply is retried once naming the numbers`() = runTest {
        val model = FakeModel(replies = listOf("You watched 121 reels, about 7 too many.", "You watched 120 reels. Bold."))
        val result = engine(model).write(request)
        assertEquals(CommentaryResult("You watched 120 reels. Bold.", CommentarySource.GEMMA, 1), result)
        assertEquals(2, model.sent.size)
        assertTrue(model.sent[1], model.sent[1].contains("121, 7"))
    }

    @Test
    fun `bad text twice falls back to the template`() = runTest {
        val model = FakeModel(replies = listOf("You watched 121 reels.", "Fine, 999 reels then."))
        val result = engine(model).write(request)
        assertEquals(CommentarySource.TEMPLATE, result.source)
        assertEquals(1, result.retries)
        assertEquals(templates.render(request), result.text)
        assertEquals(2, model.sent.size)
        assertTrue(model.sent[1].contains("121"))
    }

    @Test
    fun `missing model uses the template without loading anything`() = runTest {
        val model = FakeModel(available = false)
        assertEquals(CommentaryResult(templates.render(request), CommentarySource.TEMPLATE, 0), engine(model).write(request))
        assertEquals(0, model.loads)
    }

    @Test
    fun `model failure falls back to the template and is reported`() = runTest {
        val failure = IllegalStateException("GPU said no")
        val reported = mutableListOf<Exception>()
        val engine = GemmaCommentaryEngine(FakeModel(failWith = failure), templates, onModelError = { reported += it })
        assertEquals(CommentaryResult(templates.render(request), CommentarySource.TEMPLATE, 0), engine.write(request))
        assertEquals(listOf<Exception>(failure), reported)
    }

    @Test
    fun `garbled reply is retried`() = runTest {
        // Real output seen with FLOAT16 GPU activations on an S24 Ultra.
        val model = FakeModel(replies = listOf("It looks like you watchedヒル closely, 120 times!", "You watched 120 reels. Bold."))
        val result = engine(model).write(request)
        assertEquals(CommentaryResult("You watched 120 reels. Bold.", CommentarySource.GEMMA, 1), result)
        assertTrue(model.sent[1], model.sent[1].contains("not English: ヒル"))
    }

    @Test
    fun `cancellation is not swallowed`() = runTest {
        val model = FakeModel(failWith = CancellationException("stop"))
        try {
            engine(model).write(request)
            fail("Expected cancellation")
        } catch (_: CancellationException) {
        }
    }

    @Test
    fun `a broken engine always ends in a template with no invented numbers`() = runTest {
        val broken = listOf(
            "", "x".repeat(400), "You watched 9999 reels.", "Seventy-seven percent of a dozen.",
            "That is pure brain rot.", "Twenty reels, 3.5 hours, 12%!",
        )
        for (band in 0 until ScoreBands.COUNT) for (reels in listOf(0, 3, 45, 250)) for (tone in Tone.entries) {
            val f = StatsFacts(reels, reels / 3, ScoreBands.DEFAULT_LABELS[band], band, reels / 5, reels + 1)
            for (period in Period.entries) {
                val req = CommentaryRequest(period, tone, f)
                val result = engine(FakeModel(replies = broken.shuffled(Random(reels * 10 + band)))).write(req)
                assertEquals(CommentarySource.TEMPLATE, result.source)
                assertEquals(1, result.retries)
                assertEquals(result.text, NumberGuard.Verdict.Valid, NumberGuard.validate(result.text, f))
            }
        }
    }

    @Test
    fun `clean strips decoration but keeps the words`() {
        assertEquals("Hi there.", GemmaCommentaryEngine.clean("\"Hi   there.\""))
        assertEquals("It's fine.", GemmaCommentaryEngine.clean("It's fine."))
        assertEquals("Bold move.", GemmaCommentaryEngine.clean("**Bold** move."))
    }
}
