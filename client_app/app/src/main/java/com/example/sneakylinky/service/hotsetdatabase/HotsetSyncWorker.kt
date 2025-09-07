package com.example.sneakylinky.service.hotsetdatabase


import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf


class HotsetSyncWorker(appContext: Context, params: WorkerParameters)
    : CoroutineWorker(appContext, params) {

    // HotsetSyncWorker.kt — keep the retry, but log once here too
    override suspend fun doWork(): Result {
        val repo = HotsetRepository(applicationContext)

        val prefs = applicationContext.getSharedPreferences("hotset_prefs", Context.MODE_PRIVATE)
        val before = prefs.getInt("hotset_version", 0)
        val manual = inputData.getBoolean("manual", false)

        val result = repo.syncOnce()
        return result.fold(
            onSuccess = {
                val after = prefs.getInt("hotset_version", before)
                val status = if (after > before) "updated" else "up_to_date"
                Result.success(workDataOf("status" to status, "version" to after))
            },
            onFailure = { e ->
                val data = workDataOf("error" to (e.message ?: e.javaClass.simpleName))
                if (manual) Result.failure(data) else Result.retry()
            }
        )
    }
}