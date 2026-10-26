package com.halfbloodcoder.scrollsloth.ai

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Checks model commentary before it is shown or stored. Every number in the text, written as
 * digits, a decimal, a percentage or a word, must come from [StatsFacts] or a simple value derived
 * from them. Recognized units must match their facts, comparison direction must match the signed
 * delta, and multiplier claims need an explicit ratio fact. The text must also be non-empty,
 * short, and free of [BANNED_PHRASES].
 */
object NumberGuard {
    const val MAX_LENGTH = 300

    sealed interface Verdict {
        data object Valid : Verdict
        data class Rejected(val problems: List<Problem>) : Verdict
    }

    sealed interface Problem {
        data object Empty : Problem
        data class TooLong(val length: Int) : Problem
        /** [tokens] as written in the text, for example "7", "35%" or "thirty". */
        data class InventedNumbers(val tokens: List<String>) : Problem
        data class WrongUnit(val tokens: List<String>) : Problem
        data class ContradictoryDirection(val words: List<String>) : Problem
        data class MultiplierClaims(val tokens: List<String>) : Problem
        data class BannedPhrase(val phrase: String) : Problem

        /** Letters from another script, a sign of garbled GPU output. */
        data class ForeignScript(val characters: String) : Problem
    }

    /** A number found in text. [value] is null for words like "hundreds" that are never allowed. */
    data class FoundNumber(val token: String, val value: BigDecimal?, val isPercent: Boolean)

    /** Values a text may mention. Percentages may only use [percents]; plain numbers may use either set. */
    data class AllowList(
        val plain: Set<BigDecimal>,
        val percents: Set<BigDecimal>,
        val reels: Set<BigDecimal>,
        val time: Set<BigDecimal>,
    )

    private enum class Direction { UP, DOWN, SAME, NONE }
    private enum class Unit { REELS, TIME, PERCENT }
    private data class NumberMatch(val number: FoundNumber, val endIndex: Int)

    /** Medical and mental-health claims, insults about the person's worth, and links. */
    val BANNED_PHRASES: List<String> = listOf(
        "addict", "depress", "anxiety", "anxious", "adhd", "dopamine", "mental health", "mental illness",
        "disorder", "diagnos", "therapy", "therapist", "psycholog", "psychiatr", "brain rot", "brainrot",
        "brain damage", "rot your brain", "insomnia", "doctor", "medical", "medicat", "symptom", "illness",
        "unhealthy", "trauma", "ocd", "suicid", "self-harm", "self harm", "kill yourself",
        "worthless", "pathetic", "loser", "useless", "stupid", "idiot", "dumb", "failure", "disgusting",
        "waste of space", "shame on you", "ashamed", "hopeless", "ugly",
        "http", "www.",
    )

    fun validate(text: String, facts: StatsFacts): Verdict {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return Verdict.Rejected(listOf(Problem.Empty))
        val problems = mutableListOf<Problem>()
        if (trimmed.length > MAX_LENGTH) problems += Problem.TooLong(trimmed.length)
        val allowed = allowList(facts)
        val numbers = numberMatches(trimmed)
        val invented = numbers.map { it.number }.filterNot { it.isAllowed(allowed) }.map { it.token }.distinct()
        if (invented.isNotEmpty()) problems += Problem.InventedNumbers(invented)
        val wrongUnits = numbers.filter { match ->
            val unit = match.unit(trimmed) ?: return@filter false
            val values = when (unit) {
                Unit.REELS -> allowed.reels
                Unit.TIME -> allowed.time
                Unit.PERCENT -> allowed.percents
            }
            match.number.value?.let(::normalize) !in values
        }.map { it.number.token }.distinct()
        if (wrongUnits.isNotEmpty()) problems += Problem.WrongUnit(wrongUnits)
        val delta = facts.delta
        val direction = when {
            delta == null -> Direction.NONE
            delta > 0 -> Direction.UP
            delta < 0 -> Direction.DOWN
            else -> Direction.SAME
        }
        val contradictions = COMPARISON_WORD.findAll(trimmed).filter { match ->
            when (direction) {
                Direction.UP -> match.value.lowercase() in DOWN_WORDS
                Direction.DOWN -> match.value.lowercase() in UP_WORDS
                Direction.SAME, Direction.NONE -> true
            }
        }.map { it.value }.distinct().toList()
        if (contradictions.isNotEmpty()) problems += Problem.ContradictoryDirection(contradictions)
        // StatsFacts has no explicit ratio fact; a ratio is not authorized by two counts alone.
        val multipliers = MULTIPLIER.findAll(trimmed).map { it.value }.toList() +
            numbers.filter { TIMES.containsMatchIn(trimmed.substring(it.endIndex)) }.map { "${it.number.token} times" }
        if (multipliers.isNotEmpty()) problems += Problem.MultiplierClaims(multipliers.distinct())
        bannedPhrase(trimmed)?.let { problems += Problem.BannedPhrase(it) }
        foreignScript(trimmed, facts.band)?.let { problems += Problem.ForeignScript(it) }
        return if (problems.isEmpty()) Verdict.Valid else Verdict.Rejected(problems)
    }

