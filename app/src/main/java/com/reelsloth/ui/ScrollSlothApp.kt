package com.reelsloth.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.reelsloth.AppContainer
import com.reelsloth.ai.Tone
import com.reelsloth.debug.CaptureSharer
import com.reelsloth.debug.CaptureSummary
import com.reelsloth.debug.CapturesScreen
import com.reelsloth.detect.AppStatuses
import com.reelsloth.detect.RulesRepository
import com.reelsloth.detect.RulesUpdater
import com.reelsloth.score.ScoreConfig
import com.reelsloth.score.ScoreEngine
import com.reelsloth.stats.Period
import com.reelsloth.stats.PeriodCharts
import com.reelsloth.work.NightlyCommentaryWorker
import java.io.File
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Destination(val label: String, val icon: ImageVector) {
    TODAY("Today", Icons.Filled.Home),
    WEEK("Week", BarsIcon),
    MONTH("Month", Icons.Filled.DateRange),
    SETTINGS("Settings", Icons.Filled.Settings),
}

@Composable
fun ScrollSlothApp(
    container: AppContainer,
    accessibilityServiceEnabled: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onShareCaptures: (List<CaptureSummary>) -> Unit,
) {
    var selectedDestination by rememberSaveable { mutableStateOf(Destination.TODAY) }
    var showCaptures by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = selectedDestination == destination,
                        onClick = {
                            selectedDestination = destination
                            showCaptures = false
                        },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { contentPadding ->
        val modifier = Modifier.padding(contentPadding)
        when (selectedDestination) {
            Destination.TODAY -> TodayRoute(container, accessibilityServiceEnabled, onOpenAccessibilitySettings, modifier)
            Destination.WEEK -> WeekRoute(container, accessibilityServiceEnabled, onOpenAccessibilitySettings, modifier)
            Destination.MONTH -> MonthRoute(container, accessibilityServiceEnabled, onOpenAccessibilitySettings, modifier)
            Destination.SETTINGS -> if (showCaptures) {
                CapturesRoute(container, onBack = { showCaptures = false }, onShare = onShareCaptures, modifier = modifier)
            } else {
                SettingsRoute(
                    container = container,
                    accessibilityServiceEnabled = accessibilityServiceEnabled,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                    onOpenCaptures = { showCaptures = true },
                    modifier = modifier,
                )
            }
        }
    }
}

@Composable
private fun trackingState(container: AppContainer, permissionGranted: Boolean): TrackingState {
    val paused by container.settingsStore.trackingPaused.collectAsState(initial = false)
    val rules by container.rulesRepository.rules.collectAsState()
    return TrackingState(permissionGranted, paused, rules.enabledApps.isNotEmpty())
}

@Composable
private fun trackingStart(container: AppContainer): LocalDate? {
    val start by remember { container.statsEngine.trackingStart() }.collectAsState(initial = null)
    return start
}

@Composable
private fun TodayRoute(container: AppContainer, permissionGranted: Boolean, onOpenAccessibilitySettings: () -> Unit, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    val stats = container.statsEngine
    val report by remember { stats.report(Period.DAY) }.collectAsState(initial = null)
    val previous by remember { stats.previous(Period.DAY) }.collectAsState(initial = null)
    val config by container.settingsStore.scoreConfig.collectAsState(initial = ScoreConfig())
    val verdict by remember { container.commentaryRepository.verdict(Period.DAY) }.collectAsState(initial = null)
    val tone by container.settingsStore.commentaryTone.collectAsState(initial = Tone.GENTLE)
    val modelState by container.modelDownloader.state.collectAsState()
    val scores = remember(report, config) { report?.let { ScoreEngine(config).scoreDays(it.days) } }

    TodayScreen(
        state = TodayUiState(
            tracking = trackingState(container, permissionGranted),
            report = report,
            previous = previous,
            scores = scores,
            config = config,
            verdict = verdict,
            tone = tone,
            modelState = modelState,
        ),
        onToneChange = { scope.launch { container.settingsStore.setCommentaryTone(it) } },
        onOpenAccessibilitySettings = onOpenAccessibilitySettings,
        modifier = modifier,
    )
}

