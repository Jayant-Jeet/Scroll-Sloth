package com.halfbloodcoder.scrollsloth.detect

/** What Settings shows for each supported app. */
enum class AppStatus { WORKING, NEEDS_CALIBRATION, PERMISSION_MISSING }

object AppStatuses {
    /**
     * Calibration comes first: without rules an app is never counted, even with the permission on.
     * Only calibrated apps then depend on the accessibility service being enabled.
     */
    fun of(rules: LoadedRules, serviceEnabled: Boolean): Map<Platform, AppStatus> =
        Platform.entries.associateWith { platform ->
            when {
                rules.statuses[platform]?.state != RuleState.READY -> AppStatus.NEEDS_CALIBRATION
                !serviceEnabled -> AppStatus.PERMISSION_MISSING
                else -> AppStatus.WORKING
            }
        }
}
