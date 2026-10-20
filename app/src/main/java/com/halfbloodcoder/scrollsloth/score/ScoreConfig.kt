package com.halfbloodcoder.scrollsloth.score

/**
 * Thresholds that turn a day's numbers into scores. Every value is editable in Settings > Advanced.
 * The constructor rejects values that would divide by zero or make a band unreadable.
 */
data class ScoreConfig(
    /** Reels in one day that max out the volume component. */
    val heavyDaily: Int = DEFAULT_HEAVY_DAILY,
    /** Daily minutes you consider fine. Unproductivity maxes out at four times this. */
    val dailyMinutesBudget: Int = DEFAULT_DAILY_MINUTES_BUDGET,
    /** Reels in one session that max out the session component. */
    val sessionHeavy: Int = DEFAULT_SESSION_HEAVY,
    /** Share of reels watched late at night that maxes out the late-night component. */
    val lateShareHeavy: Double = DEFAULT_LATE_SHARE_HEAVY,
    val bandLabels: List<String> = ScoreBands.DEFAULT_LABELS,
) {
    init {
        require(heavyDaily in 1..MAX_COUNT) { "Heavy day must be between 1 and $MAX_COUNT reels." }
        require(dailyMinutesBudget in 1..MAX_MINUTES) { "Minutes budget must be between 1 and $MAX_MINUTES." }
        require(sessionHeavy in 1..MAX_COUNT) { "Heavy session must be between 1 and $MAX_COUNT reels." }
        require(lateShareHeavy > 0.0 && lateShareHeavy <= 1.0) { "Late-night share must be above 0 and at most 1." }
        require(bandLabels.size == ScoreBands.COUNT) { "There must be exactly ${ScoreBands.COUNT} band labels." }
        require(bandLabels.all { it.isNotBlank() }) { "Band labels cannot be blank." }
    }

    val bands: ScoreBands get() = ScoreBands(bandLabels)

    companion object {
        const val DEFAULT_HEAVY_DAILY = 200
        const val DEFAULT_DAILY_MINUTES_BUDGET = 30
        const val DEFAULT_SESSION_HEAVY = 100
        const val DEFAULT_LATE_SHARE_HEAVY = 0.5
        const val MAX_COUNT = 10_000
        const val MAX_MINUTES = 24 * 60
    }
}

/** One score band: an inclusive range of whole scores and its label. */
data class ScoreBand(val index: Int, val min: Int, val max: Int, val label: String)

/** Five fixed ranges (0-19, 20-39, 40-59, 60-79, 80-100) with configurable labels. */
data class ScoreBands(val labels: List<String> = DEFAULT_LABELS) {
    init {
        require(labels.size == COUNT) { "There must be exactly $COUNT band labels." }
    }

    val bands: List<ScoreBand> = labels.mapIndexed { index, label ->
        ScoreBand(
            index = index,
            min = index * WIDTH,
            max = if (index == COUNT - 1) MAX_SCORE else (index + 1) * WIDTH - 1,
            label = label,
        )
    }

    fun bandFor(score: Int): ScoreBand = bands[(score.coerceIn(0, MAX_SCORE) / WIDTH).coerceAtMost(COUNT - 1)]

    companion object {
        const val COUNT = 5
        const val MAX_SCORE = 100
        private const val WIDTH = 20
        val DEFAULT_LABELS = listOf("Fully awake", "Casual scroller", "Sofa sloth", "Reel hibernator", "Fossilised")
    }
}
