package com.halfbloodcoder.scrollsloth.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val timeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm:ss", Locale.getDefault())

@Composable
fun CapturesScreen(
    captures: List<CaptureSummary>,
    onBack: () -> Unit,
    onShare: (List<CaptureSummary>) -> Unit,
    onDelete: (CaptureSummary) -> Unit,
    onDeleteAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmDeleteAll by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Back") }
            Text("Rule Inspector captures", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            "Each capture holds screen structure only: view IDs, classes, bounds, and whether text exists. " +
                "No words, captions, or usernames are saved.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        if (captures.isEmpty()) {
            Text(
                "No captures yet. Turn on Inspector mode, open Instagram, YouTube, or Facebook, and tap Capture.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 16.dp),
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onShare(captures) }) { Text("Share all") }
                OutlinedButton(onClick = { confirmDeleteAll = true }) { Text("Delete all") }
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 8.dp),
            ) {
                items(captures, key = { it.fileName }) { capture ->
                    CaptureRow(
                        capture = capture,
                        onShare = { onShare(listOf(capture)) },
                        onDelete = { onDelete(capture) },
                    )
                }
            }
        }
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("Delete all captures?") },
            text = { Text("This removes every saved capture from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteAll = false
                    onDeleteAll()
                }) { Text("Delete all") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAll = false }) { Text("Keep them") }
            },
        )
    }
}

@Composable
private fun CaptureRow(capture: CaptureSummary, onShare: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(capture.platform.label, style = MaterialTheme.typography.titleMedium)
            Text(
                timeFormatter.format(Instant.ofEpochMilli(capture.capturedAtEpochMs).atZone(ZoneId.systemDefault())),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "${capture.fileName} · ${(capture.sizeBytes + 1023) / 1024} KB",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onShare) { Text("Share") }
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}
