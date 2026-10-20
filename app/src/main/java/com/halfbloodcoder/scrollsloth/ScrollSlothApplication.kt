package com.halfbloodcoder.scrollsloth

import android.app.Application
import androidx.work.Configuration
import com.halfbloodcoder.scrollsloth.work.NightlyCommentaryWorker
import com.halfbloodcoder.scrollsloth.work.RulesUpdateWorker
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
        container.appScope.launch {
            container.rulesUrl.map { it.isNotBlank() }.distinctUntilChanged().collect { configured ->
                RulesUpdateWorker.ensureScheduled(this@ScrollSlothApplication, configured)
            }
        }
    }
}
