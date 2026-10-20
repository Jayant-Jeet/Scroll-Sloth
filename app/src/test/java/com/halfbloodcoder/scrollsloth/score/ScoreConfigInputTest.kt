package com.halfbloodcoder.scrollsloth.score

import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreConfigInputTest {
    private val defaults = ScoreConfigInput.fieldsOf(ScoreConfig())

    @Test
    fun defaultsRoundTrip() {
        assertEquals("50", defaults.lateSharePercent)
        assertEquals(ScoreConfigInput.Result.Valid(ScoreConfig()), ScoreConfigInput.parse(defaults))
    }

    @Test
    fun parsesTrimmedValuesAndPercent() {
        val result = ScoreConfigInput.parse(
            defaults.copy(heavyDaily = " 150 ", lateSharePercent = "37.5", bandLabels = listOf(" A", "B", "C", "D", "E ")),
        )
        assertEquals(
            ScoreConfigInput.Result.Valid(
                ScoreConfig(heavyDaily = 150, lateShareHeavy = 0.375, bandLabels = listOf("A", "B", "C", "D", "E")),
            ),
            result,
        )
    }

    @Test
    fun rejectsBadInputWithAReadableMessage() {
        fun message(fields: ScoreConfigInput.Fields) = (ScoreConfigInput.parse(fields) as ScoreConfigInput.Result.Invalid).message

        assertEquals("Heavy day needs a whole number of reels.", message(defaults.copy(heavyDaily = "lots")))
        assertEquals("Heavy day must be between 1 and 10000 reels.", message(defaults.copy(heavyDaily = "0")))
        assertEquals("Minutes budget needs a whole number of minutes.", message(defaults.copy(dailyMinutesBudget = "2.5")))
        assertEquals("Heavy session needs a whole number of reels.", message(defaults.copy(sessionHeavy = "")))
        assertEquals("Late-night share must be above 0% and at most 100%.", message(defaults.copy(lateSharePercent = "0")))
        assertEquals("Late-night share needs a percentage.", message(defaults.copy(lateSharePercent = "half")))
        assertEquals(
            "Band labels cannot be blank.",
            message(defaults.copy(bandLabels = listOf("A", " ", "C", "D", "E"))),
        )
    }
}
