package com.bizur.android.transport

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bizur.android.BizurApplication
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

class QueueDrainWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = try {
        val app = applicationContext as BizurApplication
        app.container.transport.requestQueueSync()
        Result.success()
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (err: Exception) {
        Result.retry()
    }

    companion object {
        private const val UNIQUE_NAME = "bizur-queue-drain"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<QueueDrainWorker>(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
