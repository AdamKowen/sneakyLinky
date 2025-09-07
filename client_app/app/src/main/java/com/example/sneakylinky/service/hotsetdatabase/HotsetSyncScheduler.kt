package com.example.sneakylinky.service.hotsetdatabase


import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Constraints
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

object HotsetSyncScheduler {
    private const val UNIQUE_WEEKLY = "hotset-weekly-sync"

    private val net = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun scheduleWeekly(context: Context) {
        val req = PeriodicWorkRequestBuilder<HotsetSyncWorker>(7, TimeUnit.DAYS)
            .setConstraints(net)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WEEKLY,
            ExistingPeriodicWorkPolicy.UPDATE,
            req
        )
    }

    fun runNow(context: Context) {
        val once = OneTimeWorkRequestBuilder<HotsetSyncWorker>()
            .setConstraints(net)
            .setInputData(workDataOf("manual" to true)) // flag manual trigger
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "hotset-manual-sync",
            ExistingWorkPolicy.REPLACE,
            once
        )
    }



}