@Composable
private fun WeekRoute(container: AppContainer, permissionGranted: Boolean, onOpenAccessibilitySettings: () -> Unit, modifier: Modifier) {
    val zone = remember { ZoneId.systemDefault() }
    val report by remember { container.statsEngine.report(Period.WEEK) }.collectAsState(initial = null)
    val config by container.settingsStore.scoreConfig.collectAsState(initial = ScoreConfig())
    val verdict by remember { container.commentaryRepository.verdict(Period.WEEK) }.collectAsState(initial = null)
    val modelState by container.modelDownloader.state.collectAsState()
    val start = trackingStart(container)
    val scores = remember(report, config) { report?.let { ScoreEngine(config).scoreDays(it.days) } }
    val chart = remember(report, start) {
        report?.let { r -> PeriodCharts.week(r, r.lastDay(zone) ?: LocalDate.now(zone), start, zone) }
    }

    WeekScreen(
        state = WeekUiState(trackingState(container, permissionGranted), report, chart, scores, config, verdict, modelState),
        onOpenAccessibilitySettings = onOpenAccessibilitySettings,
        modifier = modifier,
    )
}

@Composable
private fun MonthRoute(container: AppContainer, permissionGranted: Boolean, onOpenAccessibilitySettings: () -> Unit, modifier: Modifier) {
    val zone = remember { ZoneId.systemDefault() }
    val report by remember { container.statsEngine.report(Period.MONTH) }.collectAsState(initial = null)
    val config by container.settingsStore.scoreConfig.collectAsState(initial = ScoreConfig())
    val weekStart by container.settingsStore.weekStart.collectAsState(initial = DayOfWeek.MONDAY)
    val verdict by remember { container.commentaryRepository.verdict(Period.MONTH) }.collectAsState(initial = null)
    val modelState by container.modelDownloader.state.collectAsState()
    val start = trackingStart(container)
    val scores = remember(report, config) { report?.let { ScoreEngine(config).scoreDays(it.days) } }
    val chart = remember(report, start, weekStart, config) {
        report?.let { r ->
            PeriodCharts.month(r, r.lastDay(zone) ?: LocalDate.now(zone), start, weekStart, config.heavyDaily, zone)
        }
    }

    MonthScreen(
        state = MonthUiState(trackingState(container, permissionGranted), report, chart, scores, config, verdict, modelState),
        onOpenAccessibilitySettings = onOpenAccessibilitySettings,
        modifier = modifier,
    )
}

@Composable
private fun SettingsRoute(
    container: AppContainer,
    accessibilityServiceEnabled: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenCaptures: () -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = container.settingsStore
    val repository = container.rulesRepository
    val rules by repository.rules.collectAsState()
    val paused by settings.trackingPaused.collectAsState(initial = false)
    val inspectorEnabled by settings.inspectorMode.collectAsState(initial = false)
    val liveEvent by container.reelDetector.lastEvent.collectAsState()
    val weekStart by settings.weekStart.collectAsState(initial = DayOfWeek.MONDAY)
    val scoreConfig by settings.scoreConfig.collectAsState(initial = ScoreConfig())
    val tone by settings.commentaryTone.collectAsState(initial = Tone.GENTLE)
    val modelState by container.modelDownloader.state.collectAsState()
    val allowMetered by settings.modelAllowMetered.collectAsState(initial = false)
    val rulesLastChecked by settings.rulesLastCheckedMs.collectAsState(initial = null)
    val writeNowState by remember { NightlyCommentaryWorker.runNowState(context) }.collectAsState(initial = null)
    var rulesMessage by remember { mutableStateOf<String?>(null) }
    var rulesChecking by remember { mutableStateOf(false) }
    var dataMessage by remember { mutableStateOf<String?>(null) }
    var dataBusy by remember { mutableStateOf(false) }

    // Picks up a rules file copied in while the app was in the background.
    LaunchedEffect(repository) { repository.reload() }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.readNBytesCompat(MAX_RULES_BYTES)?.toString(Charsets.UTF_8)
                    }
                } catch (_: IOException) {
                    null
                } catch (_: SecurityException) {
                    null
                }
            }
            rulesMessage = when {
                text == null -> "Could not read that file, or it is too big to be a rules file."
                else -> when (val result = repository.importOverride(text)) {
                    is RulesRepository.ImportResult.Imported -> "Imported rules version ${result.rules.rules.rulesVersion}."
                    is RulesRepository.ImportResult.Rejected -> result.reason
                }
            }
        }
    }

    SettingsScreen(
        state = SettingsUiState(
            accessibilityServiceEnabled = accessibilityServiceEnabled,
            trackingPaused = paused,
            appStatuses = AppStatuses.of(rules, accessibilityServiceEnabled),
            tone = tone,
            weekStart = weekStart,
            modelSpec = container.modelDownloader.spec,
            modelState = modelState,
            modelAllowMetered = allowMetered,
            rules = rules,
            rulesServerConfigured = container.rulesUpdater.isConfigured,
            rulesLastCheckedMs = rulesLastChecked,
            rulesChecking = rulesChecking,
            rulesMessage = rulesMessage,
            dataMessage = dataMessage,
            dataBusy = dataBusy,
            scoreConfig = scoreConfig,
            writeNowState = writeNowState,
            inspectorEnabled = inspectorEnabled,
            liveEvent = liveEvent,
        ),
        actions = SettingsActions(
            onTrackingPausedChange = { scope.launch { settings.setTrackingPaused(it) } },
            onOpenAccessibilitySettings = onOpenAccessibilitySettings,
            onToneChange = { scope.launch { settings.setCommentaryTone(it) } },
            onWeekStartChange = { scope.launch { settings.setWeekStart(it) } },
            onModelAllowMeteredChange = { scope.launch { settings.setModelAllowMetered(it) } },
            // App scope: the download must not be cancelled when the user leaves Settings.
            onDownloadModel = { container.appScope.launch { container.modelDownloader.download() } },
            onDeleteModel = { container.appScope.launch { container.modelDownloader.delete() } },
            onCheckRules = {
                rulesChecking = true
                scope.launch {
                    val result = container.appScope.async { container.rulesUpdater.check() }.await()
                    rulesMessage = result.message()
                    rulesChecking = false
                }
            },
            onExportCsv = {
                dataBusy = true
                scope.launch {
                    dataMessage = try {
                        shareCsv(context, container.userData.exportCsv())
                        null
                    } catch (_: IOException) {
                        "Could not write the export. Is the phone out of space?"
                    }
                    dataBusy = false
                }
            },
            onDeleteAllData = {
                dataBusy = true
                scope.launch {
                    container.appScope.async { container.userData.eraseAll() }.await()
                    dataMessage = "All counted reels and commentary are gone. Fresh start."
                    dataBusy = false
                }
            },
            onSaveScoreConfig = { scope.launch { settings.setScoreConfig(it) } },
            onResetScoreConfig = { scope.launch { settings.resetScoreConfig() } },
            onWriteNow = { NightlyCommentaryWorker.runNow(context) },
            onInspectorEnabledChange = { scope.launch { settings.setInspectorMode(it) } },
            onOpenCaptures = onOpenCaptures,
            onImportRules = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
            onResetRules = {
                scope.launch {
                    repository.resetToBundled()
                    rulesMessage = "Back to the bundled rules."
                }
            },
        ),
        modifier = modifier,
    )
}

