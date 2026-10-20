package com.reelsloth

import android.app.Application
import androidx.work.Configuration
import com.reelsloth.work.NightlyCommentaryWorker
import com.reelsloth.work.RulesUpdateWorker
import kotlinx.coroutines.launch

/** Initialises WorkManager on demand (the default startup initializer is removed in the manifest). */
class ScrollSlothApplication : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.appScope.launch { container.modelDownloader.checkOnStartup() }
        NightlyCommentaryWorker.ensureScheduled(this)
        RulesUpdateWorker.ensureScheduled(this, container.rulesUpdater.isConfigured)
    }
}
