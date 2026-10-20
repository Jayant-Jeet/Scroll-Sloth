package com.reelsloth.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.reelsloth.detect.AppStatus
import com.reelsloth.detect.Platform

data class CalibrationUiState(
    val selectedPlatform: Platform,
    val session: CalibrationState,
    val appStatuses: Map<Platform, AppStatus>,
    val trackingPaused: Boolean,
    val loggedRuns: Int,
    val busy: Boolean,
    val message: String?,
)

data class CalibrationActions(
    val onBack: () -> Unit,
    val onSelectPlatform: (Platform) -> Unit,
    val onStart: () -> Unit,
    val onPlusOne: () -> Unit,
    val onMinusOne: () -> Unit,
    val onStop: () -> Unit,
    val onCancel: () -> Unit,
    val onExport: () -> Unit,
)

@Composable
fun CalibrationScreen(state: CalibrationUiState, actions: CalibrationActions, modifier: Modifier = Modifier) {
    val running = state.session as? CalibrationState.Running
    val platform = running?.platform ?: state.selectedPlatform
    val notReady = CalibrationGate.reasonNotReady(state.appStatuses[platform], state.trackingPaused)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = actions.onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("Back") }
            Text("Calibration", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            "Pick an app and tap Start. Then watch reels there and tap the floating +1 each time you really " +
                "watch one, not the ones you flick past. Come back here and tap Stop to compare with the detector.",
            style = MaterialTheme.typography.bodyMedium,
        )

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            Platform.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == platform,
                    onClick = { actions.onSelectPlatform(option) },
                    enabled = running == null,
                    shape = SegmentedButtonDefaults.itemShape(index, Platform.entries.size),
                ) { Text(option.label) }
            }
        }

        if (running == null) {
            notReady?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = actions.onStart,
                enabled = notReady == null && !state.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("Start") }
        } else {
            RunningCard(running, state.busy, actions)
        }

        (state.session as? CalibrationState.Idle)?.lastResult?.let { ResultCard(it) }
        state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Calibration log", style = MaterialTheme.typography.titleSmall)
                Text(
                    when (state.loggedRuns) {
                        0 -> "No runs logged yet. Each Stop adds a row: app, both counts, duration and versions."
                        1 -> "1 run logged on this phone."
                        else -> "${state.loggedRuns} runs logged on this phone."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(
                    onClick = actions.onExport,
                    enabled = state.loggedRuns > 0 && !state.busy,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Export calibration CSV") }
            }
        }
    }
}

@Composable
private fun RunningCard(running: CalibrationState.Running, busy: Boolean, actions: CalibrationActions) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Counting ${running.platform.label} reels", style = MaterialTheme.typography.titleMedium)
            Text(
                "${running.manualCount}",
                style = MaterialTheme.typography.displayLarge,
                modifier = Modifier.semantics { contentDescription = "${running.manualCount} counted by hand" },
            )
            Text(
                "counted by hand. The detector's count stays hidden until you stop, so it can't sway you.",
                style = MaterialTheme.typography.bodySmall,
            )
            FilledTonalButton(
                onClick = actions.onPlusOne,
                modifier = Modifier.fillMaxWidth().height(96.dp),
            ) { Text("+1", style = MaterialTheme.typography.headlineLarge) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    onClick = actions.onMinusOne,
                    enabled = running.manualCount > 0,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Undo last +1") }
                TextButton(onClick = actions.onCancel, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Discard")
                }
                Button(onClick = actions.onStop, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Stop")
                }
            }
        }
    }
}

@Composable
private fun ResultCard(result: CalibrationResult) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Last run: ${result.platform.label}, ${result.durationSec} s", style = MaterialTheme.typography.titleSmall)
            ResultRow("Detector counted", result.autoCount.toString())
            ResultRow("You counted", result.manualCount.toString())
            ResultRow("Absolute error", result.absoluteError.toString())
            ResultRow("Percentage error", result.formatPercentError())
            Text(result.verdict(), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
