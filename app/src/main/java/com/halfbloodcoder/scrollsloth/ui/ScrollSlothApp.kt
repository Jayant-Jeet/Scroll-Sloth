package com.halfbloodcoder.scrollsloth.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.halfbloodcoder.scrollsloth.AppContainer
import com.halfbloodcoder.scrollsloth.BuildConfig
import com.halfbloodcoder.scrollsloth.R
import com.halfbloodcoder.scrollsloth.data.ThemeMode
import com.halfbloodcoder.scrollsloth.data.UserData
import com.halfbloodcoder.scrollsloth.ui.theme.SlothTheme
import com.halfbloodcoder.scrollsloth.ui.theme.slothText
import com.halfbloodcoder.scrollsloth.debug.CalibrationActions
import com.halfbloodcoder.scrollsloth.debug.CalibrationScreen
import com.halfbloodcoder.scrollsloth.debug.CalibrationState
import com.halfbloodcoder.scrollsloth.debug.CalibrationUiState
import com.halfbloodcoder.scrollsloth.detect.Platform
import com.halfbloodcoder.scrollsloth.ai.Tone
import com.halfbloodcoder.scrollsloth.debug.CaptureSharer
import com.halfbloodcoder.scrollsloth.debug.CaptureSummary
import com.halfbloodcoder.scrollsloth.debug.CapturesScreen
import com.halfbloodcoder.scrollsloth.detect.AppStatuses
import com.halfbloodcoder.scrollsloth.detect.RulesRepository
import com.halfbloodcoder.scrollsloth.detect.RulesUpdater
import com.halfbloodcoder.scrollsloth.score.ScoreConfig
import com.halfbloodcoder.scrollsloth.score.ScoreEngine
import com.halfbloodcoder.scrollsloth.stats.Period
import com.halfbloodcoder.scrollsloth.stats.PeriodCharts
import com.halfbloodcoder.scrollsloth.work.NightlyCommentaryWorker
import java.io.File
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class SettingsPage { MAIN, CAPTURES, CALIBRATION }

private enum class Destination(val label: String, @DrawableRes val icon: Int) {
    TODAY("Today", R.drawable.ic_nav_today),
    WEEK("Week", R.drawable.ic_nav_week),
    MONTH("Month", R.drawable.ic_nav_month),
    SETTINGS("Settings", R.drawable.ic_nav_settings),
}

@Composable
fun ScrollSlothApp(
    container: AppContainer,
    accessibilityServiceEnabled: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onShareCaptures: (List<CaptureSummary>) -> Unit,
) {
    var selectedDestination by rememberSaveable { mutableStateOf(Destination.TODAY) }
    var settingsPage by rememberSaveable { mutableStateOf(SettingsPage.MAIN) }

    Scaffold(
        containerColor = SlothTheme.colors.background,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        bottomBar = {
            SlothBottomNav(selectedDestination) { destination ->
                selectedDestination = destination
                settingsPage = SettingsPage.MAIN
            }
        },
    ) { contentPadding ->
        val modifier = Modifier.padding(contentPadding)
        when (selectedDestination) {
            Destination.TODAY -> TodayRoute(container, accessibilityServiceEnabled, onOpenAccessibilitySettings, modifier)
            Destination.WEEK -> WeekRoute(container, accessibilityServiceEnabled, onOpenAccessibilitySettings, modifier)
            Destination.MONTH -> MonthRoute(container, accessibilityServiceEnabled, onOpenAccessibilitySettings, modifier)
            Destination.SETTINGS -> when (settingsPage) {
                SettingsPage.CAPTURES ->
                    CapturesRoute(container, onBack = { settingsPage = SettingsPage.MAIN }, onShare = onShareCaptures, modifier = modifier)
                SettingsPage.CALIBRATION ->
                    CalibrationRoute(container, accessibilityServiceEnabled, onBack = { settingsPage = SettingsPage.MAIN }, modifier = modifier)
                SettingsPage.MAIN -> SettingsRoute(
                    container = container,
                    accessibilityServiceEnabled = accessibilityServiceEnabled,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                    onOpenCaptures = { settingsPage = SettingsPage.CAPTURES },
                    onOpenCalibration = { settingsPage = SettingsPage.CALIBRATION },
                    modifier = modifier,
                )
            }
        }
    }
}