    fun allowList(facts: StatsFacts): AllowList {
        val plain = mutableSetOf<BigDecimal>()
        val percents = mutableSetOf<BigDecimal>()
        val reels = mutableSetOf<BigDecimal>()
        val time = mutableSetOf<BigDecimal>()
        fun add(set: MutableSet<BigDecimal>, value: Number) = set.add(normalize(BigDecimal(value.toString())))

        listOf(facts.reels, facts.lateNightReels).forEach { add(reels, it) }
        add(time, facts.minutes)
        facts.previousPeriodReels?.let { previous ->
            add(reels, previous)
            add(reels, kotlin.math.abs(facts.reels - previous))
            if (previous > 0) {
                val change = BigDecimal(facts.reels - previous).multiply(HUNDRED)
                    .divide(BigDecimal(previous), 0, RoundingMode.HALF_UP).abs()
                add(percents, change)
            }
        }
        if (facts.reels > 0) {
            add(percents, BigDecimal(facts.lateNightReels).multiply(HUNDRED).divide(BigDecimal(facts.reels), 0, RoundingMode.HALF_UP))
        }
        val hours = BigDecimal(facts.minutes).divide(SIXTY, 1, RoundingMode.HALF_UP)
        add(time, hours)
        add(time, hours.setScale(0, RoundingMode.HALF_UP))
        plain += reels
        plain += time
        DIGITS.findAll(facts.band).forEach { match -> parseDigits(match.value)?.let { add(plain, it) } }
        return AllowList(plain, percents, reels, time)
    }

    fun extractNumbers(text: String): List<FoundNumber> = numberMatches(text).map { it.number }

    private fun numberMatches(text: String): List<NumberMatch> {
        val found = mutableListOf<NumberMatch>()
        NUMBER.findAll(text).forEach { match ->
            val digits = match.groups[1]!!.value
            val isPercent = match.groups[2] != null
            found += NumberMatch(FoundNumber(match.value.trim(), parseDigits(digits), isPercent), match.range.last + 1)
        }
        WORD_NUMBER.findAll(text).forEach { match ->
            val word = match.groups[1]!!.value.lowercase()
            val isPercent = match.groups[2] != null
            found += NumberMatch(
                FoundNumber(match.value.trim(), NUMBER_WORDS[word]?.let { BigDecimal(it) }, isPercent),
                match.range.last + 1,
            )
        }
        return found
    }

    private fun NumberMatch.unit(text: String): Unit? {
        if (number.isPercent) return Unit.PERCENT
        val words = FOLLOWING_WORDS.find(text.substring(endIndex)) ?: return null
        return words.groupValues.drop(1).firstNotNullOfOrNull { word ->
            when (word.lowercase()) {
                "reel", "reels", "video", "videos", "short", "shorts" -> Unit.REELS
                "min", "minute", "minutes", "hour", "hours", "hr" -> Unit.TIME
                "percent" -> Unit.PERCENT
                else -> null
            }
        }
    }

