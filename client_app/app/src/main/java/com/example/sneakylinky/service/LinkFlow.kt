// comments in English only
package com.example.sneakylinky.service

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.core.content.edit
import com.example.sneakylinky.LinkContextCache
import com.example.sneakylinky.SneakyLinkyApp
import com.example.sneakylinky.service.report.HistoryStore
import com.example.sneakylinky.service.report.LocalCheck
import com.example.sneakylinky.service.report.RemoteStatus
import com.example.sneakylinky.service.serveranalysis.MessageAnalyzer
import com.example.sneakylinky.service.serveranalysis.UrlAnalyzer
import com.example.sneakylinky.service.urlanalyzer.Verdict
import com.example.sneakylinky.service.urlanalyzer.evaluateUrl
import com.example.sneakylinky.ui.MainActivity
import com.example.sneakylinky.util.UiNotices
import com.example.sneakylinky.util.launchInSelectedBrowser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

object LinkFlow {

    enum class RemoteBreakdown {
        NONE,          // no remote failure (both passed or message skipped)
        URL_FAIL,      // URL check failed (risk or error)
        MESSAGE_FAIL,  // Message check failed (risk or error)
        BOTH_FAIL      // both URL and Message failed
    }
    private const val TAG = "LinkFlow"

    // In-memory map: runId -> breakdown (no DB changes)
    private val remoteBreakdowns = ConcurrentHashMap<Long, RemoteBreakdown>()

    /** Public getter for UI code (e.g., iconFor). */
    fun remoteBreakdownFor(runId: Long): RemoteBreakdown? = remoteBreakdowns[runId]


    // Single entry-point used by both activities
    suspend fun runLinkFlow(context: Context, raw: String, contextText: String? = null) {

        Log.d(TAG, "\nrun start raw=${raw.take(120)}")
        val runId = HistoryStore.createRun(context, raw, contextText)
        Log.d(TAG, "runId=$runId created")

        val finalUrl = resolveFinalOrWarn(context, runId, raw)
        if (finalUrl == null) {
            Log.d(TAG, "resolve failed → warned & exit")
            return
        }

        Log.d(TAG, "resolved finalUrl=$finalUrl")

        val urlEvaluation = evaluateUrl(finalUrl)
        Log.d(TAG,"evaluated verdict=${urlEvaluation.verdict} reasons=${urlEvaluation.reasonDetails.size}" +
                    (urlEvaluation.reasonDetails.firstOrNull()?.let { " firstReason=${it.reason}" } ?: ""))

        if (urlEvaluation.verdict == Verdict.BLOCK){
            Log.d(TAG, "BLOCK → markLocal(SUSPICIOUS) + toast")
            HistoryStore.markLocal(context, runId, LocalCheck.SUSPICIOUS, null, null)
            val text = packReasons(urlEvaluation.reasonDetails.map { it.message })
            UiNotices.showWarning(context, finalUrl, text)
//            UiNotices.showWarning(context, finalUrl, urlEvaluation.reasonDetails[0].message) // todo: go over all reasons
            return
        } else {
            Log.d(TAG, "SAFE → markLocal(SAFE)")
            HistoryStore.markLocal(context, runId, LocalCheck.SAFE, finalUrl, null)

            // Locally safe → open browser and mark opened
            Log.d(TAG, "rememberUrl + openSelectedBrowser")
            rememberUrl(context, finalUrl)
            openSelectedBrowserAndMarkOpened(context, runId, finalUrl)
            Log.d(TAG, "opened in browser & marked opened")

            // Kick off remote scans (URL + message context) and show a single summary toast
            Log.d(TAG, "launch remote scans (parallel)")
            launchRemoteScansCombined(context, runId, finalUrl, contextText)
        }
        Log.d(TAG, "run end")
    }

    // --- Step 1: Resolve ---
    fun packReasons(
        messages: List<String>,
        maxCols: Int = 15,
        maxRows: Int = 17
    ): String {
        val lines = mutableListOf<String>()

        for (msg in messages) {
            var i = 0
            while (i < msg.length) {
                // Candidate end of line
                val end = (i + maxCols).coerceAtMost(msg.length)

                // Try to break at the last space before `end`
                val cut = msg.lastIndexOf(' ', end - 1, ignoreCase = false)
                    .takeIf { it >= i }  // valid break point in this segment
                    ?: end                // else hard cut

                lines.add(msg.substring(i, cut).trimEnd())
                i = if (cut == end) end else cut + 1
            }

            // Blank line after each message
            lines.add("")
            if (lines.size >= maxRows) break
        }

        val limited = lines.take(maxRows)
        return limited.dropLastWhile { it.isBlank() }.joinToString("\n")
    }


    private suspend fun resolveFinalOrWarn(context: Context, runId: Long, raw: String):
            String? = withContext(Dispatchers.IO) {
        when (val res = LinkChecker.resolveUrl(raw)) {
            is LinkChecker.UrlResolutionResult.Success -> res.finalUrl
            else -> {
                rememberUrl(context, raw)
                HistoryStore.markLocal(context, runId, LocalCheck.ERROR, null, null)
                UiNotices.showWarning(context, raw,
                    "Failed to resolve the link: " +
                            "$raw\n")
                null
            }
        }
    }

