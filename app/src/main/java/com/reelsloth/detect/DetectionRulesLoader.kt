package com.reelsloth.detect

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Where the active rules came from: the APK, a file the user imported, or the rules server. */
enum class RulesSource { BUNDLED, OVERRIDE, DOWNLOADED }

enum class RuleState { READY, NEEDS_CALIBRATION }

data class AppRuleStatus(
    val platform: Platform,
    val state: RuleState,
    val reason: String? = null,
)

/** Parsed rules plus which apps are usable. Only [enabledApps] are ever evaluated. */
data class LoadedRules(
    val rules: DetectionRules,
    val source: RulesSource,
    val statuses: Map<Platform, AppRuleStatus>,
    val enabledApps: Map<String, AppRule>,
    val warning: String? = null,
)

class RulesFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

object DetectionRulesLoader {
    val json = Json { ignoreUnknownKeys = true }

    /** Parses and sanity-checks rules. Throws [RulesFormatException] for anything unusable. */
    fun parse(text: String): DetectionRules {
        val rules = try {
            json.decodeFromString(DetectionRules.serializer(), text)
        } catch (e: SerializationException) {
            throw RulesFormatException("Not a valid rules file: ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            throw RulesFormatException("Not a valid rules file: ${e.message}", e)
        }
        if (rules.schemaVersion != DetectionRules.SUPPORTED_SCHEMA_VERSION) {
            throw RulesFormatException(
                "Rules schema ${rules.schemaVersion} is not supported (expected ${DetectionRules.SUPPORTED_SCHEMA_VERSION}).",
            )
        }
        if (rules.minDwellMs <= 0 || rules.maxDwellMs < rules.minDwellMs) {
            throw RulesFormatException("Dwell range ${rules.minDwellMs}..${rules.maxDwellMs} ms is not valid.")
        }
        return rules
    }

    fun load(text: String, source: RulesSource): LoadedRules = validate(parse(text), source)

    /** Works out which apps have complete rules. Incomplete apps are disabled, never guessed. */
    fun validate(rules: DetectionRules, source: RulesSource): LoadedRules {
        val statuses = Platform.entries.associateWith { platform ->
            val rule = rules.apps[platform.packageName]
            val problem = if (rule == null) "No rule for this app yet." else problemWith(platform, rule)
            AppRuleStatus(platform, if (problem == null) RuleState.READY else RuleState.NEEDS_CALIBRATION, problem)
        }
        val enabledApps = rules.apps.filter { (packageName, _) ->
            val platform = Platform.fromPackageName(packageName)
            platform != null && statuses.getValue(platform).state == RuleState.READY
        }
        return LoadedRules(rules, source, statuses, enabledApps)
    }

    private fun problemWith(platform: Platform, rule: AppRule): String? {
        val matchers = rule.reelScreenMatchers + listOfNotNull(rule.pagerMatcher) + rule.keyMatchers
        return when {
            rule.platform != platform -> "Rule says ${rule.platform.label} but is filed under ${platform.label}."
            matchers.any { it.hasTodo } || rule.keyFields.any { it == DetectionRules.TODO_MARKER } ->
                "Values still need to be captured with the Rule Inspector."
            rule.reelScreenMatchers.isEmpty() -> "No reel screen matchers."
            matchers.any { !it.hasAnyCondition } -> "A matcher has no conditions and would match anything."
            rule.pagerMatcher == null -> "No pager matcher."
            rule.strategy == DetectionStrategy.KEY_CHANGED && rule.keyFields.isEmpty() -> "No key fields."
            rule.keyFields.any { KeyField.fromJsonName(it) == null } -> "Unknown key field."
            else -> null
        }
    }
}
