package com.example.sneakylinky.service.report

import android.content.Context

// Simple verdict enum for clarity
enum class Verdict { OK, SUSPICIOUS }

// Data class that encapsulates all report fields
data class LinkReport(
    val url: String,
    val verdict: Verdict,
    val reason: String,
    val timestampMs: Long = System.currentTimeMillis()
)

// Single-responsibility dispatcher: swap implementation later (e.g. Retrofit)
object ReportDispatcher {
    /**
     * Send user report and update DB before/after.
     * @param historyId the row id created by LinkFlow for that run
     */
    suspend fun send(context: Context, historyId: Long, url: String, verdict: UserVerdict, reason: String?) {
        // Mark local report intent
        HistoryStore.markUserReport(context, historyId, verdict, reason, ReportSendState.SENDING)

        // TODO: replace with real network call (Retrofit)
        runCatching {
            // simulate server call
            val ackId = "srv_${System.currentTimeMillis()}"
            val ackMsg = "received"
            // ... real API here ...
            HistoryStore.markServerAck(context, historyId, ReportSendState.SENT_OK, ackId, ackMsg)
        }.onFailure {
            HistoryStore.markServerAck(context, historyId, ReportSendState.SENT_ERROR, null, it.message)
        }
    }
}