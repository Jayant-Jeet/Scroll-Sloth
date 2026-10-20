package com.halfbloodcoder.scrollsloth.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.halfbloodcoder.scrollsloth.ScrollSlothApplication
import com.halfbloodcoder.scrollsloth.detect.Clock
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Writes commentary for yesterday, and for last week and last month when those are new, into the
 * cache. Runs at about 00:30 local time while the phone is charging and idle, then arms itself for
 * the next night. The model is loaded only inside this job and released straight after.
 */
class NightlyCommentaryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as ScrollSlothApplication).container
        val reschedule = inputData.getBoolean(KEY_RESCHEDULE, true)
        // A stop (CancellationException) propagates without re-arming: WorkManager reruns this same job later.
        val result = try {
            container.commentaryRepository.writeMissing(modelReady = container.modelDownloader.readyModelFile() != null)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Nightly: succeed anyway so the appended next-night job is not failed with us; tomorrow catches up.
            if (reschedule) Result.success() else Result.failure()
        }
        // Appends after this running job; REPLACE would cancel ourselves.
        if (reschedule) scheduleNext(applicationContext, ExistingWorkPolicy.APPEND_OR_REPLACE)
        return result
    }

    companion object {
        private const val NIGHTLY_WORK = "nightly-commentary"
        private const val NOW_WORK = "commentary-now"
        private const val KEY_RESCHEDULE = "reschedule"

        /** Makes sure a nightly run is queued. Called on every app start; keeps an existing one. */
        fun ensureScheduled(context: Context) = scheduleNext(context, ExistingWorkPolicy.KEEP)

        /** Runs the job once right away with no constraints. For checking the model on a real phone. */
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<NightlyCommentaryWorker>()
                .setInputData(workDataOf(KEY_RESCHEDULE to false))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NOW_WORK, ExistingWorkPolicy.REPLACE, request)
        }

        /** State of the latest "run now" job, or null if there has not been one. */
        fun runNowState(context: Context): Flow<WorkInfo.State?> =
            WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(NOW_WORK).map { infos -> infos.firstOrNull()?.state }

        private fun scheduleNext(context: Context, policy: ExistingWorkPolicy) {
            val delay = NightlySchedule.delayUntilNext(Clock.System.nowMs(), ZoneId.systemDefault())
            val request = OneTimeWorkRequestBuilder<NightlyCommentaryWorker>()
                .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresCharging(true)
                        .setRequiresDeviceIdle(true)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NIGHTLY_WORK, policy, request)
        }
    }
}