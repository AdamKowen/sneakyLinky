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
import com.example.sneakylinky.ui.flow.FeatureFlags


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

        val doUrl = FeatureFlags.remoteLinkChecks(context)
        val doMsg = FeatureFlags.remoteMessageChecks(context)

        Log.d(TAG, "\nrun start raw=${raw.take(120)}")
        val runId = HistoryStore.createRun(context, raw, contextText)
        Log.d(TAG, "runId=$runId created")

        var finalUrl = resolveFinalOrWarn(context, runId, raw)
        if (finalUrl == null) {
            Log.d(TAG, "resolve failed, continuing with raw")
            finalUrl = raw
        }else{
            Log.d(TAG, "resolved finalUrl=$finalUrl")
        }

        val urlEvaluation = evaluateUrl(finalUrl)

        val url = urlEvaluation.canon?.originalUrl
        if (url == null) {
            Log.d(TAG, "urlEvaluation failed to parse")
            return
        } else {
            Log.d(
                TAG,
                "evaluated " +
                        "verdict=${urlEvaluation.verdict}, " +
                        "reasonsCount=${urlEvaluation.reasonDetails.size}, " +
                        "firstReason=${urlEvaluation.reasonDetails.firstOrNull()?.reason}, " +
                        "originalUrl=${urlEvaluation.canon?.originalUrl}, " +
                        "source=${urlEvaluation.source}, " +
                        "score=${urlEvaluation.score}"
            )
        }

        if (urlEvaluation.verdict == Verdict.BLOCK){
            Log.d(TAG, "BLOCK → markLocal(SUSPICIOUS) + toast")
            HistoryStore.markLocal(context, runId, LocalCheck.SUSPICIOUS, null, null)
            val text2 = joinWithBlankLines(urlEvaluation.reasonDetails.map { it.message })
            UiNotices.showWarning(context, url, text2)
            //UiNotices.showWarning(context, finalUrl, urlEvaluation.reasonDetails[0].message) // todo: go over all reasons
            return
        } else {
            Log.d(TAG, "SAFE → markLocal(SAFE)")
            HistoryStore.markLocal(context, runId, LocalCheck.SAFE, url, null)

            // Locally safe → open browser and mark opened
            Log.d(TAG, "rememberUrl + openSelectedBrowser")
            rememberUrl(context, url)
            openSelectedBrowserAndMarkOpened(context, runId, url)
            Log.d(TAG, "opened in browser & marked opened")

            // Kick off remote scans (URL + message context) and show a single summary toast
            Log.d(TAG, "launch remote scans (parallel)")
            launchRemoteScansCombined(context, runId, url, contextText)


            // Per your rule: if both are true → skip remote scans entirely
            if (doUrl || doMsg) {
                Log.d(TAG, "launch remote scans (parallel)")
                launchRemoteScansCombined(context, runId, finalUrl, contextText)
            } else {
                Log.d(TAG, "Both toggles OFF → mark remote as SAFE (local-only) to avoid 'no data'")
                HistoryStore.markRemote(context, runId, RemoteStatus.SAFE, null)
                remoteBreakdowns[runId] = RemoteBreakdown.NONE
            }

        }
        Log.d(TAG, "run end")
    }

    // --- Step 1: Resolve ---
    fun joinWithBlankLines(messages: List<String>): String {
        return messages.joinToString(separator = "\n\n")
    }
    fun packReasons(
        messages: List<String>,
        maxCols: Int = 24,
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
//                UiNotices.showWarning(context, raw,
//                    "Failed to resolve the link: " +
//                            "$raw\n")
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


            // Check toggles for which remote checks to run
            val doUrl = FeatureFlags.remoteLinkChecks(context)
            val doMsg = FeatureFlags.remoteMessageChecks(context) && (ctxMsg != null)


            fun fmt(s: Float?): String = s?.let { String.format("%.2f", it) } ?: "n/a"

            try {
                coroutineScope {

                    val urlDef = if (doUrl)
                        async(Dispatchers.IO) { runCatching { UrlAnalyzer.analyze(finalUrl) } }
                    else null


                    val msgDef = if (doMsg)
                        async(Dispatchers.IO) { runCatching { MessageAnalyzer.analyze(ctxMsg!!) } }
                    else null

                    val urlRes  = urlDef?.await()
                    val msgRes  = msgDef?.await()
                    val hasMsg  = ctxMsg != null

                    val urlOk    = urlRes?.isSuccess == true
                    val urlErr   = urlRes?.isFailure == true
                    val urlScore = urlRes?.getOrNull()?.phishingScore
                    val urlRisk  = (urlScore ?: 0f) >= 0.5f

                    val msgOk    = msgRes?.isSuccess == true
                    val msgErr   = msgRes?.isFailure == true
                    val msgScore = msgRes?.getOrNull()?.phishingScore
                    val msgRisk  = (msgScore ?: 0f) >= 0.5f

                    // Fail = (ran and got risk) OR (ran and got error). If not run → not a fail.
                    val urlFail = doUrl && ((urlOk && urlRisk) || urlErr)
                    val msgFail = doMsg && ((msgOk && msgRisk) || msgErr)

                    val breakdown = when {
                        urlFail && msgFail -> RemoteBreakdown.BOTH_FAIL
                        urlFail            -> RemoteBreakdown.URL_FAIL
                        msgFail            -> RemoteBreakdown.MESSAGE_FAIL
                        else               -> RemoteBreakdown.NONE
                    }
                    remoteBreakdowns[runId] = breakdown

                    val combinedScore  = listOfNotNull(urlScore, msgScore).maxOrNull()
                    val combinedStatus = when {
                        (urlOk && urlRisk) || (msgOk && msgRisk) -> RemoteStatus.RISK
                        (listOf(urlRes, msgRes).any { it?.isSuccess == true }) &&
                                !(urlRisk || msgRisk) -> RemoteStatus.SAFE
                        else -> RemoteStatus.ERROR
                    }
                    HistoryStore.markRemote(context, runId, combinedStatus, combinedScore)


                    val toastText = buildString {
                        append("URL: ")
                        append(
                            when {
                                !doUrl          -> "skipped"
                                urlOk && urlRisk -> "⚠️ suspicious"
                                urlOk            -> "✅ safe"
                                urlErr           -> "❌ error"
                                else             -> "❌ error"
                            }
                        )
                        append(" • Message: ")
                        append(
                            when {
                                !doMsg          -> "skipped"
                                msgOk && msgRisk -> "⚠️ suspicious"
                                msgOk            -> "✅ safe"
                                msgErr           -> "❌ error"
                                else             -> "❌ error"
                            }
                        )
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
