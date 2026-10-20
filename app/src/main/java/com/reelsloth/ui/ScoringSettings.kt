package com.reelsloth.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.reelsloth.score.ScoreBands
import com.reelsloth.score.ScoreConfig
import com.reelsloth.score.ScoreConfigInput
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun WeekStartSetting(weekStart: DayOfWeek, onWeekStartChange: (DayOfWeek) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text("Week starts on") },
        supportingContent = { Text("Used for the Week tab and weekly scores") },
        trailingContent = {
            Box {
                OutlinedButton(onClick = { open = true }) { Text(weekStart.displayName()) }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    DayOfWeek.entries.forEach { day ->
                        DropdownMenuItem(
                            text = { Text(day.displayName()) },
                            onClick = {
                                open = false
                                onWeekStartChange(day)
                            },
                        )
                    }
                }
            }
        },
    )
}

/** Editor for [ScoreConfig]. Nothing is saved until the values parse. */
@Composable
fun ScoringSettings(config: ScoreConfig, onSave: (ScoreConfig) -> Unit, onReset: () -> Unit) {
    var fields by remember(config) { mutableStateOf(ScoreConfigInput.fieldsOf(config)) }
    var message by remember(config) { mutableStateOf<String?>(null) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Scoring", style = MaterialTheme.typography.titleMedium)
            Text(
                "These only change how scores are worked out. Your counts stay the same.",
                style = MaterialTheme.typography.bodySmall,
            )
            NumberField("Heavy day (reels)", fields.heavyDaily) { fields = fields.copy(heavyDaily = it) }
            NumberField("Daily minutes budget", fields.dailyMinutesBudget) { fields = fields.copy(dailyMinutesBudget = it) }
            NumberField("Heavy session (reels)", fields.sessionHeavy) { fields = fields.copy(sessionHeavy = it) }
            NumberField("Late-night share that maxes out (%)", fields.lateSharePercent, decimal = true) {
                fields = fields.copy(lateSharePercent = it)
            }
            Text("Band labels", style = MaterialTheme.typography.titleSmall)
            ScoreBands().bands.forEach { band ->
                OutlinedTextField(
                    value = fields.bandLabels[band.index],
                    onValueChange = { value ->
                        fields = fields.copy(bandLabels = fields.bandLabels.toMutableList().also { it[band.index] = value })
                    },
                    label = { Text("${band.min} to ${band.max}") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            message?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    when (val result = ScoreConfigInput.parse(fields)) {
                        is ScoreConfigInput.Result.Valid -> {
                            message = null
                            onSave(result.config)
                        }
                        is ScoreConfigInput.Result.Invalid -> message = result.message
                    }
                }) { Text("Save scoring") }
                OutlinedButton(onClick = {
                    fields = ScoreConfigInput.fieldsOf(ScoreConfig())
                    message = null
                    onReset()
                }) { Text("Reset to defaults") }
            }
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, decimal: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun DayOfWeek.displayName(): String = getDisplayName(TextStyle.FULL, Locale.getDefault())
