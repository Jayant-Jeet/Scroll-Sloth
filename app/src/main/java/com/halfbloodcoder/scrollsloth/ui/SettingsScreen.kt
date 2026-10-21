package com.halfbloodcoder.scrollsloth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.WorkInfo
import com.halfbloodcoder.scrollsloth.R
import com.halfbloodcoder.scrollsloth.ai.ModelSpec
import com.halfbloodcoder.scrollsloth.ai.ModelState
import com.halfbloodcoder.scrollsloth.ai.Tone
import com.halfbloodcoder.scrollsloth.data.ThemeMode
import com.halfbloodcoder.scrollsloth.detect.AppStatus
import com.halfbloodcoder.scrollsloth.detect.DetectorEvent
import com.halfbloodcoder.scrollsloth.detect.LoadedRules
import com.halfbloodcoder.scrollsloth.detect.Platform
import com.halfbloodcoder.scrollsloth.detect.RulesAddress
import com.halfbloodcoder.scrollsloth.detect.RulesSource
import com.halfbloodcoder.scrollsloth.score.ScoreConfig
import com.halfbloodcoder.scrollsloth.ui.theme.SlothTheme
import com.halfbloodcoder.scrollsloth.ui.theme.slothText
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/** Read-only values for the Settings screen. */
data class SettingsUiState(
    val accessibilityServiceEnabled: Boolean,
    val trackingPaused: Boolean,
    val appStatuses: Map<Platform, AppStatus>,
    val tone: Tone,
    val themeMode: ThemeMode,
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
    val onThemeModeChange: (ThemeMode) -> Unit,
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
    val onResetOnboarding: () -> Unit,
)

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    var advancedExpanded by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    ScreenColumn(modifier, contentPadding) {
        BrandHeader("Your settings.")
        Section("Counting") { CountingCard(state, actions) }
        Section("Your preferences") { PreferencesCard(state, actions) }
        Section("On-device writer") {
            ModelCard(
                spec = state.modelSpec,
                state = state.modelState,
                onDownload = actions.onDownloadModel,
                onDelete = actions.onDeleteModel,
            )
            SlothCard {
                SwitchRow(
                    title = "Allow mobile data",
                    description = "Off means Wi-Fi only. Applies to the next download.",
                    checked = state.modelAllowMetered,
                    onCheckedChange = actions.onModelAllowMeteredChange,
                )
            }
        }
        Section("Detection rules") { RulesCard(state, actions.onCheckRules) }
        Section("Your data") { DataCard(state, actions.onExportCsv, onDeleteAll = { confirmDelete = true }) }
        AdvancedCard(state, actions, advancedExpanded) { advancedExpanded = !advancedExpanded }
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
                    colors = ButtonDefaults.textButtonColors(contentColor = SlothTheme.colors.destructive),
                ) { Text("Delete everything") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep it") } },
        )
    }
}

@Composable
private fun CountingCard(state: SettingsUiState, actions: SettingsActions) {
    val colors = SlothTheme.colors
    SlothCard {
        SwitchRow(
            title = "Pause counting",
            description = if (state.trackingPaused) {
                "Paused. Nothing is counted until you switch this off."
            } else {
                "Take a break from being judged. A reel already past two seconds still counts."
            },
            checked = state.trackingPaused,
            onCheckedChange = actions.onTrackingPausedChange,
        )
        SlothDivider()
        SettingRow(
            title = "Accessibility service",
            status = if (state.accessibilityServiceEnabled) "On" else "Off",
            statusColor = if (state.accessibilityServiceEnabled) colors.mintText else colors.destructive,
            description = if (state.accessibilityServiceEnabled) {
                "The sloth is watching, politely."
            } else {
                "Turn it on so Scroll Sloth can notice reels."
            },
        ) {
            ActionPill(
                if (state.accessibilityServiceEnabled) "Open" else "Turn on",
                R.drawable.ic_external_link,
                actions.onOpenAccessibilitySettings,
            )
        }
        SlothDivider()
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Apps", style = slothText(12.sp), color = colors.muted)
            Platform.entries.forEach { platform ->
                AppRow(platform, state.appStatuses[platform] ?: AppStatus.NEEDS_CALIBRATION)
            }
            if (AppStatus.NEEDS_CALIBRATION in state.appStatuses.values) {
                Text(
                    "Needs calibration means this version has no detection rules for the app yet. " +
                        "Import calibrated rules under Advanced, or wait for a rules update.",
                    style = slothText(12.sp, lineHeight = 1.5f),
                    color = colors.muted,
                )
            }
        }
    }
}

@Composable
private fun AppRow(platform: Platform, status: AppStatus) {
    val colors = SlothTheme.colors
    val (label, color, icon) = when (status) {
        AppStatus.WORKING -> Triple("Working", colors.mintText, R.drawable.ic_check)
        AppStatus.NEEDS_CALIBRATION -> Triple("Needs calibration", colors.lavenderText, null)
        AppStatus.PERMISSION_MISSING -> Triple("Permission missing", colors.destructive, null)
    }
    Row(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconBox(platform.icon, platform.color(), boxSize = 32.dp, iconSize = 18.dp, corner = 10.dp)
        Text(platform.label, style = slothText(13.sp), color = colors.text, modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (icon != null) SlothIcon(icon, color, 12.dp)
            Text(label, style = slothText(12.sp, FontWeight.Medium), color = color)
        }
    }
}

