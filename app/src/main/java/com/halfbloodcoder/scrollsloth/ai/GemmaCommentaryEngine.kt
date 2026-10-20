package com.halfbloodcoder.scrollsloth.ai

import kotlinx.coroutines.CancellationException

/**
 * Commentary written by the on-device Gemma model, checked by [NumberGuard].
 *
 * A rejected reply gets one follow-up in the same conversation that names what was wrong. If that
 * is rejected too, or the model is missing or fails, the [fallback] template is used instead.
 */
class GemmaCommentaryEngine(
    private val model: LanguageModel,
    private val fallback: CommentaryEngine,
    private val prompts: PromptBuilder = PromptBuilder(),
    /** Told about model failures before falling back. Never given the prompt or reply text. */
    private val onModelError: (Exception) -> Unit = {},
) : CommentaryEngine {
    override suspend fun write(request: CommentaryRequest): CommentaryResult {
        if (!model.isAvailable()) return fallback.write(request)
        var retries = 0
        val text = try {
            model.withChat { chat ->
                var reply = clean(chat.send(prompts.build(request)))
                var verdict = NumberGuard.validate(reply, request.facts)
                if (verdict is NumberGuard.Verdict.Rejected) {
                    retries = 1
                    reply = clean(chat.send(prompts.retry(verdict)))
                    verdict = NumberGuard.validate(reply, request.facts)
                }
                reply.takeIf { verdict == NumberGuard.Verdict.Valid }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onModelError(e)
            null
        }
        return if (text != null) {
            CommentaryResult(text, CommentarySource.GEMMA, retries)
        } else {
            fallback.write(request).copy(retries = retries)
        }
    }

    companion object {
        /** Strips markdown emphasis, wrapping quotes and extra whitespace that small models like to add. */
        fun clean(raw: String): String {
            var text = raw.replace("*", "").replace(Regex("\\s+"), " ").trim()
            val quotes = setOf('"', '\u201C', '\u201D', '\'')
            while (text.length >= 2 && text.first() in quotes && text.last() in quotes) {
                text = text.substring(1, text.length - 1).trim()
            }
            return text
        }
    }
}
