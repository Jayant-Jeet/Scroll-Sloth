package com.reelsloth.ai

import com.reelsloth.score.ScoreBand
import com.reelsloth.stats.PeriodStats
import kotlin.math.roundToInt

/**
 * The only numbers commentary may mention. Everything here comes from [PeriodStats] and the
 * laziness band; nothing is computed by the language model.
 *
 * [bandIndex] picks the template set and is not shown to the model.
 */
data class StatsFacts(
    val reels: Int,
    val minutes: Int,
    val band: String,
    val bandIndex: Int,
    val lateNightReels: Int,
    val previousPeriodReels: Int?,
) {
    /** Reels minus the previous period's reels, or null without a previous period. */
    val delta: Int? get() = previousPeriodReels?.let { reels - it }

    companion object {
        fun of(stats: PeriodStats, previous: PeriodStats?, band: ScoreBand): StatsFacts = StatsFacts(
            reels = stats.reels,
            minutes = stats.minutes.roundToInt(),
            band = band.label,
            bandIndex = band.index,
            lateNightReels = stats.lateNightReels,
            previousPeriodReels = previous?.reels,
        )
    }
}
