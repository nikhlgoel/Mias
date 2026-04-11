package dev.kid.core.evolution.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.kid.core.evolution.EvolutionEngine
import java.util.concurrent.TimeUnit

/**
 * EvolutionWorker — WorkManager periodic background task.
 *
 * Used when the user has NOT enabled the always-on EvolutionService.
 * Runs a lightweight evolution cycle periodically (default: every 6h)
 * when the device is idle and charging.
 *
 * WorkManager handles scheduling, persistence across reboots, and
 * constraint enforcement. No battery drain from constant polling.
 */
@HiltWorker
class EvolutionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val evolutionEngine: EvolutionEngine,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val session = evolutionEngine.runFullCycle()
            if (session.isSuccess) Result.success() else Result.retry()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "kid_evolution_periodic"

        /** Schedule periodic evolution. Safe to call multiple times — uses KEEP policy. */
        fun scheduleIfNotRunning(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()

            val request = PeriodicWorkRequestBuilder<EvolutionWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        /** Cancel all scheduled evolution tasks. */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
