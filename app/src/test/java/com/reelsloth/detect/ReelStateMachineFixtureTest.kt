package com.reelsloth.detect

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Replays recorded-style detector event streams (one screen-change event every 250-400 ms, as seen
 * in Rule Inspector event logs) and compares what the state machine emits with the expected list.
 */
@RunWith(Parameterized::class)
class ReelStateMachineFixtureTest(private val fixtureName: String) {
    @Serializable
    private data class Step(
        val timeMs: Long,
        val packageName: String? = null,
        val onReelScreen: Boolean = false,
        val reelKeyHash: Int? = null,
        val pagerIndex: Int? = null,
        val reset: ResetReason? = null,
    )

    @Serializable
    private data class Fixture(
        val description: String,
        val minDwellMs: Long,
        val maxDwellMs: Long,
        val steps: List<Step>,
        val expected: List<ReelViewed>,
    )

    @Test
    fun replayEmitsExpectedViews() {
        val text = requireNotNull(javaClass.getResource("/fixtures/$fixtureName.json")) {
            "Missing fixture $fixtureName"
        }.readText()
        val fixture = Json.decodeFromString(Fixture.serializer(), text)
        var now = 0L
        val machine = ReelStateMachine(fixture.minDwellMs, fixture.maxDwellMs, clock = { now })

        val emitted = fixture.steps.flatMap { step ->
            now = step.timeMs
            val reset = step.reset
            if (reset != null) {
                machine.reset(reset)
            } else {
                machine.onEvent(
                    DetectorEvent(
                        timeMs = step.timeMs,
                        packageName = requireNotNull(step.packageName),
                        onReelScreen = step.onReelScreen,
                        reelKeyHash = step.reelKeyHash,
                        pagerIndex = step.pagerIndex,
                    ),
                )
            }
        }

        assertEquals(fixture.description, fixture.expected, emitted)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun fixtures(): List<String> = listOf(
            "fast_flicks",
            "three_second_views",
            "loop_capped",
            "scroll_back_and_forth",
            "scroll_back_and_forth_keys",
            "revisit_after_window",
            "leave_mid_reel",
            "app_switch_resets",
            "leave_app",
            "interleaved_platforms",
        )
    }
}
