package com.halfbloodcoder.scrollsloth.score

/**
 * Turns the text fields of the scoring settings into a [ScoreConfig], or a readable error.
 * The late-night share is typed as a whole percentage (50 means 0.5).
 */
object ScoreConfigInput {
    sealed interface Result {
        data class Valid(val config: ScoreConfig) : Result
        data class Invalid(val message: String) : Result
    }

    data class Fields(
        val heavyDaily: String,
        val dailyMinutesBudget: String,
        val sessionHeavy: String,
        val lateSharePercent: String,
        val bandLabels: List<String>,
    )

    fun fieldsOf(config: ScoreConfig) = Fields(
        heavyDaily = config.heavyDaily.toString(),
        dailyMinutesBudget = config.dailyMinutesBudget.toString(),
        sessionHeavy = config.sessionHeavy.toString(),
        lateSharePercent = (config.lateShareHeavy * 100).let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() },
        bandLabels = config.bandLabels,
    )

    fun parse(fields: Fields): Result {
        val heavy = fields.heavyDaily.wholeNumber() ?: return Result.Invalid("Heavy day needs a whole number of reels.")
        val budget = fields.dailyMinutesBudget.wholeNumber()
            ?: return Result.Invalid("Minutes budget needs a whole number of minutes.")
        val session = fields.sessionHeavy.wholeNumber() ?: return Result.Invalid("Heavy session needs a whole number of reels.")
        val percent = fields.lateSharePercent.trim().toDoubleOrNull()
            ?: return Result.Invalid("Late-night share needs a percentage.")
        if (percent <= 0.0 || percent > 100.0) return Result.Invalid("Late-night share must be above 0% and at most 100%.")
        return try {
            Result.Valid(
                ScoreConfig(
                    heavyDaily = heavy,
                    dailyMinutesBudget = budget,
                    sessionHeavy = session,
                    lateShareHeavy = percent / 100.0,
                    bandLabels = fields.bandLabels.map { it.trim() },
                ),
            )
        } catch (e: IllegalArgumentException) {
            Result.Invalid(e.message ?: "Those values do not work together.")
        }
    }

    private fun String.wholeNumber(): Int? = trim().toIntOrNull()
}
