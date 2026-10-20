package com.reelsloth.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import com.reelsloth.ai.ModelSpec
import com.reelsloth.ai.ModelState
import com.reelsloth.ai.Tone
import com.reelsloth.detect.AppStatus
import com.reelsloth.detect.DetectorEvent
import com.reelsloth.detect.LoadedRules
import com.reelsloth.detect.Platform
import com.reelsloth.detect.RulesAddress
import com.reelsloth.detect.RulesSource
import com.reelsloth.score.ScoreConfig
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Read-only values for the Settings screen. */
data class SettingsUiState(
    val accessibilityServiceEnabled: Boolean,
    val trackingPaused: Boolean,
    val appStatuses: Map<Platform, AppStatus>,
    val tone: Tone,
    val weekStart: DayOfWeek,
    val modelSpec: ModelSpec,
    val modelState: ModelState,
    val modelAllowMetered: Boolean,
    val rules: LoadedRules,
    /** Address rule updates come from; blank means updates are off. */
    val rulesUrl: String,
    val rulesUrlIsDefault: Boolean,
    val rulesLastCheckedMs: Long?,
    val rulesChecking: Boolean,
    val rulesMessage: String?,
    val dataMessage: String?,
    val dataBusy: Boolean,
    val scoreConfig: ScoreConfig,
    val writeNowState: WorkInfo.State?,
    val inspectorEnabled: Boolean,
    val liveEvent: DetectorEvent?,
)

/** Everything the Settings screen can ask for. */
data class SettingsActions(
    val onTrackingPausedChange: (Boolean) -> Unit,
    val onOpenAccessibilitySettings: () -> Unit,
    val onToneChange: (Tone) -> Unit,
    val onWeekStartChange: (DayOfWeek) -> Unit,
    val onModelAllowMeteredChange: (Boolean) -> Unit,
    val onDownloadModel: () -> Unit,
    val onDeleteModel: () -> Unit,
    val onCheckRules: () -> Unit,
    val onExportCsv: () -> Unit,
    val onDeleteAllData: () -> Unit,
    val onSaveScoreConfig: (ScoreConfig) -> Unit,
    val onResetScoreConfig: () -> Unit,
    val onWriteNow: () -> Unit,
    val onInspectorEnabledChange: (Boolean) -> Unit,
    val onOpenCaptures: () -> Unit,
    val onImportRules: () -> Unit,
    val onResetRules: () -> Unit,
    /** Null means "use the build default". */
    val onSaveRulesUrl: (String?) -> Unit,
    val onOpenCalibration: () -> Unit,
)

@Composable
fun SettingsScreen(state: SettingsUiState, actions: SettingsActions, modifier: Modifier = Modifier) {
    var advancedExpanded by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenTitle("Settings")

        TrackingCard(state, actions)
        AppsCard(state.appStatuses, state.accessibilityServiceEnabled, actions.onOpenAccessibilitySettings)
        ToneSetting(tone = state.tone, onToneChange = actions.onToneChange)
        WeekStartSetting(weekStart = state.weekStart, onWeekStartChange = actions.onWeekStartChange)
        ModelCard(
            spec = state.modelSpec,
            state = state.modelState,
            allowMetered = state.modelAllowMetered,
            onAllowMeteredChange = actions.onModelAllowMeteredChange,
            onDownload = actions.onDownloadModel,
            onDelete = actions.onDeleteModel,
        )
        RulesCard(state, actions.onCheckRules)
        DataCard(state, actions.onExportCsv, onDeleteAll = { confirmDelete = true })

        ListItem(
            headlineContent = { Text("Advanced") },
            supportingContent = { Text("Scoring thresholds, commentary and detection-rule tools") },
            trailingContent = {
                Icon(
                    if (advancedExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                )
            },
            modifier = Modifier
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClickLabel = if (advancedExpanded) "Hide" else "Show") {
                    advancedExpanded = !advancedExpanded
                },
        )

        if (advancedExpanded) {
            HorizontalDivider()
            ScoringSettings(config = state.scoreConfig, onSave = actions.onSaveScoreConfig, onReset = actions.onResetScoreConfig)
            WriteNowItem(state = state.writeNowState, onWriteNow = actions.onWriteNow)
            SwitchRow(
                title = "Inspector mode",
                body = "Shows a floating Capture button. Each tap saves the screen structure of Instagram, " +
                    "YouTube, or Facebook, never the words on it." +
                    if (state.accessibilityServiceEnabled) "" else " Needs the accessibility service on.",
                checked = state.inspectorEnabled,
                onCheckedChange = actions.onInspectorEnabledChange,
            )
            ClickRow("Rule Inspector captures", "Share or delete saved captures", actions.onOpenCaptures)
            ClickRow(
                "Calibration",
                "Count reels by hand next to the detector and see how far apart you are",
                actions.onOpenCalibration,
            )
            RulesAddressEditor(url = state.rulesUrl, isDefault = state.rulesUrlIsDefault, onSave = actions.onSaveRulesUrl)
            ClickRow("Import detection rules", "Load a calibrated detection-rules.json from your files", actions.onImportRules)
            if (state.rules.source != RulesSource.BUNDLED) {
                ClickRow("Use bundled rules", "Forget the imported or downloaded rules file", actions.onResetRules)
            }
            ListItem(
                headlineContent = { Text("Live detector") },
                supportingContent = { Text(state.liveEvent.describe()) },
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete all data?") },
            text = {
                Text(
                    "This removes every counted reel, all commentary, Inspector captures and calibration runs from this phone. " +
                        "Your settings, rules and the downloaded model stay. This cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        actions.onDeleteAllData()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete everything") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep it") } },
        )
    }
}

@Composable
private fun TrackingCard(state: SettingsUiState, actions: SettingsActions) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            SectionTitle("Counting", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            SwitchRow(
                title = "Pause counting",
                body = if (state.trackingPaused) {
                    "Paused. Nothing is counted until you switch this off."
                } else {
                    "Take a break from being judged. A reel already past two seconds still counts."
                },
                checked = state.trackingPaused,
                onCheckedChange = actions.onTrackingPausedChange,
            )
            ListItem(
                headlineContent = { Text("Accessibility service") },
                supportingContent = {
                    Text(
                        if (state.accessibilityServiceEnabled) {
                            "On. The sloth is watching, politely."
                        } else {
                            "Off. Turn it on so Scroll Sloth can notice reels."
                        },
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                trailingContent = {
                    OutlinedButton(onClick = actions.onOpenAccessibilitySettings) {
                        Text(if (state.accessibilityServiceEnabled) "Open" else "Turn on")
                    }
                },
            )
        }
    }
}

@Composable
private fun AppsCard(statuses: Map<Platform, AppStatus>, serviceEnabled: Boolean, onOpenAccessibilitySettings: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("Apps")
            Platform.entries.forEach { platform ->
                val status = statuses[platform] ?: AppStatus.NEEDS_CALIBRATION
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(platform.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    StatusPill(status)
                }
            }
            val statusValues = statuses.values
            when {
                AppStatus.NEEDS_CALIBRATION in statusValues -> Text(
                    "Needs calibration means this version has no detection rules for the app yet. " +
                        "Import calibrated rules under Advanced, or wait for a rules update.",
                    style = MaterialTheme.typography.bodySmall,
                )
                !serviceEnabled -> TextButton(onClick = onOpenAccessibilitySettings) { Text("Grant the permission") }
            }
        }
    }
}

