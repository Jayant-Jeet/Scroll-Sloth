package com.halfbloodcoder.scrollsloth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.WorkInfo
import com.halfbloodcoder.scrollsloth.R
import com.halfbloodcoder.scrollsloth.ai.FailReason
import com.halfbloodcoder.scrollsloth.ai.ModelSpec
import com.halfbloodcoder.scrollsloth.ai.ModelState
import com.halfbloodcoder.scrollsloth.ai.Waiting
import com.halfbloodcoder.scrollsloth.ui.theme.SlothTheme
import com.halfbloodcoder.scrollsloth.ui.theme.slothText
import java.util.Locale

/** The on-device writer: which model, its status, and download / delete. */
@Composable
fun ModelCard(spec: ModelSpec, state: ModelState, onDownload: () -> Unit, onDelete: () -> Unit) {
    val colors = SlothTheme.colors
    val (name, variant) = splitModelName(spec.displayName)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(colors.reflection)
            .border(1.dp, colors.reflectionBorder, CardShape)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBox(R.drawable.ic_model, colors.mintText, boxSize = 38.dp, iconSize = 20.dp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(name, style = slothText(22.sp, lineHeight = 1.2f), color = colors.text)
                if (variant != null) Text(variant, style = slothText(12.sp), color = colors.reflectionBody)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FactPill(formatBytes(spec.sizeBytes))
            FactPill("${spec.license} license")
            FactPill("Runs offline")
        }
        Text(
            "Writes the nightly verdict on your phone. Your numbers never leave it; without the model, you get templates.",
            style = slothText(13.sp, lineHeight = 1.6f),
            color = colors.reflectionBody,
        )
        SlothDivider(color = colors.reflectionBorder)
        val (label, detail) = state.statusLine()
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (state == ModelState.Ready) SlothIcon(R.drawable.ic_check_circle, colors.mintText, 14.dp)
                Text(
                    label,
                    style = slothText(13.sp, FontWeight.SemiBold),
                    color = if (state is ModelState.Failed) colors.destructive else colors.mintText,
                )
            }
            Text(detail, style = slothText(12.sp, lineHeight = 1.5f), color = colors.reflectionBody)
        }
        val progress = (state as? ModelState.Downloading)?.takeIf { it.totalBytes > 0 }
            ?.let { (it.bytesSoFar.toFloat() / it.totalBytes).coerceIn(0f, 1f) }
        when {
            progress != null -> LinearProgressIndicator(
                progress = { progress },
                color = colors.mint,
                trackColor = colors.inset,
                modifier = Modifier.fillMaxWidth(),
            )
            state is ModelState.Downloading || state == ModelState.Verifying -> LinearProgressIndicator(
                color = colors.mint,
                trackColor = colors.inset,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        when (state) {
            ModelState.NotDownloaded, is ModelState.Failed ->
                ActionPill(if (state is ModelState.Failed) "Try again" else "Download", R.drawable.ic_export, onDownload)
            is ModelState.Downloading -> ActionPill("Cancel", null, onDelete, color = colors.destructive)
            ModelState.Ready -> ActionPill("Delete model", R.drawable.ic_trash, onDelete, color = colors.destructive)
            ModelState.Checking, ModelState.Verifying -> Unit
        }
    }
}

@Composable
private fun FactPill(text: String) {
    Text(
        text,
        style = slothText(11.sp, FontWeight.Medium),
        color = SlothTheme.colors.mintText,
        modifier = Modifier
            .clip(CircleShape)
            .background(SlothTheme.colors.inset)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
fun WriteNowItem(state: WorkInfo.State?, onWriteNow: () -> Unit) {
    SettingRow(
        title = "Write commentary now",
        description = when (state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> "Queued…"
            WorkInfo.State.RUNNING -> "Writing… this can take a minute with the model."
            WorkInfo.State.SUCCEEDED -> "Done. Check the verdict cards."
            WorkInfo.State.FAILED -> "That did not work. Try again later."
            WorkInfo.State.CANCELLED, null -> "Runs the nightly writer straight away, ignoring charging and idle."
        },
    ) {
        ActionPill(
            "Write",
            R.drawable.ic_sparkles,
            onWriteNow,
            enabled = state != WorkInfo.State.RUNNING && state != WorkInfo.State.ENQUEUED,
        )
    }
}

/** "Gemma 4 E2B (instruction-tuned)" becomes "Gemma 4 E2B" and "Instruction-tuned". */
internal fun splitModelName(displayName: String): Pair<String, String?> {
    val open = displayName.indexOf('(')
    if (open <= 0 || !displayName.endsWith(")")) return displayName to null
    val variant = displayName.substring(open + 1, displayName.length - 1).trim()
    return displayName.substring(0, open).trim() to variant.replaceFirstChar { it.titlecase(Locale.getDefault()) }
}

private fun ModelState.statusLine(): Pair<String, String> = when (this) {
    ModelState.Checking -> "Checking" to "Making sure the model file is intact…"
    ModelState.NotDownloaded -> "Not downloaded" to "Templates write the verdicts until you download it."
    is ModelState.Downloading -> "Downloading" to describe()
    ModelState.Verifying -> "Verifying" to describe()
    ModelState.Ready -> "Ready" to "Verdicts are written by Gemma overnight."
    is ModelState.Failed -> "Didn't finish" to describe()
}

internal fun ModelState.describe(): String = when (this) {
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
