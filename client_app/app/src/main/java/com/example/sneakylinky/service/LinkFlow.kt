// comments in English only
package com.example.sneakylinky.service

import android.content.Context
import android.os.SystemClock
import android.util.Log
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
import com.example.sneakylinky.ui.flow.FeatureFlags
import com.example.sneakylinky.util.UiNotices
import com.example.sneakylinky.util.launchInSelectedBrowser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import com.example.sneakylinky.service.urlanalyzer.DecisionSource
object LinkFlow {

    @Volatile private var lastResolveBlocked: Boolean = false

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

    // --------- small logging helpers (no logic change) ----------
    private fun tn(): String = Thread.currentThread().name
    private fun now(): Long = SystemClock.elapsedRealtime()
    private fun short(s: String?, max: Int = 160): String =
        s?.let { if (it.length <= max) it else it.take(max) + "…" } ?: "null"

    // Single entry-point used by both activities
    suspend fun runLinkFlow(context: Context, raw: String, contextText: String? = null) {
        val t0 = now()
        val doUrl = FeatureFlags.remoteLinkChecks(context)
        val doMsg = FeatureFlags.remoteMessageChecks(context)

        Log.d(TAG, "\nrun start thread=${tn()} doUrl=$doUrl doMsg=$doMsg raw='${short(raw)}' ctxLen=${contextText?.length ?: 0}")
        val runId = HistoryStore.createRun(context, raw, contextText)
        Log.d(TAG, "runId=$runId created (thread=${tn()})")

        val tResolveStart = now()
        Log.d(TAG, "[${tn()}] resolveFinalOrWarn start (runId=$runId)")
        var finalUrl = resolveFinalOrWarn(context, runId, raw)
        val tResolveMs = now() - tResolveStart
        Log.d(TAG, "resolve finished in ${tResolveMs}ms (runId=$runId); lastResolveBlocked=$lastResolveBlocked finalUrlNull=${finalUrl==null} (thread=${tn()})")

        if (lastResolveBlocked) {
            Log.d(TAG, "hard stop due to lastResolveBlocked=true (runId=$runId)")
            return   // ← hard stop on 6s budget exhaustion
        }
        if (finalUrl == null) {
            Log.d(TAG, "resolve failed → continuing with raw (runId=$runId)")
            finalUrl = raw
        } else {
            Log.d(TAG, "resolved finalUrl='${short(finalUrl)}' (runId=$runId)")
        }

        val tEvalStart = now()
        val urlEvaluation = evaluateUrl(finalUrl)
        Log.d(TAG, "evaluateUrl took ${now() - tEvalStart}ms (runId=$runId)")

        val url = urlEvaluation.canon?.originalUrl
        if (url == null) {
            Log.d(TAG, "urlEvaluation failed to parse; rememberUrl(raw) and return (runId=$runId)")
            rememberUrl(context, finalUrl)
            return
        } else {
            Log.d(
                TAG,
                "evaluated verdict=${urlEvaluation.verdict}, reasonsCount=${urlEvaluation.reasonDetails.size}, " +
                        "firstReason=${urlEvaluation.reasonDetails.firstOrNull()?.reason}, " +
                        "originalUrl='${short(urlEvaluation.canon?.originalUrl)}', " +
                        "source=${urlEvaluation.source}, score=${urlEvaluation.score} (runId=$runId)"
            )
        }

        if (urlEvaluation.verdict == Verdict.BLOCK){
            Log.d(TAG, "BLOCK → markLocal(SUSPICIOUS) + showWarning (runId=$runId)")
            HistoryStore.markLocal(context, runId, LocalCheck.SUSPICIOUS, null, null)
            rememberUrl(context, url)
            // Build reason text: prefer provided reasons; fallback only if empty
            val msgs = urlEvaluation.reasonDetails
                .map { it.message.trim() }
                .filter { it.isNotEmpty() }
                .distinct()

            val text2 = if (msgs.isNotEmpty()) {
                // Use the exact formatter that already works well for you
                joinWithBlankLines(msgs)
            } else {
                // Minimal, source-aware fallback to avoid empty screens
                when (urlEvaluation.source) {
                    DecisionSource.BLACKLIST ->
                        "Blocked: domain is on your blacklist (${urlEvaluation.canon?.hostAscii ?: "this domain"})."
                    DecisionSource.CANON_PARSE_ERROR ->
                        "Blocked: invalid or unparsable URL."
                    else ->
                        "Blocked for your safety."
                }
            }
            Log.d(TAG, "showWarning url='${short(url)}' reason='${short(text2)}' (runId=$runId)")
            UiNotices.showWarning(context, url, text2)
            return
        } else {
            Log.d(TAG, "SAFE → markLocal(SAFE) (runId=$runId)")
            HistoryStore.markLocal(context, runId, LocalCheck.SAFE, url, null)

            // Locally safe → open browser and mark opened
            Log.d(TAG, "rememberUrl + openSelectedBrowser (runId=$runId)")
            rememberUrl(context, url)
            openSelectedBrowserAndMarkOpened(context, runId, url)
            Log.d(TAG, "opened in browser & marked opened (runId=$runId)")

            // Kick off remote scans (URL + message context) and show a single summary toast
            if (doUrl || doMsg) {
                Log.d(TAG, "launch remote scans (parallel) (runId=$runId)")
                launchRemoteScansCombined(context, runId, finalUrl, contextText)
            } else {
                Log.d(TAG, "Both toggles OFF → mark remote as SAFE (local-only) to avoid 'no data' (runId=$runId)")
                HistoryStore.markRemote(context, runId, RemoteStatus.SAFE, null)
                remoteBreakdowns[runId] = RemoteBreakdown.NONE
            }
        }
        Log.d(TAG, "run end took=${now() - t0}ms (runId=$runId)")
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
                val end = (i + maxCols).coerceAtMost(msg.length)
                val cut = msg.lastIndexOf(' ', end - 1, ignoreCase = false)
                    .takeIf { it >= i } ?: end
                lines.add(msg.substring(i, cut).trimEnd())
                i = if (cut == end) end else cut + 1
            }
            lines.add("")
            if (lines.size >= maxRows) break
        }
        val limited = lines.take(maxRows)
        return limited.dropLastWhile { it.isBlank() }.joinToString("\n")
    }

    private suspend fun resolveFinalOrWarn(
        context: Context,
        runId: Long,
        raw: String
    ): String? = kotlinx.coroutines.coroutineScope {
        lastResolveBlocked = false

        Log.d(TAG, "resolveFinalOrWarn(raw='${short(raw)}') thread=${tn()} (runId=$runId)")

        // staged, user-friendly progress toasts while resolve runs in IO
        val hintJob = launch {
            try {
                Log.d(TAG, "hintJob scheduled (runId=$runId)")
                kotlinx.coroutines.delay(1500) // 1.5s
                Log.d(TAG, "hint#1 toast about to show (runId=$runId)")
                UiNotices.safeToast(context, "taking longer than usual…", 2000)
                kotlinx.coroutines.delay(3500) // +1.5s = 3s total
                Log.d(TAG, "hint#2 toast about to show (runId=$runId)")
                UiNotices.safeToast(context, "still checking…",2500)
            } catch (t: Throwable) {
                Log.d(TAG, "hintJob cancelled: ${t.message} (runId=$runId)")
            }
        }

        // run the blocking resolve on IO while hints tick on the main scope
        val tIO = now()
        Log.d(TAG, "[${tn()}] resolveUrl IO call begin (runId=$runId)")
        val res = withContext(Dispatchers.IO) { LinkChecker.resolveUrl(raw) }
        Log.d(TAG, "[${tn()}] resolveUrl IO call end in ${now() - tIO}ms (runId=$runId)")

        // stop any pending hints immediately when resolve completes
        hintJob.cancel()
        Log.d(TAG, "hintJob cancel() called (runId=$runId)")

        when (res) {
            is LinkChecker.UrlResolutionResult.Success -> {
                Log.d(TAG, "resolve SUCCESS final='${short(res.finalUrl)}' (runId=$runId)")
                res.finalUrl
            }
            is LinkChecker.UrlResolutionResult.Failure -> {
                Log.d(TAG, "resolve FAILURE cause=${res.error} → markLocal(ERROR) + maybe block (runId=$runId)")
                rememberUrl(context, raw)
                HistoryStore.markLocal(context, runId, LocalCheck.ERROR, null, null)

                if (res.error == LinkChecker.UrlResolutionResult.ErrorCause.SLOW_REDIRECT) {
                    Log.d(TAG, "slow redirect → showWarning + set lastResolveBlocked=true (runId=$runId)")
                    UiNotices.showWarning(context, raw, "The link took too long to respond and was blocked for your safety. Try again later.")
                    lastResolveBlocked = true
                }
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
        Log.d(TAG, "openSelectedBrowserAndMarkOpened url='${short(finalUrl)}' (runId=$runId)")
        launchInSelectedBrowser(context, finalUrl)
        HistoryStore.markOpened(context, runId, true)
        Log.d(TAG, "markOpened(true) persisted (runId=$runId)")
    }

    // --- Step 5: Remote scans (URL + message), run in parallel and toast once ---
    private suspend fun launchRemoteScansCombined(
        context: Context,
        runId: Long,
        finalUrl: String,
        explicitContextText: String?
    ) {
        val scanId = java.lang.Long.toHexString(System.nanoTime()).takeLast(12)
        Log.d(TAG, "remote-scan entry (runId=$runId scan=$scanId) finalUrl='${short(finalUrl)}' explicitCtxLen=${explicitContextText?.length ?: 0}")

        val cacheLen = LinkContextCache.surroundingTxt?.length ?: 0
        Log.d(TAG, "chooseContextMessage: explicitLen=${explicitContextText?.length ?: 0} cacheLen=$cacheLen (runId=$runId scan=$scanId)")
        val ctxMsg = chooseContextMessage(explicitContextText)

        val wantUrl = FeatureFlags.remoteLinkChecks(context)
        val wantMsg = FeatureFlags.remoteMessageChecks(context)
        val runUrl = wantUrl
        val runMsg = wantMsg && ctxMsg != null

        Log.d(TAG, "remote-scan decide (runId=$runId scan=$scanId) wantUrl=$wantUrl wantMsg=$wantMsg ctxNull=${ctxMsg==null} → runUrl=$runUrl runMsg=$runMsg")

        if (!runUrl && !runMsg) {
            Log.d(TAG, "no remote scans → markRemote(SAFE,null) + breakdown=NONE (runId=$runId scan=$scanId)")
            HistoryStore.markRemote(context, runId, RemoteStatus.SAFE, null)
            remoteBreakdowns[runId] = RemoteBreakdown.NONE
            return
        }

        val startText = when {
            runUrl && runMsg -> "Checking link + message…"
            runUrl           -> "Checking link…"
            else             -> "Checking message…"
        }
        Log.d(TAG, "remote-scan start toast: '${short(startText)}' (runId=$runId scan=$scanId)")
        //UiNotices.safeToast(context, startText)

        Log.d(TAG, "mark REMOTE RUNNING (runId=$runId scan=$scanId)")
        HistoryStore.markRemote(context, runId, RemoteStatus.RUNNING, null)

        val tStart = now()
        SneakyLinkyApp.appScope.launch {
            try {
                Log.d(TAG, "[${tn()}] remote-scan coroutine started (runId=$runId scan=$scanId)")
                coroutineScope {
                    val urlDef = if (runUrl)
                        async(Dispatchers.IO) {
                            Log.d(TAG, "launch UrlAnalyzer on IO (runId=$runId scan=$scanId)")
                            runCatching { UrlAnalyzer.analyze(finalUrl) }
                        } else null

                    val msgDef = if (runMsg)
                        async(Dispatchers.IO) {
                            Log.d(TAG, "launch MessageAnalyzer on IO (runId=$runId scan=$scanId msgLen=${ctxMsg?.length})")
                            runCatching { MessageAnalyzer.analyze(ctxMsg!!) }
                        } else null

                    val urlRes = urlDef?.await()
                    val msgRes = msgDef?.await()

                    Log.d(TAG, "urlRes awaited: null=${urlRes==null} success=${urlRes?.isSuccess==true} failure=${urlRes?.isFailure==true} (scan=$scanId)")
                    Log.d(TAG, "msgRes awaited: null=${msgRes==null} success=${msgRes?.isSuccess==true} failure=${msgRes?.isFailure==true} (scan=$scanId)")

                    val urlOk    = urlRes?.isSuccess == true
                    val urlErr   = urlRes?.isFailure == true
                    val urlScore = urlRes?.getOrNull()?.phishingScore
                    val urlRisk  = (urlScore ?: 0f) >= 0.5f

                    val msgOk    = msgRes?.isSuccess == true
                    val msgErr   = msgRes?.isFailure == true
                    val msgScore = msgRes?.getOrNull()?.phishingScore
                    val msgRisk  = (msgScore ?: 0f) >= 0.5f

                    Log.d(TAG, "scores: urlOk=$urlOk urlErr=$urlErr urlScore=${urlScore ?: "null"} urlRisk=$urlRisk | msgOk=$msgOk msgErr=$msgErr msgScore=${msgScore ?: "null"} msgRisk=$msgRisk (scan=$scanId)")

                    // Fail only if that check actually ran
                    val urlFail = runUrl && ((urlOk && urlRisk) || urlErr)
                    val msgFail = runMsg && ((msgOk && msgRisk) || msgErr)

                    val breakdown = when {
                        urlFail && msgFail -> RemoteBreakdown.BOTH_FAIL
                        urlFail            -> RemoteBreakdown.URL_FAIL
                        msgFail            -> RemoteBreakdown.MESSAGE_FAIL
                        else               -> RemoteBreakdown.NONE
                    }
                    remoteBreakdowns[runId] = breakdown
                    Log.d(TAG, "breakdown=$breakdown (runId=$runId scan=$scanId)")

                    val combinedScore  = listOfNotNull(urlScore, msgScore).maxOrNull()
                    val combinedStatus = when {
                        (urlOk && urlRisk) || (msgOk && msgRisk) -> RemoteStatus.RISK
                        (listOf(urlRes, msgRes).any { it?.isSuccess == true }) &&
                                !(urlRisk || msgRisk) -> RemoteStatus.SAFE
                        else -> RemoteStatus.ERROR
                    }
                    Log.d(TAG, "markRemote status=$combinedStatus score=${combinedScore ?: "null"} (runId=$runId scan=$scanId)")
                    HistoryStore.markRemote(context, runId, combinedStatus, combinedScore)

                    val parts = mutableListOf<String>()

                    if (runUrl) {
                        parts.add(
                            "🔗 Link: " + when {
                                urlOk && urlRisk -> "⚠️ suspicious️"
                                urlOk            -> "✅ safe"
                                urlErr           -> "❌ error"
                                else             -> "❌ error"
                            }
                        )
                    }

                    if (runMsg) {
                        parts.add(
                            "💬 Message: " + when {
                                msgOk && msgRisk -> "⚠️ suspicious"
                                msgOk            -> "✅ safe"
                                msgErr           -> "❌ error"
                                else             -> "❌ error"
                            }
                        )
                    }

                    val toastText = parts.joinToString("\n")

                    Log.d(TAG, "remote-scan final toast='${short(toastText)}' (runId=$runId scan=$scanId)")
                    UiNotices.safeToast(context, toastText)

                }
                Log.d(TAG, "remote-scan end; took=${now() - tStart}ms (runId=$runId scan=$scanId)")
            } catch (e: Throwable) {
                Log.w(TAG, "remote-scan exception: ${e.message} (runId=$runId scan=$scanId)", e)
                remoteBreakdowns[runId] = RemoteBreakdown.BOTH_FAIL // conservative
                HistoryStore.markRemote(context, runId, RemoteStatus.ERROR, null)
                Log.d(TAG, "remote-scan error toast about to show (runId=$runId scan=$scanId)")
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
        Log.d(TAG, "rememberUrl set lastOpenedLink + prefs url='${short(url)}'")
    }


    private fun buildReasonText(e: com.example.sneakylinky.service.urlanalyzer.UrlEvaluation): String {
        // Prefer explicit reason messages if present (heuristics usually provide them)
        val msgs = e.reasonDetails.map { it.message }.filter { it.isNotBlank() }
        if (msgs.isNotEmpty()) {
            // Use existing wrapper to keep consistent wrapping/spacing
            return packReasons(msgs)
        }

        // Fallbacks by decision source so we never show an empty screen
        val host = e.canon?.hostAscii ?: "this domain"
        return when (e.source) {
            DecisionSource.BLACKLIST ->
                "Blocked: domain is on your blacklist ($host)."
            DecisionSource.CANON_PARSE_ERROR ->
                "Blocked: invalid or unparsable URL."
            DecisionSource.WHITELIST ->
                // Shouldn’t happen with BLOCK, but safe default
                "Blocked for your safety."
            DecisionSource.HEURISTICS ->
                // Heuristics without explicit reasons (rare) — generic text
                "Blocked for your safety."
        }
    }
}
