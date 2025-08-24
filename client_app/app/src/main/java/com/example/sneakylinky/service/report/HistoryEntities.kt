package com.example.sneakylinky.service.report


import androidx.room.*

/** What happened to the link at each stage */
enum class LocalCheck { UNKNOWN, SAFE, SUSPICIOUS, ERROR }
enum class RemoteStatus { NOT_STARTED, RUNNING, SAFE, RISK, ERROR }
enum class UserVerdict { NONE, OK, SUSPICIOUS }
enum class ReportSendState { NOT_SENT, SENDING, SENT_OK, SENT_ERROR }

/**
 * One row per (url, session/timestamp).
 * You can insert multiple rows for same URL across time; we query latest per URL for UI.
 */
@Entity(tableName = "link_history")
data class LinkHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val url: String,

    // canonicalization + resolution (for debugging / filters later)
    val finalUrl: String? = null,
    val canonHost: String? = null,

    // Local fast check
    val localCheck: LocalCheck = LocalCheck.UNKNOWN,

    // Remote/AI analysis
    val remoteScore: Float? = null,     // 0..1
    val remoteStatus: RemoteStatus = RemoteStatus.NOT_STARTED,

    // UX facts
    val openedInBrowser: Boolean = false,

    // Free-text around the link (optional)
    val contextText: String? = null,

    // --- user report fields (optional) ---
    val userVerdict: UserVerdict = UserVerdict.NONE,
    val userReason: String? = null,
    val reportSendState: ReportSendState = ReportSendState.NOT_SENT,
    val serverAckId: String? = null,
    val serverAckMessage: String? = null,

    // Times
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)