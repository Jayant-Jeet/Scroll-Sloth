package com.halfbloodcoder.scrollsloth.ai

import com.halfbloodcoder.scrollsloth.stats.Period

enum class Tone { GENTLE, ROAST }

enum class CommentarySource { GEMMA, TEMPLATE }

/** What to comment on: a completed [period] described only by [facts]. */
data class CommentaryRequest(val period: Period, val tone: Tone, val facts: StatsFacts)

/**
 * [retries] is how many follow-up attempts the model needed. A [CommentarySource.TEMPLATE] result
 * with retries > 0 means the model was tried and its text was rejected.
 */
data class CommentaryResult(val text: String, val source: CommentarySource, val retries: Int = 0)

/** Writes one or two sentences of commentary. Never computes or changes a number. */
interface CommentaryEngine {
    suspend fun write(request: CommentaryRequest): CommentaryResult
}