    /**
     * Letters outside the Latin script (emoji and punctuation are script COMMON and pass), except
     * those in the band label, which the user may have renamed. Null when there are none.
     */
    fun foreignScript(text: String, band: String): String? {
        val foreign = buildString {
            var i = 0
            while (i < text.length) {
                val codePoint = text.codePointAt(i)
                val script = Character.UnicodeScript.of(codePoint)
                if (Character.isLetter(codePoint) && script !in ALLOWED_SCRIPTS && band.indexOf(String(Character.toChars(codePoint))) < 0) {
                    appendCodePoint(codePoint)
                }
                i += Character.charCount(codePoint)
            }
        }
        return foreign.ifEmpty { null }
    }

    private val ALLOWED_SCRIPTS = setOf(Character.UnicodeScript.LATIN, Character.UnicodeScript.COMMON, Character.UnicodeScript.INHERITED)

    fun bannedPhrase(text: String): String? {
        val lower = text.lowercase()
        return BANNED_PHRASES.firstOrNull { phrase ->
            Regex("(?<![a-z])" + Regex.escape(phrase)).containsMatchIn(lower)
        }
    }

    private fun FoundNumber.isAllowed(allowed: AllowList): Boolean {
        val number = value?.let(::normalize) ?: return false
        return if (isPercent) number in allowed.percents else number in allowed.plain || number in allowed.percents
    }

    private fun parseDigits(digits: String): BigDecimal? = digits.replace(",", "").toBigDecimalOrNull()

    private fun normalize(value: BigDecimal): BigDecimal =
        if (value.signum() == 0) BigDecimal.ZERO else value.stripTrailingZeros()

    private val HUNDRED = BigDecimal(100)
    private val SIXTY = BigDecimal(60)

    private val UP_WORDS = setOf("more", "higher", "up", "increase", "increased", "rose", "grew", "climbed", "jumped", "above", "worse")
    private val DOWN_WORDS = setOf("fewer", "less", "lower", "down", "drop", "dropped", "decreased", "fell", "dipped", "below", "better")
    private val COMPARISON_WORD = Regex("""\b(${(UP_WORDS + DOWN_WORDS).joinToString("|")})\b""", RegexOption.IGNORE_CASE)
    private val MULTIPLIER = Regex("""\b(twice|double|triple|half)\b""", RegexOption.IGNORE_CASE)
    private val TIMES = Regex("""^\s+times\b""", RegexOption.IGNORE_CASE)
    private const val UNIT_WORD = """[\p{L}]+(?:-[\p{L}]+)*"""
    private val FOLLOWING_WORDS = Regex("""^\s*($UNIT_WORD)(?:[ \t]+($UNIT_WORD))?""")

    private const val PERCENT_SUFFIX = """(\s*(?:%|percent\b|per cent\b))?"""

    /** Digits with optional thousands separators and decimals, e.g. 7, 1,200, 1.5, 35%. */
    private val NUMBER = Regex("""(?<![\d.])(\d{1,3}(?:,\d{3})+(?![\d,])|\d+(?:\.\d+)?)""" + PERCENT_SUFFIX, RegexOption.IGNORE_CASE)

    private val DIGITS = Regex("""\d+(?:\.\d+)?""")

    private val NUMBER_WORDS: Map<String, Int?> = mapOf(
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6,
        "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12,
        "thirteen" to 13, "fourteen" to 14, "fifteen" to 15, "sixteen" to 16, "seventeen" to 17,
        "eighteen" to 18, "nineteen" to 19, "twenty" to 20, "thirty" to 30, "forty" to 40,
        "fifty" to 50, "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90,
        // Vague quantities can never be checked against the facts.
        "dozen" to null, "dozens" to null, "hundred" to null, "hundreds" to null,
        "thousand" to null, "thousands" to null, "million" to null, "millions" to null,
    )

    private val WORD_NUMBER = Regex(
        """\b(${NUMBER_WORDS.keys.joinToString("|")})\b""" + PERCENT_SUFFIX,
        RegexOption.IGNORE_CASE,
    )
}
