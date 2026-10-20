package com.halfbloodcoder.scrollsloth.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import com.halfbloodcoder.scrollsloth.ai.FailReason
import com.halfbloodcoder.scrollsloth.ai.ModelSpec
import com.halfbloodcoder.scrollsloth.ai.ModelState
import com.halfbloodcoder.scrollsloth.ai.Tone
import com.halfbloodcoder.scrollsloth.ai.Waiting
import java.util.Locale

/** Gentle / Roast. Segmented buttons keep a 48dp touch target. */
@Composable
fun ToneToggle(tone: Tone, onToneChange: (Tone) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        Tone.entries.forEachIndexed { index, option ->
            SegmentedButton(
                selected = tone == option,
                onClick = { onToneChange(option) },
                shape = SegmentedButtonDefaults.itemShape(index, Tone.entries.size),
                label = { Text(if (option == Tone.GENTLE) "Gentle" else "Roast") },
            )
        }
    }
}

@Composable
fun ToneSetting(tone: Tone, onToneChange: (Tone) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Commentary tone")
        ToneToggle(tone, onToneChange)
        Text(
            if (tone == Tone.GENTLE) "Kind nudges." else "Cheeky, never cruel.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
fun ModelCard(
    spec: ModelSpec,
    state: ModelState,
    allowMetered: Boolean,
    onAllowMeteredChange: (Boolean) -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("On-device writer", style = MaterialTheme.typography.titleMedium)
            Text(
                "${spec.displayName}, ${formatBytes(spec.sizeBytes)}, ${spec.license} licence. " +
                    "Writes the nightly verdict on your phone. Your numbers never leave it; without the model you get templates.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(state.describe(), style = MaterialTheme.typography.bodyMedium)
            if (state is ModelState.Downloading) {
                if (state.totalBytes > 0) {
                    LinearProgressIndicator(
                        progress = { (state.bytesSoFar.toFloat() / state.totalBytes).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            if (state is ModelState.Verifying) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            when (state) {
                ModelState.NotDownloaded, is ModelState.Failed -> Button(onClick = onDownload) { Text("Download") }
                is ModelState.Downloading -> OutlinedButton(onClick = onDelete) { Text("Cancel") }
                ModelState.Ready -> OutlinedButton(onClick = onDelete) { Text("Delete model") }
                ModelState.Checking, ModelState.Verifying -> Unit
            }
            ListItem(
                headlineContent = { Text("Allow mobile data") },
                supportingContent = { Text("Off means Wi-Fi only. Applies to the next download.") },
                trailingContent = { Switch(checked = allowMetered, onCheckedChange = onAllowMeteredChange) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
    }
}

@Composable
fun WriteNowItem(state: WorkInfo.State?, onWriteNow: () -> Unit) {
    ListItem(
        headlineContent = { Text("Write commentary now") },
        supportingContent = {
            Text(
                when (state) {
                    WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> "Queued…"
                    WorkInfo.State.RUNNING -> "Writing… this can take a minute with the model."
                    WorkInfo.State.SUCCEEDED -> "Done. Check the verdict cards."
                    WorkInfo.State.FAILED -> "That did not work. Try again later."
                    WorkInfo.State.CANCELLED, null -> "Runs the nightly writer straight away, ignoring charging and idle."
                },
            )
        },
        trailingContent = {
            OutlinedButton(
                onClick = onWriteNow,
                enabled = state != WorkInfo.State.RUNNING && state != WorkInfo.State.ENQUEUED,
            ) { Text("Write") }
        },
    )
}

private fun ModelState.describe(): String = when (this) {
    ModelState.Checking -> "Checking…"
    ModelState.NotDownloaded -> "Not downloaded."
    is ModelState.Downloading -> when (waitingFor) {
        Waiting.WIFI -> "Waiting for Wi-Fi."
        Waiting.NETWORK -> "Waiting for a connection."
        Waiting.RETRY -> "Paused, will retry."
        Waiting.OTHER -> "Paused."
        null -> if (totalBytes > 0) "Downloading ${formatBytes(bytesSoFar)} of ${formatBytes(totalBytes)}." else "Starting download…"
    }
    ModelState.Verifying -> "Checking the file is intact…"
    ModelState.Ready -> "Ready. Verdicts are written by Gemma overnight."
    is ModelState.Failed -> when (reason) {
        FailReason.NOT_ENOUGH_SPACE -> "Not enough free space. Clear a little over the model size and try again."
        FailReason.DOWNLOAD_FAILED -> "The download failed."
        FailReason.CHECKSUM_MISMATCH -> "The file arrived damaged and was deleted."
    }
}

internal fun formatBytes(bytes: Long): String {
    val gb = bytes / (1024.0 * 1024 * 1024)
    return if (gb >= 1) String.format(Locale.ROOT, "%.1f GB", gb) else "${bytes / (1024 * 1024)} MB"
}
