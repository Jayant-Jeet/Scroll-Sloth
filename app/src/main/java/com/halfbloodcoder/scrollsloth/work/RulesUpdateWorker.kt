package com.halfbloodcoder.scrollsloth.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.halfbloodcoder.scrollsloth.ScrollSlothApplication
import com.halfbloodcoder.scrollsloth.detect.RulesUpdater
import java.util.concurrent.TimeUnit

/**
 * Checks the rules server about once a week, on any network. Any failure leaves the current rules
 * in place and retries with WorkManager's backoff. Not scheduled while the rules address is blank.
 */
class RulesUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val updater = (applicationContext as ScrollSlothApplication).container.rulesUpdater
        return when (updater.check()) {
            is RulesUpdater.CheckResult.Failed -> Result.retry()
            else -> Result.success()
        }
    }

    companion object {
        private const val WORK_NAME = "rules-update"
        private const val INTERVAL_DAYS = 7L

        /** Queues the weekly check when a rules address is set; cancels it otherwise. */
        fun ensureScheduled(context: Context, configured: Boolean) {
            val workManager = WorkManager.getInstance(context)
            if (!configured) {
                workManager.cancelUniqueWork(WORK_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<RulesUpdateWorker>(INTERVAL_DAYS, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
