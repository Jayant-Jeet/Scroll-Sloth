package com.halfbloodcoder.scrollsloth.detect

import kotlinx.serialization.Serializable

/**
 * Versioned detection rules, read from `detection-rules.json`. Matcher values must come from
 * Rule Inspector captures; [TODO_MARKER] marks a value nobody has captured yet.
 */
@Serializable
data class DetectionRules(
    val schemaVersion: Int,
    val rulesVersion: Int,
    val minDwellMs: Long = DEFAULT_MIN_DWELL_MS,
    val maxDwellMs: Long = DEFAULT_MAX_DWELL_MS,
    val apps: Map<String, AppRule> = emptyMap(),
) {
    companion object {
        const val SUPPORTED_SCHEMA_VERSION = 1
        const val TODO_MARKER = "TODO_FROM_INSPECTOR"
        const val DEFAULT_MIN_DWELL_MS = 2_000L
        const val DEFAULT_MAX_DWELL_MS = 120_000L
    }
}

@Serializable
data class AppRule(
    val platform: Platform,
    /** Every matcher must match at least one node for the screen to count as a reel screen. */
    val reelScreenMatchers: List<NodeMatcher> = emptyList(),
    val strategy: DetectionStrategy,
    /** The vertical pager whose most visible child is the current reel. */
    val pagerMatcher: NodeMatcher? = null,
    /** Fields hashed (in memory only) to identify a reel under [DetectionStrategy.KEY_CHANGED]. */
    val keyFields: List<String> = emptyList(),
    /**
     * Optional: only page nodes matching any of these feed the key. Empty means the whole page.
     * Lets a rule skip parts that change during one reel, such as player controls or like counts.
     */
    val keyMatchers: List<NodeMatcher> = emptyList(),
)

@Serializable
enum class DetectionStrategy {
    /** A new reel is a new `collectionItemInfo.rowIndex` on the current pager page. */
    ITEM_CHANGED,

    /** A new reel is a new hash of [AppRule.keyFields] inside the current pager page. */
    KEY_CHANGED,
}

/** Field names allowed in [AppRule.keyFields]. */
enum class KeyField(val jsonName: String) {
    TEXT("text"),
    CONTENT_DESCRIPTION("contentDescription"),
    ;

    companion object {
        fun fromJsonName(name: String): KeyField? = entries.firstOrNull { it.jsonName == name }
    }
}

/**
 * Matches one accessibility node. All non-null conditions must hold. The bounds conditions are
 * fractions of the root window and exist because some apps obfuscate their view ids.
 */
@Serializable
data class NodeMatcher(
    val viewIdContains: String? = null,
    val classNameEquals: String? = null,
    val scrollable: Boolean? = null,
    val minWidthFraction: Float? = null,
    val minHeightFraction: Float? = null,
    /** Visible top edge at or below this fraction of the window height. */
    val minTopFraction: Float? = null,
    /** Visible right edge at or left of this fraction of the window width. */
    val maxRightFraction: Float? = null,
) {
    val hasAnyCondition: Boolean
        get() = viewIdContains != null || classNameEquals != null || scrollable != null ||
            minWidthFraction != null || minHeightFraction != null || minTopFraction != null ||
            maxRightFraction != null

    val hasTodo: Boolean
        get() = viewIdContains == DetectionRules.TODO_MARKER || classNameEquals == DetectionRules.TODO_MARKER
}
