package com.reelsloth.ai

import com.reelsloth.ai.NumberGuard.Problem
import com.reelsloth.ai.NumberGuard.Verdict
import com.reelsloth.stats.Period
import org.junit.Assert.assertEquals
import org.junit.Test

class PromptBuilderTest {
    private val prompts = PromptBuilder()
    private val facts = StatsFacts(
        reels = 120, minutes = 45, band = "Sofa sloth", bandIndex = 2, lateNightReels = 30, previousPeriodReels = 80,
    )

    @Test
    fun `daily roast prompt snapshot`() {
        val expected = """
            You write a short comment for Scroll Sloth, an app that counts the short videos (reels) a person watched.
            Facts:
            ```json
            {"period":"yesterday","reels":120,"minutes":45,"band":"Sofa sloth","lateNightReels":30,"previousPeriodReels":80,"delta":40}
            ```
            Rules:
            - Write at most two sentences, addressed to the person as "you".
            - Tone: roast. Playful, cheeky teasing about the scrolling habit, like a friend who is joking.
            - Use only numbers that appear in the facts. Do not calculate or invent any other number.
            - Do not make medical or mental-health claims.
            - Do not insult the person or their worth. Tease the habit, not the person.
            - Reply with the comment only.
        """.trimIndent()
        assertEquals(expected, prompts.build(CommentaryRequest(Period.DAY, Tone.ROAST, facts)))
    }

    @Test
    fun `gentle tone and missing previous period`() {
        val prompt = prompts.build(CommentaryRequest(Period.MONTH, Tone.GENTLE, facts.copy(previousPeriodReels = null)))
        val json = prompt.lines()[3]
        assertEquals("""{"period":"last month","reels":120,"minutes":45,"band":"Sofa sloth","lateNightReels":30}""", json)
        assertEquals("- Tone: gentle. Kind and encouraging, like a supportive friend.", prompt.lines()[7])
    }

    @Test
    fun `band labels are JSON escaped`() {
        val request = CommentaryRequest(Period.WEEK, Tone.GENTLE, facts.copy(band = "Sofa \"sloth\""))
        assertEquals(
            """{"period":"last week","reels":120,"minutes":45,"band":"Sofa \"sloth\"","lateNightReels":30,"previousPeriodReels":80,"delta":40}""",
            prompts.factsJson(request),
        )
    }

    @Test
    fun `retry prompt names every problem and rejected number`() {
        val rejection = Verdict.Rejected(listOf(Problem.InventedNumbers(listOf("121", "seven")), Problem.BannedPhrase("addict")))
        assertEquals(
            "That comment broke the rules: it used numbers that are not in the facts: 121, seven; " +
                "it used a phrase that is not allowed: \"addict\". " +
                "Write it again in at most two sentences, using only numbers from the facts.",
            prompts.retry(rejection),
        )
    }
}
