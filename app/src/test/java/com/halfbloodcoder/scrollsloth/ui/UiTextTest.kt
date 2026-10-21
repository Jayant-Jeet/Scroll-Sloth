package com.halfbloodcoder.scrollsloth.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class UiTextTest {
    @Test
    fun verdictSplitsAfterTheFirstSentence() {
        assertEquals(
            "120 reels today." to "That is a lot of sofa time. Maybe stretch?",
            splitVerdict("120 reels today. That is a lot of sofa time. Maybe stretch?"),
        )
    }

    @Test
    fun verdictKeepsClosingQuotesWithTheHeadline() {
        assertEquals("You said \"one more.\"" to "It was not one more.", splitVerdict("You said \"one more.\" It was not one more."))
    }

    @Test
    fun singleSentenceVerdictHasNoBody() {
        assertEquals("Only 3 reels." to "", splitVerdict("  Only 3 reels.  "))
        assertEquals("No full stop at all" to "", splitVerdict("No full stop at all"))
    }

    @Test
    fun decimalsDoNotSplitTheVerdict() {
        assertEquals("You watched 1.5 hours today." to "", splitVerdict("You watched 1.5 hours today."))
    }

    @Test
    fun modelNameSplitsTheVariantInBrackets() {
        assertEquals("Gemma 3 1B" to "Instruct, int4", splitModelName("Gemma 3 1B (instruct, int4)"))
        assertEquals("Gemma 3 1B" to null, splitModelName("Gemma 3 1B"))
        assertEquals("(odd)" to null, splitModelName("(odd)"))
    }

    @Test
    fun minutesFormatAsHoursAndMinutes() {
        assertEquals("0 min", formatMinutes(0.2))
        assertEquals("59 min", formatMinutes(59.0))
        assertEquals("1 h", formatMinutes(60.0))
        assertEquals("1 h 15 min", formatMinutes(75.0))
    }
}
