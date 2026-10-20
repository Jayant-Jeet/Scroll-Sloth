package com.halfbloodcoder.scrollsloth.ai

import com.halfbloodcoder.scrollsloth.score.ScoreBands
import com.halfbloodcoder.scrollsloth.stats.Period
import kotlin.math.abs

/**
 * Deterministic commentary from fixed templates. Always available, so the app has commentary
 * before (or without) the language model.
 *
 * Placeholders, filled only from [StatsFacts]:
 * {When}/{when} period ("Yesterday", "last week"), {reels} "N reels", {minutes} "N minutes",
 * {late} "N late-night reels", {previous} "N reels" in the previous period,
 * {change} "N more than"/"N fewer than"/"the same as", {before} "the day before" etc., {band} band label.
 */
class TemplateCommentaryEngine : CommentaryEngine {
    override suspend fun write(request: CommentaryRequest): CommentaryResult =
        CommentaryResult(text = render(request), source = CommentarySource.TEMPLATE)

    fun render(request: CommentaryRequest): String {
        val facts = request.facts
        val candidates = templatesFor(facts.bandIndex, request.tone).filter { it.fits(facts) }
        // Same facts always give the same line; different days usually give different lines.
        val seed = facts.reels * 31 + facts.minutes * 17 + facts.lateNightReels * 7 + request.period.ordinal
        val template = candidates[abs(seed) % candidates.size]
        return fill(template.text, request)
    }

    internal fun fill(text: String, request: CommentaryRequest): String {
        val facts = request.facts
        val period = request.period.completedLabel
        val replacements = mapOf(
            "{When}" to period.replaceFirstChar { it.uppercase() },
            "{when}" to period,
            "{reels}" to count(facts.reels, "reel"),
            "{minutes}" to count(facts.minutes, "minute"),
            "{late}" to count(facts.lateNightReels, "late-night reel"),
            "{previous}" to count(facts.previousPeriodReels ?: 0, "reel"),
            "{change}" to change(facts.delta ?: 0),
            "{before}" to request.period.beforeLabel,
            "{band}" to facts.band,
        )
        return replacements.entries.fold(text) { acc, (key, value) -> acc.replace(key, value) }
    }

    private fun count(n: Int, noun: String) = "$n ${if (n == 1) noun else noun + "s"}"

    private fun change(delta: Int) = when {
        delta > 0 -> "${count(delta, "reel")} more than"
        delta < 0 -> "${count(-delta, "reel")} fewer than"
        else -> "the same as"
    }

    data class Template(val text: String, val needsPrevious: Boolean = false, val needsLate: Boolean = false) {
        fun fits(facts: StatsFacts): Boolean =
            (!needsPrevious || facts.previousPeriodReels != null) && (!needsLate || facts.lateNightReels > 0)
    }

