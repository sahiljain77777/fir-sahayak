package com.example.firsahayak

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf

/*
 * DownloadWorker.kt — WorkManager worker that downloads the Gemma model
 * in the background, surviving app backgrounding and process death.
 * Deletes corrupt temp files on failure or fresh start.
 */
class DownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            ModelDownloader.downloadModel(applicationContext) { pct ->
                setProgressAsync(workDataOf("percent" to pct))
            }
            Result.success()
        } catch (e: Exception) {
            ModelDownloader.deleteTempFile(applicationContext)
            Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "gemma_model_download"

        fun enqueue(context: Context): LiveData<WorkInfo?> {
            val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    WORK_NAME,
                    ExistingWorkPolicy.KEEP,
                    request
                )

            return WorkManager.getInstance(context)
                .getWorkInfosForUniqueWorkLiveData(WORK_NAME)
                .map { it.firstOrNull() }
        }
    }
}
