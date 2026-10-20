package com.halfbloodcoder.scrollsloth.detect

import com.halfbloodcoder.scrollsloth.debug.InspectableNode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Glue between the accessibility service and the pure detection classes. Rebuilds the
 * [RuleEngine] and [ReelStateMachine] when the rules change, closing the current reel first.
 * Not thread-safe: call it from one thread (the service uses a single-threaded dispatcher).
 */
class ReelDetector(
    private val rules: () -> LoadedRules,
    private val clock: Clock,
    private val sink: (ReelViewed) -> Unit,
) {
    private var activeRules: LoadedRules? = null
    private var engine: RuleEngine? = null
    private var machine: ReelStateMachine? = null

    private val latest = MutableStateFlow<DetectorEvent?>(null)

    /** Last observation, for the diagnostics readout. Holds only hashes and indexes. */
    val lastEvent: StateFlow<DetectorEvent?> = latest.asStateFlow()

    val hasCurrentReel: Boolean get() = machine?.hasCurrentReel == true

    /** While true, windows are ignored and nothing is counted. */
    var paused: Boolean = false
        private set

    /** Pausing closes the open reel the same way leaving the app does, so time already watched still counts. */
    fun setPaused(paused: Boolean) {
        if (paused == this.paused) return
        if (paused) reset(ResetReason.TRACKING_PAUSED)
        this.paused = paused
    }

    /** Returns the observation, or null while [paused]. */
    fun onWindow(packageName: String, root: InspectableNode?): DetectorEvent? {
        if (paused) return null
        val engine = ensureCurrentRules()
        val event = engine.evaluate(clock.nowMs(), packageName, root)
        machine?.onEvent(event)?.forEach(sink)
        latest.value = event
        return event
    }

    fun onOtherAppInForeground() = reset(ResetReason.APP_SWITCH)

    fun reset(reason: ResetReason) {
        machine?.reset(reason)?.forEach(sink)
        if (reason != ResetReason.RULES_CHANGED) latest.value = null
    }

    private fun ensureCurrentRules(): RuleEngine {
        val current = rules()
        val existing = engine
        if (existing != null && current === activeRules) return existing

        reset(ResetReason.RULES_CHANGED)
        activeRules = current
        machine = ReelStateMachine(current.rules.minDwellMs, current.rules.maxDwellMs, clock)
        return RuleEngine(current).also { engine = it }
    }
}
