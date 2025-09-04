// file: service/report/ReportDispatcher.kt
package com.example.sneakylinky.service.report

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody

data class UserReportPayload(
    @SerializedName("url")                  val url: String,
    @SerializedName("systemClassification") val systemClassification: String,
    @SerializedName("userClassification")   val userClassification: String,
    @SerializedName("userReason")           val userReason: String? = null
)

data class ReportAck(
    @SerializedName("id")       val id: String? = null,
    @SerializedName("reportId") val reportId: String? = null,
    @SerializedName("message")  val message: String? = null
)

object ReportDispatcher {

    private const val TAG = "ReportDispatcher"
    private const val ENDPOINT =
        "https://sneaky-server-901205359337.europe-west1.run.app/v1/userReports"

    private val client = OkHttpClient()
    private val gson   = Gson()
    private val JSON: MediaType = MediaType.parse("application/json; charset=utf-8")
        ?: throw IllegalStateException("Cannot create MediaType")

    suspend fun send(
        context: Context,
        historyId: Long,
        url: String,
        verdict: UserVerdict,
        reason: String?
    ) = withContext(Dispatchers.IO) {
        // Mark local intent (SENDING) — positional args
        HistoryStore.markUserReport(context, historyId, verdict, reason, ReportSendState.SENDING)


        val latest = runCatching { HistoryStore.latestForUrl(context, url) }.getOrNull()
        val systemCls = mapSystemClassification(latest?.localCheck, latest?.remoteStatus)
        val userCls   = mapUserClassification(verdict)

// server requires a non-empty reason string
        val safeReason = (reason?.takeIf { it.isNotBlank() } ?: "no-reason-provided")

        val json = JsonObject().apply {
            addProperty("url", url)
            addProperty("systemClassification", systemCls) // Int (0/1), not a String
            addProperty("userClassification", userCls)     // Int (0/1), not a String
            addProperty("userReason", safeReason)          // always non-empty
        }
        val body = RequestBody.create(JSON, gson.toJson(json))
        val request = Request.Builder()
            .url(ENDPOINT)
            .post(body)
            .build()

        runCatching {
            client.newCall(request).execute().use { response ->
                val raw = response.body()?.string().orEmpty()
                if (response.isSuccessful) {
                    val ackId = parseAckId(raw)
                    // POSITIONAL: ctx, id, state, ackId, ackMsg
                    HistoryStore.markServerAck(context, historyId, ReportSendState.SENT_OK, ackId, raw.ifBlank { "OK ${response.code()}" })
                    Log.i(TAG, "Report OK (${response.code()}) id=$ackId url=$url")
                } else {
                    HistoryStore.markServerAck(context, historyId, ReportSendState.SENT_ERROR, null, raw.ifBlank { "HTTP ${response.code()} – ${response.message()}" })
                    Log.w(TAG, "Report FAIL (${response.code()}) url=$url body=$raw")
                }
            }
        }.onFailure { t ->
            HistoryStore.markServerAck(context, historyId, ReportSendState.SENT_ERROR, null, t.message)
            Log.e(TAG, "Report error for $url", t)
        }
    }

    // Mapping based on your enums

    private fun mapSystemClassification(local: LocalCheck?, remote: RemoteStatus?): Int {
        return when {
            remote == RemoteStatus.SAFE -> 0
            remote == RemoteStatus.RISK || remote == RemoteStatus.ERROR -> 1
            local == LocalCheck.SAFE -> 0
            local == LocalCheck.SUSPICIOUS || local == LocalCheck.ERROR -> 1
            else -> 1 // conservative fallback; schema doesn't allow UNKNOWN
        }
    }

    private fun mapUserClassification(verdict: UserVerdict): Int {
        return when (verdict) {
            UserVerdict.OK          -> 0
            UserVerdict.SUSPICIOUS  -> 1
            UserVerdict.NONE        -> 1 // pick 1 as fallback (schema is [0,1])
        }
    }



    private fun parseAckId(raw: String?): String? = runCatching {
        if (raw.isNullOrBlank()) return null
        val ack = gson.fromJson(raw, ReportAck::class.java)
        ack.id ?: ack.reportId
    }.getOrNull()
}