@Composable
private fun PreferencesCard(state: SettingsUiState, actions: SettingsActions) {
    SlothCard {
        SettingRow(
            title = "Appearance",
            description = when (state.themeMode) {
                ThemeMode.LIGHT -> "Cream and calm."
                ThemeMode.DARK -> "Easy on late-night eyes."
                ThemeMode.SYSTEM -> "Follows your phone."
            },
        ) {
            PillToggle(
                options = ThemeMode.entries,
                selected = state.themeMode,
                label = { mode ->
                    when (mode) {
                        ThemeMode.LIGHT -> "Light"
                        ThemeMode.DARK -> "Dark"
                        ThemeMode.SYSTEM -> "System"
                    }
                },
                onSelect = actions.onThemeModeChange,
                textSize = 11.sp,
                horizontalPadding = 12.dp,
                verticalPadding = 7.dp,
                modifier = Modifier.semantics { contentDescription = "Appearance" },
            )
        }
        SlothDivider()
        SettingRow(
            title = "Commentary tone",
            description = if (state.tone == Tone.GENTLE) "Kind nudges." else "Cheeky, never cruel.",
        ) {
            ToneToggle(state.tone, actions.onToneChange, textSize = 11.sp, horizontalPadding = 12.dp, verticalPadding = 7.dp)
        }
        SlothDivider()
        SettingRow(title = "Week starts on", description = "Used for the Week tab and weekly scores.") {
            WeekStartPicker(state.weekStart, actions.onWeekStartChange)
        }
    }
}

@Composable
private fun WeekStartPicker(weekStart: DayOfWeek, onWeekStartChange: (DayOfWeek) -> Unit) {
    val colors = SlothTheme.colors
    var open by remember { mutableStateOf(false) }
    Box {
        PressableBox(onClick = { open = true }, onClickLabel = "Change the first day of the week") { pressed ->
            Row(
                modifier = Modifier
                    .alpha(if (pressed) 0.7f else 1f)
                    .heightIn(min = 32.dp)
                    .background(colors.cardRaised, CircleShape)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(weekStart.displayName(), style = slothText(12.sp, FontWeight.Medium), color = colors.mintText)
                SlothIcon(R.drawable.ic_chevron_down, colors.mintText, 13.dp)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DayOfWeek.entries.forEach { day ->
                DropdownMenuItem(
                    text = { Text(day.displayName(), style = slothText(14.sp)) },
                    onClick = {
                        open = false
                        onWeekStartChange(day)
                    },
                )
            }
        }
    }
}

@Composable
private fun RulesCard(state: SettingsUiState, onCheckRules: () -> Unit) {
    val colors = SlothTheme.colors
    val rules = state.rules
    SlothCard(spacing = 12.dp) {
        LabelValue("Version", rules.rules.rulesVersion.toString())
        Text(
            when (rules.source) {
                RulesSource.BUNDLED -> "Bundled with the app."
                RulesSource.OVERRIDE -> "Imported from a file."
                RulesSource.DOWNLOADED -> "Downloaded from the rules server."
            },
            style = slothText(13.sp, lineHeight = 1.6f),
            color = colors.muted,
        )
        if (state.rulesUrl.isNotBlank()) {
            SlothDivider()
            LabelValue("Last checked", state.rulesLastCheckedMs?.let(::formatDateTime) ?: "Not yet")
            Text(
                "Checking downloads one public file. It sends nothing about you.",
                style = slothText(12.sp, lineHeight = 1.5f),
                color = colors.muted,
            )
            ActionPill(
                if (state.rulesChecking) "Checking…" else "Check now",
                R.drawable.ic_refresh,
                onCheckRules,
                enabled = !state.rulesChecking,
            )
        } else {
            Text(
                "Rule updates are off, so new rules come only from imported files. Set an address under Advanced.",
                style = slothText(12.sp, lineHeight = 1.5f),
                color = colors.muted,
            )
        }
        rules.warning?.let { Text(it, style = slothText(12.sp, lineHeight = 1.5f), color = colors.destructive) }
        state.rulesMessage?.let { Text(it, style = slothText(12.sp, lineHeight = 1.5f), color = colors.text) }
    }
}

@Composable
private fun LabelValue(label: String, value: String) {
    Row(modifier = Modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = slothText(13.sp), color = SlothTheme.colors.muted, modifier = Modifier.weight(1f))
        Text(value, style = slothText(14.sp, FontWeight.Medium), color = SlothTheme.colors.text)
    }
}

