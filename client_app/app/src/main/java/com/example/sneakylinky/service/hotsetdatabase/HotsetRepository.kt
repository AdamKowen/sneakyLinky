package com.example.sneakylinky.service.hotsetdatabase

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.room.withTransaction
import com.example.sneakylinky.SneakyLinkyApp
import com.example.sneakylinky.data.AppDatabase
import com.example.sneakylinky.data.BlacklistEntry
import com.example.sneakylinky.data.WhitelistEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HotsetRepository(private val context: Context) {
    private val TAG = "HotsetRepository"
    private val prefs by lazy {
        context.getSharedPreferences("hotset_prefs", Context.MODE_PRIVATE)
    }

    private fun getLocalVersion(): Int = prefs.getInt("hotset_version", 0)
    private fun setLocalVersion(v: Int) = prefs.edit { putInt("hotset_version", v) }


// HotsetRepository.kt — add explicit logging
    suspend fun syncOnce(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val db = AppDatabase.getInstance(SneakyLinkyApp.appContext())
            val wl = db.whitelistDao()
            val bl = db.blacklistDao()

            val headResp = RetrofitProvider.api.headLatestVersion()
            if (!headResp.isSuccessful) {
                throw IllegalStateException("latest-version HTTP ${headResp.code()}")
            }

            val latestHeader = headResp.headers()["Latest-Version"]
                ?: throw IllegalStateException("Missing Latest-Version header")

            val latest = latestHeader.toIntOrNull()
                ?: throw IllegalStateException("Bad Latest-Version value: $latestHeader")

            val current = maxOf(1, getLocalVersion())

            if (current == latest) return@runCatching

            val rec = RetrofitProvider.api.getDeltaOrSnapshot(current).record

            db.withTransaction {
                if (rec.whiteSnapshot != null || rec.blackSnapshot != null) {
                    wl.clearAll(); bl.clearAll()
                    rec.whiteSnapshot?.let { wl.insertAll(it.map { s -> WhitelistEntry(s) }) }
                    rec.blackSnapshot?.let { bl.insertAll(it.map { s -> BlacklistEntry(s) }) }
                } else {
                    // no DAO changes needed
                    rec.whiteRemove.forEach { wl.delete(it) }
                    rec.blackRemove.forEach { bl.delete(it) }
                    wl.insertAll(rec.whiteAdd.map { WhitelistEntry(it) })
                    bl.insertAll(rec.blackAdd.map { BlacklistEntry(it) })
                }
            }

            setLocalVersion(latest)
            android.util.Log.i(TAG, "Hotset updated to version=$latest")
        }.onFailure {
            android.util.Log.e(TAG, "sync failed: ${it.javaClass.simpleName}: ${it.message}", it)
        }
    }

}