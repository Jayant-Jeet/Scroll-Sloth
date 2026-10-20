package com.halfbloodcoder.scrollsloth.ai

import com.halfbloodcoder.scrollsloth.stats.Period
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Builds the model prompt. The prompt contains the facts as one JSON block and the writing rules,
 * nothing else: no reel content, no names, no history beyond the two period totals.
 */
class PromptBuilder {
    fun build(request: CommentaryRequest): String = buildString {
        appendLine("You write a short comment for Scroll Sloth, an app that counts the short videos (reels) a person watched.")
        appendLine("Facts:")
        appendLine("```json")
        appendLine(factsJson(request))
        appendLine("```")
        appendLine("Rules:")
        appendLine("- Write at most two sentences, addressed to the person as \"you\".")
        appendLine("- Tone: ${toneRule(request.tone)}")
        appendLine("- Use only numbers that appear in the facts. Do not calculate or invent any other number.")
        appendLine("- Do not make medical or mental-health claims.")
        appendLine("- Do not insult the person or their worth. Tease the habit, not the person.")
        append("- Reply with the comment only.")
    }

    /** Follow-up sent in the same conversation when [NumberGuard] rejects a reply. */
    fun retry(rejection: NumberGuard.Verdict.Rejected): String = buildString {
        append("That comment broke the rules: ")
        append(rejection.problems.joinToString("; ") { it.describe() })
        append(". Write it again in at most two sentences, using only numbers from the facts.")
    }

    internal fun factsJson(request: CommentaryRequest): String {
        val facts = request.facts
        return json.encodeToString(
            FactsJson.serializer(),
            FactsJson(
                period = request.period.completedLabel,
                reels = facts.reels,
                minutes = facts.minutes,
                band = facts.band,
                lateNightReels = facts.lateNightReels,
                previousPeriodReels = facts.previousPeriodReels,
                delta = facts.delta,
            ),
        )
    }

    private fun toneRule(tone: Tone): String = when (tone) {
        Tone.GENTLE -> "gentle. Kind and encouraging, like a supportive friend."
        Tone.ROAST -> "roast. Playful, cheeky teasing about the scrolling habit, like a friend who is joking."
    }

    private fun NumberGuard.Problem.describe(): String = when (this) {
        NumberGuard.Problem.Empty -> "it was empty"
        is NumberGuard.Problem.TooLong -> "it was longer than ${NumberGuard.MAX_LENGTH} characters"
        is NumberGuard.Problem.InventedNumbers -> "it used numbers that are not in the facts: ${tokens.joinToString(", ")}"
        is NumberGuard.Problem.BannedPhrase -> "it used a phrase that is not allowed: \"$phrase\""
        is NumberGuard.Problem.ForeignScript -> "it contained characters that are not English: $characters"
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Serializable
    private data class FactsJson(
        val period: String,
        val reels: Int,
        val minutes: Int,
        val band: String,
        val lateNightReels: Int,
        @EncodeDefault(EncodeDefault.Mode.NEVER) val previousPeriodReels: Int? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER) val delta: Int? = null,
    )

    private companion object {
        val json = Json { encodeDefaults = true }
    }
}

/** How a completed period is referred to in commentary. */
internal val Period.completedLabel: String
    get() = when (this) {
        Period.DAY -> "yesterday"
        Period.WEEK -> "last week"
        Period.MONTH -> "last month"
    }