    companion object {
        fun templatesFor(bandIndex: Int, tone: Tone): List<Template> =
            TEMPLATES.getValue(tone)[bandIndex.coerceIn(0, ScoreBands.COUNT - 1)]

        private fun t(text: String) = Template(text)
        private fun prev(text: String) = Template(text, needsPrevious = true)
        private fun late(text: String) = Template(text, needsLate = true)

        /** Per tone, one list per band, lowest band first. */
        val TEMPLATES: Map<Tone, List<List<Template>>> = mapOf(
            Tone.GENTLE to listOf(
                listOf(
                    t("{When} you watched {reels}. Your thumb barely broke a sweat."),
                    t("Just {reels} and {minutes} {when}. That is a calm, wide-awake rhythm."),
                    t("{When} stayed in \"{band}\" territory with {reels}. Nicely done."),
                    t("{minutes} of reels {when}. The rest of the time was all yours."),
                    t("{reels} {when}. The sloth had very little to report."),
                    t("A light touch {when}: {reels} over {minutes}. Keep it easy."),
                    prev("{When} you watched {change} {before}. Steady as you go."),
                ),
                listOf(
                    t("{reels} and {minutes} {when}. A casual scroll, nothing more."),
                    t("{When} landed on \"{band}\" with {reels}. Still plenty of room in your day."),
                    t("{minutes} of scrolling {when}. A small dip, then back to life."),
                    t("{reels} {when}. A gentle pace; maybe pick a stopping point next time."),
                    t("You dipped in for {reels} {when}. Comfortably in the casual zone."),
                    late("{late} came late at night {when}. An earlier wind-down might feel nice."),
                    prev("{When} you watched {change} {before}. Small shifts add up."),
                ),
                listOf(
                    t("{reels} and {minutes} {when}. The sofa got some company."),
                    t("{When} reached \"{band}\" with {reels}. A walk might feel good next time."),
                    t("{minutes} went to reels {when}. Maybe trade a few for something you enjoy offline."),
                    t("{reels} {when}. A middle-of-the-road day for your thumb."),
                    t("You spent {minutes} on reels {when}. Try a timer next session."),
                    late("{late} were after dark {when}. Your evening self deserves some rest."),
                    prev("{When} you watched {change} {before}. Worth a gentle look."),
                ),
                listOf(
                    t("{reels} and {minutes} {when}. That is a lot of scrolling, so be kind to yourself today."),
                    t("{When} slid into \"{band}\" with {reels}. A short break from reels could feel refreshing."),
                    t("{minutes} of reels {when}. Maybe plan a reel-free hour today."),
                    t("{reels} {when}. Your thumb put in a long shift."),
                    t("You watched {reels} {when}. Small steps help, like keeping the phone in another room for a while."),
                    late("{late} came late at night {when}. Consider an earlier cut-off tonight."),
                    prev("{When} you watched {change} {before}. Noticing it is a good start."),
                ),
                listOf(
                    t("{reels} and {minutes} {when}. Today is a fresh start."),
                    t("{When} hit \"{band}\" with {reels}. Try a small change today."),
                    t("{minutes} went to reels {when}. Maybe swap the next session for something offline."),
                    t("{reels} {when}. Your thumb has earned a long rest."),
                    t("You watched {reels} {when}. Setting a stopping point before you start can really help."),
                    late("{late} were late at night {when}. Give tonight an earlier finish."),
                    prev("{When} you watched {change} {before}. Every period is a new chance."),
                ),
            ),
            Tone.ROAST to listOf(
                listOf(
                    t("{reels} {when}? The algorithm is filing a missing person report."),
                    t("Only {minutes} of reels {when}. Touching grass professionally, are we?"),
                    t("{When}: \"{band}\" with {reels}. The sloth is frankly disappointed."),
                    t("{reels} {when}. Your thumb is basically on holiday."),
                    t("{minutes} {when}. Influencers are wondering where you went."),
                    t("{reels} {when}, {minutes}. Suspiciously productive behaviour."),
                    prev("{When} you watched {change} {before}. The feed misses you."),
                ),
                listOf(
                    t("{reels} {when}. Just a casual scroll, you keep telling yourself."),
                    t("{minutes} of reels {when}. Not hooked, just hanging around."),
                    t("{When}: \"{band}\" with {reels}. The sofa is starting to recognise you."),
                    t("{reels} {when}. Your thumb has started warming up."),
                    t("You watched {reels} {when}. \"Just a little longer\" said with confidence."),
                    late("{late} after dark {when}. The night owls say hello."),
                    prev("{When} you watched {change} {before}. Thumb training is going well."),
                ),
                listOf(
                    t("{reels} {when}. The sofa now has a you-shaped dent."),
                    t("{minutes} of reels {when}. Your thumb did more cardio than anyone else in the room."),
                    t("{When}: \"{band}\" with {reels}. The sloth nods in quiet respect."),
                    t("{reels} and {minutes} {when}. The algorithm sends its warmest regards."),
                    t("You watched {reels} {when}. Somewhere, a to-do list sighs."),
                    late("{late} after dark {when}. Bedtime was more of a suggestion."),
                    prev("{When} you watched {change} {before}. Bold strategy."),
                ),
                listOf(
                    t("{reels} {when}. Your thumb deserves a pay rise."),
                    t("{minutes} of reels {when}. The sofa has filed for joint custody."),
                    t("{When}: \"{band}\" with {reels}. Hibernation, but make it vertical video."),
                    t("{reels} and {minutes} {when}. The algorithm knows you better than your friends do."),
                    t("You watched {reels} {when}. The feed did not end, and neither did you."),
                    late("{late} after dark {when}. The moon saw everything."),
                    prev("{When} you watched {change} {before}. The sloth is taking notes."),
                ),
                listOf(
                    t("{reels} {when}. Archaeologists will find you mid-scroll."),
                    t("{minutes} of reels {when}. The sofa cushions have merged with you."),
                    t("{When}: \"{band}\" with {reels}. Even the sloth called that excessive."),
                    t("{reels} and {minutes} {when}. Your thumb is legally a professional athlete."),
                    t("You watched {reels} {when}. The algorithm is naming a server after you."),
                    late("{late} after dark {when}. Sleep sent a strongly worded email."),
                    prev("{When} you watched {change} {before}. A new personal record, of sorts."),
                ),
            ),
        )
    }
}

private val Period.beforeLabel: String
    get() = when (this) {
        Period.DAY -> "the day before"
        Period.WEEK -> "the week before"
        Period.MONTH -> "the month before"
    }
