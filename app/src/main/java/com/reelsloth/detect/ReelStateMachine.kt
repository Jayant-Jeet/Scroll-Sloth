package com.reelsloth.detect

import java.util.EnumMap

/**
 * Turns [DetectorEvent]s into [ReelViewed]s. Pure Kotlin: no Android types, time comes from the
 * events themselves or, for [reset], from [clock].
 *
 * A reel is identified by its key hash, else its pager index. It is counted once, when it stops
 * being current, if it stayed current for at least [minDwellMs]. Dwell is capped at [maxDwellMs]
 * so a looping reel is one reel. Each platform remembers its last [historySize] reels; one seen
 * again within [dedupeWindowMs] of last being current is not recounted.
 *
 * Pager indexes only mean something inside one pager session, so they are forgotten whenever the
 * session ends (leaving the reel screen, switching apps, or [reset]). Key hashes are kept.
 */
class ReelStateMachine(
    private val minDwellMs: Long,
    private val maxDwellMs: Long,
    private val clock: Clock,
    private val dedupeWindowMs: Long = DEFAULT_DEDUPE_WINDOW_MS,
    private val historySize: Int = DEFAULT_HISTORY_SIZE,
) {
    private sealed interface Identity {
        data class Key(val hash: Int) : Identity
        data class Index(val index: Int) : Identity
    }

    private class CurrentReel(var identity: Identity?, val startMs: Long)

    private class PlatformState(historySize: Int) {
        var current: CurrentReel? = null

        /** Last time each recent reel stopped being current, least recently used first. */
        val history = object : LinkedHashMap<Identity, Long>(historySize + 1, 1f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Identity, Long>?) = size > historySize
        }
    }

    private val states = EnumMap<Platform, PlatformState>(Platform::class.java)
    private var activePlatform: Platform? = null

    val hasCurrentReel: Boolean get() = states.values.any { it.current != null }

    fun onEvent(event: DetectorEvent): List<ReelViewed> {
        val platform = Platform.fromPackageName(event.packageName) ?: return emptyList()
        val emitted = mutableListOf<ReelViewed>()

        val previous = activePlatform
        if (previous != null && previous != platform) endSession(previous, event.timeMs, emitted)
        activePlatform = platform

        if (!event.onReelScreen) {
            endSession(platform, event.timeMs, emitted)
            return emitted
        }

        val state = states.getOrPut(platform) { PlatformState(historySize) }
        val identity: Identity? = event.reelKeyHash?.let { Identity.Key(it) } ?: event.pagerIndex?.let { Identity.Index(it) }
        val current = state.current
        when {
            current == null -> state.current = CurrentReel(identity, event.timeMs)
            identity == null || identity == current.identity -> Unit
            // The screen settled before the reel could be identified; this is still the same reel.
            current.identity == null -> current.identity = identity
            else -> {
                finish(platform, state, event.timeMs)?.let(emitted::add)
                state.current = CurrentReel(identity, event.timeMs)
            }
        }
        return emitted
    }

    /** Closes every platform's current reel at [Clock.nowMs]. All reasons close the same way. */
    @Suppress("UNUSED_PARAMETER")
    fun reset(reason: ResetReason): List<ReelViewed> {
        val now = clock.nowMs()
        val emitted = mutableListOf<ReelViewed>()
        states.keys.toList().forEach { endSession(it, now, emitted) }
        activePlatform = null
        return emitted
    }

    private fun endSession(platform: Platform, endMs: Long, emitted: MutableList<ReelViewed>) {
        val state = states[platform] ?: return
        finish(platform, state, endMs)?.let(emitted::add)
        state.history.keys.removeAll { it is Identity.Index }
    }

    private fun finish(platform: Platform, state: PlatformState, endMs: Long): ReelViewed? {
        val current = state.current ?: return null
        state.current = null
        val identity = current.identity ?: return null
        val dwellMs = endMs - current.startMs
        if (dwellMs < minDwellMs) return null

        val lastSeenMs = state.history[identity]
        state.history[identity] = endMs
        if (lastSeenMs != null && current.startMs - lastSeenMs < dedupeWindowMs) return null

        return ReelViewed(platform, current.startMs, minOf(dwellMs, maxDwellMs))
    }

    companion object {
        const val DEFAULT_DEDUPE_WINDOW_MS = 60_000L
        const val DEFAULT_HISTORY_SIZE = 10
    }
}