    private suspend fun resolveFinalUrl(raw: String): String? =
        withContext(Dispatchers.IO) {
            when (val res = LinkChecker.resolveUrl(raw)) {
                is LinkChecker.UrlResolutionResult.Success -> res.finalUrl
                else -> null
            }
        }


    // --- Step 4: Open in selected browser and record ---

    private suspend fun openSelectedBrowserAndMarkOpened(
        context: Context,
        runId: Long,
        finalUrl: String
    ) {
        launchInSelectedBrowser(context, finalUrl)
        HistoryStore.markOpened(context, runId, true)
    }

    // --- Step 5: Remote scans (URL + message), run in parallel and toast once ---

    private suspend fun launchRemoteScansCombined(
        context: Context,
        runId: Long,
        finalUrl: String,
        explicitContextText: String?
    ) {

        Toast.makeText(context, "Sneaky checking link", Toast.LENGTH_SHORT).show()

        HistoryStore.markRemote(context, runId, RemoteStatus.RUNNING, null)

        SneakyLinkyApp.appScope.launch {
            val ctxMsg = chooseContextMessage(explicitContextText)
            fun fmt(s: Float?): String = s?.let { String.format("%.2f", it) } ?: "n/a"

            try {
                coroutineScope {
                    val urlDef = async(Dispatchers.IO) { runCatching { UrlAnalyzer.analyze(finalUrl) } }
                    val msgDef = async(Dispatchers.IO) {
                        if (ctxMsg != null) runCatching { MessageAnalyzer.analyze(ctxMsg) } else null
                    }

                    val urlRes  = urlDef.await()
                    val msgRes  = msgDef.await()
                    val hasMsg  = ctxMsg != null

                    val urlOk    = urlRes.isSuccess
                    val urlErr   = urlRes.isFailure
                    val urlScore = urlRes.getOrNull()?.phishingScore
                    val urlRisk  = (urlScore ?: 0f) >= 0.5f

                    val msgOk    = hasMsg && (msgRes?.isSuccess == true)
                    val msgErr   = hasMsg && (msgRes?.isFailure == true)
                    val msgScore = if (hasMsg) msgRes?.getOrNull()?.phishingScore else null
                    val msgRisk  = (msgScore ?: 0f) >= 0.5f

                    // --- NEW: simple breakdown (risk OR error counts as "fail") ---
                    val urlFail = (urlOk && urlRisk) || urlErr
                    val msgFail = hasMsg && ((msgOk && msgRisk) || msgErr)

                    val breakdown = when {
                        urlFail && msgFail -> RemoteBreakdown.BOTH_FAIL
                        urlFail            -> RemoteBreakdown.URL_FAIL
                        msgFail            -> RemoteBreakdown.MESSAGE_FAIL
                        else               -> RemoteBreakdown.NONE
                    }
                    remoteBreakdowns[runId] = breakdown
                    // --------------------------------------------------------------

                    val combinedScore  = listOfNotNull(urlScore, msgScore).maxOrNull()
                    val combinedStatus = when {
                        (urlOk && urlRisk) || (msgOk && msgRisk) -> RemoteStatus.RISK
                        (urlOk || msgOk) && !(urlRisk || msgRisk) -> RemoteStatus.SAFE
                        else -> RemoteStatus.ERROR
                    }
                    HistoryStore.markRemote(context, runId, combinedStatus, combinedScore)

                    val toastText = buildString {
                        append("URL: ")
                        append(
                            when {
                                urlOk && urlRisk -> "⚠️ suspicious (" + fmt(urlScore) + ")"
                                urlOk            -> "✅ safe (" + fmt(urlScore) + ")"
                                urlErr           -> "❌ error"
                                else             -> "❌ error"
                            }
                        )
                        if (hasMsg) {
                            append(" • Message: ")
                            append(
                                when {
                                    msgOk && msgRisk -> "⚠️ suspicious (" + fmt(msgScore) + ")"
                                    msgOk            -> "✅ safe (" + fmt(msgScore) + ")"
                                    msgErr           -> "❌ error"
                                    else             -> "skipped"
                                }
                            )
                        }
                    }

                    UiNotices.safeToast(context, toastText)
                }
            } catch (_: Throwable) {
                remoteBreakdowns[runId] = RemoteBreakdown.BOTH_FAIL // conservative fallback
                HistoryStore.markRemote(context, runId, RemoteStatus.ERROR, null)
                UiNotices.safeToast(context, "Remote scan error")
            }
        }
    }

    // --- Helpers ---

    private fun chooseContextMessage(explicit: String?): String? {
        // Prefer the explicit text passed by the caller; otherwise use Accessibility cache
        return explicit?.takeIf { it.isNotBlank() }
            ?: LinkContextCache.surroundingTxt?.takeIf { it.isNotBlank() }
    }



    // Shared: remember last URL in memory + persist for process restarts
    private fun rememberUrl(context: Context, url: String) {
        MainActivity.lastOpenedLink = url
        val prefs = context.getSharedPreferences("sneaky_linky_prefs", Context.MODE_PRIVATE)
        prefs.edit { putString("last_url", url) }
    }
}