private fun RulesUpdater.CheckResult.message(): String = when (this) {
    RulesUpdater.CheckResult.NotConfigured -> "This build has no rules server set."
    is RulesUpdater.CheckResult.UpToDate -> "Already up to date (version $version)."
    is RulesUpdater.CheckResult.Updated -> "Updated to rules version $version."
    is RulesUpdater.CheckResult.Failed -> reason
}

/** Hands the CSV to the share sheet. Nothing leaves the phone unless the user picks a target. */
private fun shareCsv(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, CaptureSharer.authority(context), file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(null, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Export reels"))
}

/** Three rising bars, for the Week tab. */
private val BarsIcon: ImageVector by lazy {
    ImageVector.Builder(name = "Bars", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .path(fill = SolidColor(Color.Black)) {
            moveTo(4f, 13f); lineTo(8f, 13f); lineTo(8f, 20f); lineTo(4f, 20f); close()
            moveTo(10f, 8f); lineTo(14f, 8f); lineTo(14f, 20f); lineTo(10f, 20f); close()
            moveTo(16f, 4f); lineTo(20f, 4f); lineTo(20f, 20f); lineTo(16f, 20f); close()
        }
        .build()
}

/** Reads at most [limit] bytes; returns null if the stream is longer. */
private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray? {
    val bytes = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    while (true) {
        val read = read(buffer)
        if (read < 0) return bytes.toByteArray()
        if (bytes.size() + read > limit) return null
        bytes.write(buffer, 0, read)
    }
}

private const val MAX_RULES_BYTES = 256 * 1024

@Composable
private fun CapturesRoute(
    container: AppContainer,
    onBack: () -> Unit,
    onShare: (List<CaptureSummary>) -> Unit,
    modifier: Modifier,
) {
    val repository = container.captureRepository
    val captures by repository.captures.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(repository) { repository.refresh() }
    BackHandler(onBack = onBack)

    CapturesScreen(
        captures = captures,
        onBack = onBack,
        onShare = onShare,
        onDelete = { capture -> scope.launch { repository.delete(capture.fileName) } },
        onDeleteAll = { scope.launch { repository.deleteAll() } },
        modifier = modifier,
    )
}