@Composable
private fun DataCard(state: SettingsUiState, onExport: () -> Unit, onDeleteAll: () -> Unit) {
    val colors = SlothTheme.colors
    SlothCard(spacing = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SlothIcon(R.drawable.ic_shield, colors.mintText, 16.dp)
            Text("On your phone. Only your phone.", style = slothText(13.sp, FontWeight.Medium), color = colors.mintText)
        }
        Text(
            "Stores only when a reel was watched, in which app, and for how long. It all stays on this phone.",
            style = slothText(13.sp, lineHeight = 1.6f),
            color = colors.muted,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionPill("Export CSV", R.drawable.ic_export, onExport, enabled = !state.dataBusy)
            ActionPill("Delete all data", R.drawable.ic_trash, onDeleteAll, color = colors.destructive, enabled = !state.dataBusy)
        }
        state.dataMessage?.let { Text(it, style = slothText(12.sp, lineHeight = 1.5f), color = colors.text) }
    }
}

@Composable
private fun AdvancedCard(state: SettingsUiState, actions: SettingsActions, expanded: Boolean, onToggle: () -> Unit) {
    val colors = SlothTheme.colors
    SlothCard(padding = PaddingValues(horizontal = 18.dp, vertical = 8.dp), spacing = 8.dp) {
        PressableBox(
            onClick = onToggle,
            onClickLabel = if (expanded) "Hide" else "Show",
            modifier = Modifier.fillMaxWidth(),
        ) { pressed ->
            Row(
                modifier = Modifier.fillMaxWidth().alpha(if (pressed) 0.7f else 1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Advanced", style = slothText(14.sp, FontWeight.Medium), color = colors.text)
                    Text(
                        "Scoring thresholds, commentary and detection-rule tools.",
                        style = slothText(12.sp, lineHeight = 1.5f),
                        color = colors.muted,
                    )
                }
                SlothIcon(
                    R.drawable.ic_chevron_down,
                    colors.muted,
                    18.dp,
                    modifier = Modifier.rotate(if (expanded) 180f else 0f),
                )
            }
        }
        if (expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                SlothDivider()
                ScoringSettings(config = state.scoreConfig, onSave = actions.onSaveScoreConfig, onReset = actions.onResetScoreConfig)
                WriteNowItem(state = state.writeNowState, onWriteNow = actions.onWriteNow)
                SlothDivider()
                SwitchRow(
                    title = "Inspector mode",
                    description = "Shows a floating Capture button. Each tap saves the screen structure of Instagram, " +
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
                SlothDivider()
                RulesAddressEditor(url = state.rulesUrl, isDefault = state.rulesUrlIsDefault, onSave = actions.onSaveRulesUrl)
                ClickRow("Import detection rules", "Load a calibrated detection-rules.json from your files", actions.onImportRules)
                if (state.rules.source != RulesSource.BUNDLED) {
                    ClickRow("Use bundled rules", "Forget the imported or downloaded rules file", actions.onResetRules)
                }
                ClickRow("Review onboarding", "See the welcome screen and setup guide again", actions.onResetOnboarding)
                SlothDivider()
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Live detector", style = slothText(14.sp, FontWeight.Medium), color = colors.text)
                    Text(state.liveEvent.describe(), style = slothText(12.sp, lineHeight = 1.5f), color = colors.muted)
                }
            }
        }
    }
}

/**
 * A settings row: title (with an optional coloured status word), a description under it, and a
 * control on the right.
 */
@Composable
internal fun SettingRow(
    title: String,
    description: String?,
    modifier: Modifier = Modifier,
    status: String? = null,
    statusColor: Color = SlothTheme.colors.mintText,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val colors = SlothTheme.colors
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = slothText(14.sp, FontWeight.Medium), color = colors.text)
                if (status != null) Text(status, style = slothText(11.sp, FontWeight.SemiBold), color = statusColor)
            }
            if (description != null) {
                Text(description, style = slothText(13.sp, lineHeight = 1.6f), color = colors.muted)
            }
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

/** A whole-row toggle with the design's pill switch. */
@Composable
internal fun SwitchRow(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    SettingRow(
        title = title,
        description = description,
        status = if (checked) "On" else "Off",
        statusColor = if (checked) SlothTheme.colors.mintText else SlothTheme.colors.muted,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
    ) {
        PillSwitch(checked)
    }
}

@Composable
private fun ClickRow(title: String, description: String, onClick: () -> Unit) {
    PressableBox(onClick = onClick, modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) { pressed ->
        SettingRow(title = title, description = description, modifier = Modifier.alpha(if (pressed) 0.7f else 1f)) {
            SlothIcon(R.drawable.ic_chevron_right, SlothTheme.colors.muted, 16.dp)
        }
    }
}

/** Where rule updates come from. Validated as https before it is saved; blank turns updates off. */
@Composable
private fun RulesAddressEditor(url: String, isDefault: Boolean, onSave: (String?) -> Unit) {
    var text by rememberSaveable(url) { mutableStateOf(url) }
    val problem = RulesAddress.problem(text)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Rules update address", style = slothText(14.sp, FontWeight.Medium), color = SlothTheme.colors.text)
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionPill("Save address", null, { onSave(text.trim()) }, enabled = problem == null && text.trim() != url)
            if (!isDefault) ActionPill("Use default", null, { onSave(null) })
        }
    }
}

private fun DayOfWeek.displayName(): String = getDisplayName(TextStyle.FULL, Locale.getDefault())

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