@Composable
private fun SlothBottomNav(selected: Destination, onSelect: (Destination) -> Unit) {
    val colors = SlothTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.nav)
            .testTag(Tags.BOTTOM_NAV),
    ) {
        SlothDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Destination.entries.forEach { destination ->
                val isSelected = destination == selected
                val tint = if (isSelected) colors.mintText else colors.muted
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSelected) colors.cardRaised else Color.Transparent)
                        .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(destination) })
                        .heightIn(min = 48.dp)
                        .padding(vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
                ) {
                    SlothIcon(destination.icon, tint, 21.dp)
                    Text(
                        destination.label,
                        style = slothText(11.sp, if (isSelected) FontWeight.SemiBold else FontWeight.Medium),
                        color = tint,
                        maxLines = 1,
                    )
                }
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
    onOpenCalibration: () -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val rulesUrl by container.rulesUrl.collectAsState(initial = BuildConfig.RULES_URL)
    val rulesUrlOverride by container.settingsStore.rulesUrlOverride.collectAsState(initial = null)
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
    val themeMode by settings.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
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
            themeMode = themeMode,
            weekStart = weekStart,
            modelSpec = container.modelDownloader.spec,
            modelState = modelState,
            modelAllowMetered = allowMetered,
            rules = rules,
            rulesUrl = rulesUrl,
            rulesUrlIsDefault = rulesUrlOverride == null,
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
            onThemeModeChange = { scope.launch { settings.setThemeMode(it) } },
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
                        shareCsv(context, container.userData.exportCsv(), "Export reels")
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
            onSaveRulesUrl = { url ->
                scope.launch {
                    settings.setRulesUrlOverride(url)
                    rulesMessage = when {
                        url == null -> "Using the default rules address."
                        url.isBlank() -> "Rule updates are off."
                        else -> "Rules address saved."
                    }
                }
            },
            onOpenCalibration = onOpenCalibration,
            onResetOnboarding = { scope.launch { settings.setOnboardingComplete(false) } },
        ),
        modifier = modifier,
    )
}

private fun RulesUpdater.CheckResult.message(): String = when (this) {
    RulesUpdater.CheckResult.NotConfigured -> "Rule updates are off."
    is RulesUpdater.CheckResult.UpToDate -> "Already up to date (version $version)."
    is RulesUpdater.CheckResult.Updated -> "Updated to rules version $version."
    is RulesUpdater.CheckResult.Failed -> reason
}

/** Hands the CSV to the share sheet. Nothing leaves the phone unless the user picks a target. */
private fun shareCsv(context: Context, file: File, title: String) {
    val uri = FileProvider.getUriForFile(context, CaptureSharer.authority(context), file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(null, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, title))
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

@Composable
private fun CalibrationRoute(
    container: AppContainer,
    accessibilityServiceEnabled: Boolean,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session = container.calibrationSession
    val log = container.calibrationLog
    val sessionState by session.state.collectAsState()
    val rules by container.rulesRepository.rules.collectAsState()
    val paused by container.settingsStore.trackingPaused.collectAsState(initial = false)
    var selected by rememberSaveable {
        mutableStateOf((sessionState as? CalibrationState.Running)?.platform ?: Platform.INSTAGRAM)
    }
    var loggedRuns by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(log) { loggedRuns = log.count() }
    BackHandler(onBack = onBack)

    CalibrationScreen(
        state = CalibrationUiState(
            selectedPlatform = selected,
            session = sessionState,
            appStatuses = AppStatuses.of(rules, accessibilityServiceEnabled),
            trackingPaused = paused,
            loggedRuns = loggedRuns,
            busy = busy,
            message = message,
        ),
        actions = CalibrationActions(
            onBack = onBack,
            onSelectPlatform = { selected = it },
            onStart = {
                busy = true
                message = null
                scope.launch {
                    session.start(selected)
                    busy = false
                }
            },
            onPlusOne = session::plusOne,
            onMinusOne = session::minusOne,
            onStop = {
                busy = true
                scope.launch {
                    // App scope: the row must be written even if the user leaves this screen.
                    message = container.appScope.async {
                        val result = session.stop() ?: return@async null
                        try {
                            log.append(result)
                            null
                        } catch (_: IOException) {
                            "Could not save this run to the log."
                        }
                    }.await()
                    loggedRuns = log.count()
                    busy = false
                }
            },
            onCancel = {
                session.cancel()
                message = "Run discarded."
            },
            onExport = {
                busy = true
                scope.launch {
                    message = try {
                        val file = log.exportTo(File(context.cacheDir, UserData.EXPORT_DIRECTORY))
                        if (file == null) "Nothing logged yet." else {
                            shareCsv(context, file, "Export calibration")
                            null
                        }
                    } catch (_: IOException) {
                        "Could not write the export. Is the phone out of space?"
                    }
                    busy = false
                }
            },
        ),
        modifier = modifier,
    )
}