@Composable
private fun StatusPill(status: AppStatus) {
    val colors = MaterialTheme.colorScheme
    val (label, container, content) = when (status) {
        AppStatus.WORKING -> Triple("Working", colors.primaryContainer, colors.onPrimaryContainer)
        AppStatus.NEEDS_CALIBRATION -> Triple("Needs calibration", colors.tertiaryContainer, colors.onTertiaryContainer)
        AppStatus.PERMISSION_MISSING -> Triple("Permission missing", colors.errorContainer, colors.onErrorContainer)
    }
    Surface(shape = CircleShape, color = container, contentColor = content) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}

@Composable
private fun RulesCard(state: SettingsUiState, onCheckRules: () -> Unit) {
    val rules = state.rules
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("Detection rules")
            Text(
                "Version ${rules.rules.rulesVersion}, " + when (rules.source) {
                    RulesSource.BUNDLED -> "bundled with the app."
                    RulesSource.OVERRIDE -> "imported from a file."
                    RulesSource.DOWNLOADED -> "downloaded from the rules server."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (state.rulesUrl.isNotBlank()) {
                Text(
                    state.rulesLastCheckedMs?.let { "Last checked ${formatDateTime(it)}." } ?: "Not checked yet.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Checking downloads one public file. It sends nothing about you.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = onCheckRules, enabled = !state.rulesChecking) {
                    Text(if (state.rulesChecking) "Checking…" else "Check now")
                }
            } else {
                Text(
                    "Rule updates are off, so new rules come only from imported files. Set an address under Advanced.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            rules.warning?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            state.rulesMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun DataCard(state: SettingsUiState, onExport: () -> Unit, onDeleteAll: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("Your data")
            Text(
                "Scroll Sloth stores only when a reel was watched, in which app, and for how long. It stays on this phone.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onExport, enabled = !state.dataBusy) { Text("Export CSV") }
                OutlinedButton(
                    onClick = onDeleteAll,
                    enabled = !state.dataBusy,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete all data") }
            }
            state.dataMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun SwitchRow(title: String, body: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(body) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
    )
}

@Composable
private fun ClickRow(title: String, body: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(body) },
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
    )
}

/** Where rule updates come from. Validated as https before it is saved; blank turns updates off. */
@Composable
private fun RulesAddressEditor(url: String, isDefault: Boolean, onSave: (String?) -> Unit) {
    var text by rememberSaveable(url) { mutableStateOf(url) }
    val problem = RulesAddress.problem(text)
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Rules update address", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            isError = problem != null,
            label = { Text("https:// address of detection-rules.json") },
            supportingText = {
                Text(
                    problem ?: when {
                        text.isBlank() -> "Empty turns weekly updates off."
                        isDefault && text == url -> "The default address."
                        else -> "Checked weekly, and when you tap Check now."
                    },
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { onSave(text.trim()) }, enabled = problem == null && text.trim() != url) {
                Text("Save address")
            }
            if (!isDefault) TextButton(onClick = { onSave(null) }) { Text("Use default") }
        }
    }
}

private fun formatDateTime(ms: Long): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(Locale.getDefault())
        .format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

/** Shows only flags, an index, and a short slice of the in-memory hash. Never reel content. */
private fun DetectorEvent?.describe(): String {
    if (this == null) return "Nothing seen yet. Open a supported app."
    val where = if (onReelScreen) "on a reel screen" else "not on a reel screen"
    val identity = when {
        pagerIndex != null -> ", page $pagerIndex"
        reelKeyHash != null -> ", key ${(reelKeyHash and 0xFFFF).toString(16)}"
        else -> ""
    }
    return "${packageName.substringAfterLast('.')}: $where$identity"
}
