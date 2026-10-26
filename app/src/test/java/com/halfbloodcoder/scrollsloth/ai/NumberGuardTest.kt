package com.halfbloodcoder.scrollsloth.ai

import com.halfbloodcoder.scrollsloth.ai.NumberGuard.Problem
import com.halfbloodcoder.scrollsloth.ai.NumberGuard.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NumberGuardTest {
    // Allowed: 120, 45, 30, 80, |delta| 40, hours 0.8 and 1; percents 50 (change) and 25 (late share).
    private val facts = StatsFacts(
        reels = 120, minutes = 45, band = "Sofa sloth", bandIndex = 2, lateNightReels = 30, previousPeriodReels = 80,
    )
    private val rising = StatsFacts(45, 12, "Sofa sloth", 2, lateNightReels = 9, previousPeriodReels = 37)

    private fun assertRejected(text: String, facts: StatsFacts = rising) {
        assertTrue(text, NumberGuard.validate(text, facts) is Verdict.Rejected)
    }

    @Test
    fun `rising counts reject downward comparisons and accept upward comparisons`() {
        listOf("down 20% from yesterday", "fewer reels than yesterday").forEach { assertRejected(it) }
        listOf(
            "45 reels in 12 minutes, up from yesterday",
            "more reels than yesterday",
            "an overall busy day",
            "A download for the upper floor.",
        ).forEach { assertEquals(it, Verdict.Valid, NumberGuard.validate(it, rising)) }
    }

    @Test
    fun `every comparison word is checked case insensitively in both directions`() {
        val up = listOf("more", "higher", "up", "increase", "increased", "rose", "grew", "climbed", "jumped", "above", "worse")
        val down = listOf("fewer", "less", "lower", "down", "drop", "dropped", "decreased", "fell", "dipped", "below", "better")
        val falling = rising.copy(previousPeriodReels = 60)
        up.forEach {
            assertEquals(it, Verdict.Valid, NumberGuard.validate(it.uppercase(), rising))
            assertRejected(it.uppercase(), falling)
        }
        down.forEach {
            assertEquals(it, Verdict.Valid, NumberGuard.validate(it.uppercase(), falling))
            assertRejected(it.uppercase())
        }
        assertRejected("up 20%", falling)
        assertEquals(Verdict.Valid, NumberGuard.validate("down from yesterday", falling))
        assertRejected("up overall, but down yesterday")
    }

    @Test
    fun `same and missing previous periods reject all comparison classes`() {
        for (previous in listOf(null, rising.reels)) {
            val facts = rising.copy(previousPeriodReels = previous)
            listOf("more than yesterday", "lower than yesterday", "worse", "better").forEach { assertRejected(it, facts) }
            assertEquals(Verdict.Valid, NumberGuard.validate("an overall busy day", facts))
        }
        assertEquals(Verdict.Valid, NumberGuard.validate("up from yesterday", rising.copy(previousPeriodReels = 0)))
    }

    @Test
    fun `multipliers require an explicit ratio fact not a coincidentally allowed number`() {
        listOf("twice as many reels", "double the reels", "triple the reels", "half as many reels", "12 times", "twelve times")
            .forEach { assertRejected(it) }
        assertRejected("twice as many reels", rising.copy(reels = 74))
        assertEquals(Verdict.Valid, NumberGuard.validate("A halftime doublecheck.", rising))
    }

    @Test
    fun `numbers bind to reel time and percentage units within two words`() {
        listOf("12 reels in 45 minutes", "twelve reels", "12 watched videos", "45 total min", "45 hr", "45 %", "12 percent")
            .forEach { assertRejected(it) }
        listOf(
            "45 reels in 12 minutes", "45 watched videos", "37 shorts", "9 late reels", "8 reels",
            "12 min", "0.2 hours", "0 hr", "20 %", "20 percent", "twenty per cent",
            "12, with 45.", "45. Minutes of reflection.", "45; minutes later.",
        ).forEach { assertEquals(it, Verdict.Valid, NumberGuard.validate(it, rising)) }
    }

    @Test
    fun `all recognized unit spellings use the right allow list`() {
        for (unit in listOf("reel", "reels", "video", "videos", "short", "shorts")) {
            assertEquals(Verdict.Valid, NumberGuard.validate("45 $unit", rising))
            assertRejected("12 total ${unit.uppercase()}")
        }
        for (unit in listOf("min", "minute", "minutes", "hour", "hours", "hr")) {
            assertEquals(Verdict.Valid, NumberGuard.validate("12 $unit", rising))
            assertRejected("45 total ${unit.uppercase()}")
        }
        assertRejected("12 total percent")
        assertEquals(Verdict.Valid, NumberGuard.validate("20 total percent", rising))
        assertRejected("twelve total percent")
    }

    @Test
    fun `rejection identifies contradictions and wrong units even when every number is globally allowed`() {
        val direction = NumberGuard.validate("down 20%", rising) as Verdict.Rejected
        assertEquals(listOf(Problem.ContradictoryDirection(listOf("down"))), direction.problems)
        val swapped = NumberGuard.validate("12 reels in 45 minutes", rising) as Verdict.Rejected
        assertEquals(listOf(Problem.WrongUnit(listOf("12", "45"))), swapped.problems)
    }

    private fun invented(text: String, facts: StatsFacts = this.facts): List<String> =
        (NumberGuard.validate(text, facts) as Verdict.Rejected).problems
            .filterIsInstance<Problem.InventedNumbers>().single().tokens

    @Test
    fun `text using only fact numbers is valid`() {
        assertEquals(Verdict.Valid, NumberGuard.validate("Yesterday you watched 120 reels in 45 minutes, 30 of them late.", facts))
        assertEquals(Verdict.Valid, NumberGuard.validate("A calm day with no numbers at all.", facts))
    }

    @Test
    fun `invented numbers are rejected and named`() {
        assertEquals(listOf("121"), invented("You watched 121 reels in 45 minutes."))
        assertEquals(listOf("7", "2.5"), invented("That is 7 days of scrolling, or 2.5 films."))
    }

    @Test
    fun `letters from other scripts are rejected but accents, emoji and a renamed band pass`() {
        val rejected = NumberGuard.validate("It looks like you watchedヒル closely, 120 times!", facts) as Verdict.Rejected
        assertTrue(rejected.problems.contains(Problem.ForeignScript("ヒル")))
        assertEquals("сервер", NumberGuard.foreignScript("Wow сервер", "Sofa sloth"))
        assertEquals(Verdict.Valid, NumberGuard.validate("A naïve café crawl — 120 reels 🦥, “cheeky”.", facts))
        assertNull(NumberGuard.foreignScript("Вы ленивец", "Вы ленивец"))
    }

    @Test
    fun `simple derived values are allowed`() {
        assertEquals(Verdict.Valid, NumberGuard.validate("That is 40 more than the 80 before, up 50%.", facts))
        assertEquals(Verdict.Valid, NumberGuard.validate("About 0.8 hours, call it 1 hour.", facts))
        assertEquals(Verdict.Valid, NumberGuard.validate("25 percent of them came late at night.", facts))
    }

    @Test
    fun `percentages must match a percentage fact`() {
        assertEquals(listOf("45%"), invented("You spent 45% of your evening scrolling."))
        assertEquals(listOf("120 per cent"), invented("You gave it 120 per cent."))
    }

    @Test
    fun `percentage change is rounded half up`() {
        val small = facts.copy(reels = 4, minutes = 2, lateNightReels = 0, previousPeriodReels = 3)
        assertEquals(Verdict.Valid, NumberGuard.validate("Up 33% on the 3 reels before.", small))
        assertEquals(listOf("33.3%"), invented("Up 33.3% on before.", small))
        // (1 - 8) / 8 = -87.5%, rounded half up to 88.
        val drop = facts.copy(reels = 1, minutes = 0, lateNightReels = 0, previousPeriodReels = 8)
        assertEquals(Verdict.Valid, NumberGuard.validate("Down 88% from 8 reels.", drop))
    }

    @Test
    fun `number words are checked like digits`() {
        assertEquals(Verdict.Valid, NumberGuard.validate("Thirty of them came after dark.", facts))
        assertEquals(listOf("Seven"), invented("Seven more and you would be a legend."))
        assertEquals(listOf("two"), invented("Just two more, right?"))
        // 45 minutes rounds to 1 hour, so "one" is a derived value here.
        assertEquals(Verdict.Valid, NumberGuard.validate("Nearly one hour.", facts))
        assertEquals(listOf("Dozens", "hundreds"), invented("Dozens became hundreds."))
        assertEquals(Verdict.Valid, NumberGuard.validate("Up fifty percent on before.", facts))
        assertEquals(listOf("sixty percent"), invented("Late share was sixty percent."))
    }

    @Test
    fun `thousands separators are understood`() {
        val big = facts.copy(reels = 1200, previousPeriodReels = null)
        assertEquals(Verdict.Valid, NumberGuard.validate("A mighty 1,200 reels.", big))
        assertEquals(listOf("1,300"), invented("A mighty 1,300 reels.", big))
    }

    @Test
    fun `without a previous period only current numbers are allowed`() {
        val alone = facts.copy(previousPeriodReels = null)
        assertEquals(listOf("80", "50%"), invented("80 before, so up 50%.", alone))
    }

    @Test
    fun `banned phrases are rejected`() {
        val verdict = NumberGuard.validate("This is pure brain rot.", facts) as Verdict.Rejected
        assertEquals(listOf(Problem.BannedPhrase("brain rot")), verdict.problems)
        assertEquals("addict", NumberGuard.bannedPhrase("Total Addiction vibes"))
        assertNull(NumberGuard.bannedPhrase("A lazy, sleepy sofa evening."))
    }

    @Test
    fun `empty and overlong text are rejected`() {
        assertEquals(Verdict.Rejected(listOf(Problem.Empty)), NumberGuard.validate("   ", facts))
        val verdict = NumberGuard.validate("Scroll. ".repeat(40), facts) as Verdict.Rejected
        assertTrue(verdict.problems.single() is Problem.TooLong)
        assertEquals(Verdict.Valid, NumberGuard.validate("a".repeat(NumberGuard.MAX_LENGTH), facts))
    }
}
