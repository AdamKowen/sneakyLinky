package com.example.sneakylinky.service.hotsetdatabase


import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters


class HotsetSyncWorker(appContext: Context, params: WorkerParameters)
    : CoroutineWorker(appContext, params) {

    // HotsetSyncWorker.kt — keep the retry, but log once here too
    override suspend fun doWork(): Result {
        val repo = HotsetRepository(applicationContext)
        val result = repo.syncOnce()
        return result.fold(
            onSuccess = { Result.success() },
            onFailure = {
                android.util.Log.e("HotsetSyncWorker", "retry due to: ${it.message}")
                Result.retry()
            }
        )
    }
}