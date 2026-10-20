package com.halfbloodcoder.scrollsloth

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.preferencesDataStore
import androidx.room3.Room
import com.halfbloodcoder.scrollsloth.ai.AndroidDownloads
import com.halfbloodcoder.scrollsloth.ai.CommentaryRepository
import com.halfbloodcoder.scrollsloth.ai.GemmaCommentaryEngine
import com.halfbloodcoder.scrollsloth.ai.LiteRtLanguageModel
import com.halfbloodcoder.scrollsloth.ai.ModelDownloader
import com.halfbloodcoder.scrollsloth.ai.ModelFiles
import com.halfbloodcoder.scrollsloth.ai.ModelSpec
import com.halfbloodcoder.scrollsloth.ai.TemplateCommentaryEngine
import com.halfbloodcoder.scrollsloth.data.AppDatabase
import com.halfbloodcoder.scrollsloth.data.ReelEventWriter
import com.halfbloodcoder.scrollsloth.data.SettingsStore
import com.halfbloodcoder.scrollsloth.data.UserData
import com.halfbloodcoder.scrollsloth.debug.CalibrationLog
import com.halfbloodcoder.scrollsloth.debug.CalibrationSession
import com.halfbloodcoder.scrollsloth.debug.CaptureRepository
import com.halfbloodcoder.scrollsloth.debug.RuleInspector
import com.halfbloodcoder.scrollsloth.debug.SnapshotSerializer
import com.halfbloodcoder.scrollsloth.detect.Clock
import com.halfbloodcoder.scrollsloth.detect.HttpRulesFetcher
import com.halfbloodcoder.scrollsloth.detect.ReelDetector
import com.halfbloodcoder.scrollsloth.detect.RulesRepository
import com.halfbloodcoder.scrollsloth.detect.RulesUpdater
import com.halfbloodcoder.scrollsloth.detect.targetAppInfo
import com.halfbloodcoder.scrollsloth.stats.StatsEngine
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

// One DataStore per file per process: shared by the activity and the accessibility service.
private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** Lives as long as the process, so the last reel is still written after the service stops. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Every detector call runs here, one at a time; [ReelDetector] is not thread-safe. */
    val detectionDispatcher = Dispatchers.Default.limitedParallelism(1)

    val settingsStore = SettingsStore(appContext.settingsDataStore)
    val snapshotSerializer = SnapshotSerializer()
    val captureRepository = CaptureRepository(
        directory = File(appContext.filesDir, CaptureRepository.DIRECTORY_NAME),
        serializer = snapshotSerializer,
    )
    val ruleInspector = RuleInspector(snapshotSerializer, captureRepository)

    val database: AppDatabase by lazy {
        Room.databaseBuilder(appContext, AppDatabase::class.java, DATABASE_NAME)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .build()
    }

    val rulesRepository by lazy {
        RulesRepository(
            bundledRules = {
                appContext.assets.open(RulesRepository.FILE_NAME).bufferedReader().use { it.readText() }
            },
            overrideFile = File(appContext.filesDir, RulesRepository.FILE_NAME),
        )
    }

    /** Where rule updates come from: Settings, else the build default. Blank turns updates off. */
    val rulesUrl: Flow<String> get() = settingsStore.rulesUrl(BuildConfig.RULES_URL)

    val rulesUpdater by lazy {
        RulesUpdater(
            url = { rulesUrl.first() },
            fetcher = HttpRulesFetcher(),
            repository = rulesRepository,
            onServerAnswered = settingsStore::setRulesLastCheckedMs,
            clock = Clock.System,
        )
    }

    val userData by lazy {
        UserData(
            reelDao = database.reelDao(),
            commentaryDao = database.commentaryDao(),
            exportDirectory = File(appContext.cacheDir, UserData.EXPORT_DIRECTORY),
            deleteCaptures = { captureRepository.deleteAll() },
            deleteCalibration = { calibrationLog.delete() },
            clock = Clock.System,
        )
    }

    val reelEventWriter by lazy { ReelEventWriter(database.reelDao(), appScope) }

    val reelDetector: ReelDetector by lazy {
        ReelDetector(
            rules = { rulesRepository.rules.value },
            clock = Clock.System,
            sink = { event ->
                reelEventWriter.submit(event)
                calibrationSession.onReelViewed(event)
            },
        )
    }

    val calibrationSession: CalibrationSession by lazy {
        CalibrationSession(
            clock = Clock.System,
            // As if the user left the app: the open reel is counted now, on the detector's thread.
            flushDetector = { withContext(detectionDispatcher) { reelDetector.onOtherAppInForeground() } },
            rulesVersion = { rulesRepository.rules.value.rules.rulesVersion },
            appVersionName = { platform -> appContext.packageManager.targetAppInfo(platform.packageName).versionName },
        )
    }

    val calibrationLog = CalibrationLog(
        File(File(appContext.filesDir, CalibrationLog.DIRECTORY), CalibrationLog.FILE_NAME),
    )

    val statsEngine by lazy {
        StatsEngine(dao = database.reelDao(), clock = Clock.System, weekStart = settingsStore.weekStart)
    }

    val modelDownloader by lazy {
        // App-specific external storage: no permission needed, removed with the app.
        val directory = appContext.getExternalFilesDir(MODELS_DIRECTORY) ?: File(appContext.filesDir, MODELS_DIRECTORY)
        ModelDownloader(
            files = ModelFiles(directory, ModelSpec.GEMMA_4_E2B),
            downloads = AndroidDownloads(appContext),
            settings = settingsStore,
            scope = appScope,
        )
    }

    val templateCommentaryEngine = TemplateCommentaryEngine()

    val commentaryEngine by lazy {
        GemmaCommentaryEngine(
            model = LiteRtLanguageModel(modelFile = { modelDownloader.readyModelFile() }, cacheDir = appContext.cacheDir),
            fallback = templateCommentaryEngine,
            onModelError = { e -> Log.w("ScrollSloth", "Model failed, using a template: ${e.javaClass.simpleName}: ${e.message}") },
        )
    }

    val commentaryRepository by lazy {
        CommentaryRepository(
            stats = statsEngine,
            reelDao = database.reelDao(),
            commentaryDao = database.commentaryDao(),
            scoreConfig = settingsStore.scoreConfig,
            tone = settingsStore.commentaryTone,
            engine = commentaryEngine,
            templates = templateCommentaryEngine,
            clock = Clock.System,
        )
    }

    private companion object {
        const val MODELS_DIRECTORY = "models"
        const val DATABASE_NAME = "scroll_sloth.db"
    }
}