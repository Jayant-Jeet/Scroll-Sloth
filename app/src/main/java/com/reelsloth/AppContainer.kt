package com.reelsloth

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.preferencesDataStore
import androidx.room3.Room
import com.reelsloth.ai.AndroidDownloads
import com.reelsloth.ai.CommentaryRepository
import com.reelsloth.ai.GemmaCommentaryEngine
import com.reelsloth.ai.LiteRtLanguageModel
import com.reelsloth.ai.ModelDownloader
import com.reelsloth.ai.ModelFiles
import com.reelsloth.ai.ModelSpec
import com.reelsloth.ai.TemplateCommentaryEngine
import com.reelsloth.data.AppDatabase
import com.reelsloth.data.ReelEventWriter
import com.reelsloth.data.SettingsStore
import com.reelsloth.data.UserData
import com.reelsloth.debug.CaptureRepository
import com.reelsloth.debug.RuleInspector
import com.reelsloth.debug.SnapshotSerializer
import com.reelsloth.detect.Clock
import com.reelsloth.detect.HttpRulesFetcher
import com.reelsloth.detect.ReelDetector
import com.reelsloth.detect.RulesRepository
import com.reelsloth.detect.RulesUpdater
import com.reelsloth.stats.StatsEngine
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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

    val rulesUpdater by lazy {
        RulesUpdater(
            url = BuildConfig.RULES_URL,
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
            clock = Clock.System,
        )
    }

    val reelEventWriter by lazy { ReelEventWriter(database.reelDao(), appScope) }

    val reelDetector by lazy {
        ReelDetector(
            rules = { rulesRepository.rules.value },
            clock = Clock.System,
            sink = { reelEventWriter.submit(it) },
        )
    }

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