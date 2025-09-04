// file: service/report/HistoryStore.kt
package com.example.sneakylinky.service.report

import android.content.Context
import com.example.sneakylinky.data.AppDatabase
import com.example.sneakylinky.data.LinkHistoryDao
import kotlinx.coroutines.flow.Flow

object HistoryStore {
    private fun dao(ctx: Context): LinkHistoryDao =
        AppDatabase.getInstance(ctx).linkHistoryDao() // note: method name is lowerCamelCase

    suspend fun createRun(ctx: Context, url: String, contextText: String?): Long {
        val id = dao(ctx).insert(LinkHistory(url = url, contextText = contextText))
        return id
    }

    suspend fun markLocal(ctx: Context, id: Long, local: LocalCheck, finalUrl: String?, canonHost: String?) {
        dao(ctx).markLocalResult(id, local, finalUrl, canonHost, System.currentTimeMillis())
    }

    suspend fun markOpened(ctx: Context, id: Long, opened: Boolean) {
        dao(ctx).markOpened(id, opened, System.currentTimeMillis())
    }

    suspend fun markRemote(ctx: Context, id: Long, status: RemoteStatus, score: Float?) {
        dao(ctx).markRemote(id, status, score, System.currentTimeMillis())
    }

    suspend fun markUserReport(ctx: Context, id: Long, verdict: UserVerdict, reason: String?, state: ReportSendState) {
        dao(ctx).markUserReport(id, verdict, reason, state, System.currentTimeMillis())
    }

    suspend fun markServerAck(ctx: Context, id: Long, state: ReportSendState, ackId: String?, ackMsg: String?) {
        dao(ctx).markServerAck(id, state, ackId, ackMsg, System.currentTimeMillis())
    }

    // ✅ now resolves
    suspend fun latestForUrl(ctx: Context, url: String) = dao(ctx).latestForUrl(url)
    suspend fun recentDistinct(ctx: Context, limit: Int = 200) = dao(ctx).recentDistinct(limit)


    // expose Flow from DAO (already implemented in DAO)
    fun recentStream(ctx: Context, limit: Int = 200): Flow<List<LinkHistory>> =
        dao(ctx).recentStream(limit)

    // optional one-shot fetch if you want manual refresh calls
    suspend fun recent(ctx: Context, limit: Int = 200): List<LinkHistory> =
        dao(ctx).recent(limit)



}
