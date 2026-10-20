package com.halfbloodcoder.scrollsloth.detect

import kotlinx.serialization.Serializable

/**
 * One observation of a supported app's screen. Carries no content: [reelKeyHash] is an in-memory
 * hash of the reel's identity fields and [pagerIndex] is the pager position.
 */
@Serializable
data class DetectorEvent(
    val timeMs: Long,
    val packageName: String,
    val onReelScreen: Boolean,
    val reelKeyHash: Int? = null,
    val pagerIndex: Int? = null,
)

/** A reel the user stayed on for at least the minimum dwell. This is all that gets stored. */
@Serializable
data class ReelViewed(
    val platform: Platform,
    val timestampMs: Long,
    val dwellMs: Long,
)

@Serializable
enum class ResetReason {
    APP_SWITCH,
    SCREEN_OFF,
    SERVICE_DISCONNECTED,
    RULES_CHANGED,
    TRACKING_PAUSED,
}

/** Epoch milliseconds. Injected so the state machine can be tested without real time. */
fun interface Clock {
    fun nowMs(): Long

    companion object {
        val System = Clock { java.lang.System.currentTimeMillis() }
    }
}
