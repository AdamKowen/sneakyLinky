// comments in English only
package com.example.sneakylinky.service

import android.content.Context
import androidx.core.content.edit
import androidx.core.net.toUri
import com.example.sneakylinky.LinkContextCache
import com.example.sneakylinky.SneakyLinkyApp
import com.example.sneakylinky.service.report.HistoryStore
import com.example.sneakylinky.service.report.LocalCheck
import com.example.sneakylinky.service.report.RemoteStatus
import com.example.sneakylinky.service.serveranalysis.MessageAnalyzer
import com.example.sneakylinky.service.serveranalysis.UrlAnalyzer
import com.example.sneakylinky.service.urlanalyzer.CanonicalParseResult
import com.example.sneakylinky.service.urlanalyzer.CanonUrl
import com.example.sneakylinky.service.urlanalyzer.canonicalize
import com.example.sneakylinky.service.urlanalyzer.isLocalSafe
import com.example.sneakylinky.ui.MainActivity
import com.example.sneakylinky.util.UiNotices
import com.example.sneakylinky.util.launchInSelectedBrowser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object LinkFlow {

    // Single entry-point used by both activities
    suspend fun runLinkFlow(
        context: Context,
        raw: String,
        contextText: String? = null
    ) {
        val runId = HistoryStore.createRun(context, raw, contextText)

        val finalUrl = resolveFinalOrWarn(context, runId, raw) ?: return
        val canon    = canonicalizeOrWarn(context, runId, finalUrl) ?: return

        val isSafe = performLocalCheckAndMark(context, runId, finalUrl, canon)
        if (!isSafe) {
            rememberUrl(context, finalUrl)
            UiNotices.showWarning(context, finalUrl, "Link found to be suspicious. Proceed with caution.")
            return
        }

        // Locally safe → open browser and mark opened
        rememberUrl(context, finalUrl)
        openSelectedBrowserAndMarkOpened(context, runId, finalUrl)

        // Kick off remote scans (URL + message context) and show a single summary toast
        launchRemoteScansCombined(context, runId, finalUrl, contextText)
    }

    // --- Step 1: Resolve ---

    private suspend fun resolveFinalOrWarn(
        context: Context,
        runId: Long,
        raw: String
    ): String? = withContext(Dispatchers.IO) {
        when (val res = LinkChecker.resolveUrl(raw)) {
            is LinkChecker.UrlResolutionResult.Success -> res.finalUrl
            else -> {
                rememberUrl(context, raw)
                HistoryStore.markLocal(context, runId, LocalCheck.ERROR, null, null)
                UiNotices.showWarning(context, raw, "Internal error: could not verify link safely")
                null
            }
        }
    }

    // --- Step 2: Canonicalize ---

    private suspend fun canonicalizeOrWarn(
        context: Context,
        runId: Long,
        url: String
    ): CanonUrl? = when (val r = url.canonicalize()) {
        is CanonicalParseResult.Success -> r.canonUrl
        else -> {
            rememberUrl(context, url)
            HistoryStore.markLocal(context, runId, LocalCheck.ERROR, url, null)
            UiNotices.showWarning(context, url, "Internal error: could not verify link safely")
            null
        }
    }

    // --- Step 3: Local check (host, lists, heuristics) ---

    private suspend fun performLocalCheckAndMark(
        context: Context,
        runId: Long,
        finalUrl: String,
        canon: CanonUrl
    ): Boolean {
        val isSafe = withContext(Dispatchers.IO) { canon.isLocalSafe() }
        val host   = finalUrl.toUri().host
        HistoryStore.markLocal(
            context,
            runId,
            if (isSafe) LocalCheck.SAFE else LocalCheck.SUSPICIOUS,
            finalUrl,
            host
        )
        return isSafe
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
                    val msgRes  = msgDef.await() // may be null if no context
                    val hasMsg  = ctxMsg != null

                    val urlOk    = urlRes.isSuccess
                    val urlErr   = urlRes.isFailure
                    val urlScore = urlRes.getOrNull()?.phishingScore
                    val urlRisk  = (urlScore ?: 0f) >= 0.5f

                    val msgOk    = hasMsg && (msgRes?.isSuccess == true)
                    val msgErr   = hasMsg && (msgRes?.isFailure == true)
                    val msgScore = if (hasMsg) msgRes?.getOrNull()?.phishingScore else null
                    val msgRisk  = (msgScore ?: 0f) >= 0.5f

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

    private fun canonicalizeOrNull(url: String): CanonUrl? =
        when (val r = url.canonicalize()) {
            is CanonicalParseResult.Success -> r.canonUrl
            else -> null
        }

    private suspend fun resolveFinalUrl(raw: String): String? =
        withContext(Dispatchers.IO) {
            when (val res = LinkChecker.resolveUrl(raw)) {
                is LinkChecker.UrlResolutionResult.Success -> res.finalUrl
                else -> null
            }
        }

    // Shared: remember last URL in memory + persist for process restarts
    private fun rememberUrl(context: Context, url: String) {
        MainActivity.lastOpenedLink = url
        val prefs = context.getSharedPreferences("sneaky_linky_prefs", Context.MODE_PRIVATE)
        prefs.edit { putString("last_url", url) }
    }
}
