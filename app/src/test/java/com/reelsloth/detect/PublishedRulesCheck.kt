package com.reelsloth.detect

/**
 * The "Validate rules" CI check for the published `site/detection-rules.json`.
 *
 * Stricter than the app's loader, which must stay lenient so older builds can read newer files:
 * here a misspelt key, an unknown app, or a broken rule fails instead of silently disabling
 * detection on every phone that downloads the file. An app whose rule still holds
 * [DetectionRules.TODO_MARKER] values is allowed; it is simply not calibrated yet.
 */
object PublishedRulesCheck {
    fun problems(text: String, bundledRulesVersion: Int): List<String> {
        val rules = try {
            DetectionRulesLoader.parse(text, strict = true)
        } catch (e: RulesFormatException) {
            return listOf(e.message ?: "Not a valid rules file.")
        }
        val problems = mutableListOf<String>()
        if (rules.rulesVersion <= bundledRulesVersion) {
            problems += "rulesVersion ${rules.rulesVersion} must be higher than the bundled rules " +
                "($bundledRulesVersion), or no phone will ever take it."
        }
        val supported = Platform.entries.joinToString { it.packageName }
        rules.apps.keys.filter { Platform.fromPackageName(it) == null }.forEach {
            problems += "Unknown app \"$it\". Only $supported are supported."
        }
        val loaded = DetectionRulesLoader.validate(rules, RulesSource.DOWNLOADED)
        rules.apps.forEach { (packageName, rule) ->
            val platform = Platform.fromPackageName(packageName) ?: return@forEach
            val status = loaded.statuses.getValue(platform)
            if (status.state != RuleState.READY && !rule.isPlaceholder) {
                problems += "${platform.label}: ${status.reason}"
            }
        }
        if (loaded.enabledApps.isEmpty()) {
            problems += "No app is calibrated, so phones would reject this file."
        }
        return problems
    }

    private val AppRule.isPlaceholder: Boolean
        get() = (reelScreenMatchers + listOfNotNull(pagerMatcher) + keyMatchers).any { it.hasTodo } ||
            keyFields.any { it == DetectionRules.TODO_MARKER }
}