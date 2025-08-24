package com.example.sneakylinky.service

import android.content.Context
import com.example.sneakylinky.SneakyLinkyApp
import com.example.sneakylinky.service.aianalysis.UrlAnalyzer
import com.example.sneakylinky.service.urlanalyzer.CanonicalParseResult
import com.example.sneakylinky.service.urlanalyzer.canonicalize
import com.example.sneakylinky.service.urlanalyzer.isLocalSafe
import com.example.sneakylinky.ui.MainActivity
import com.example.sneakylinky.util.UiNotices
import com.example.sneakylinky.util.launchInSelectedBrowser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.content.edit
import com.example.sneakylinky.service.report.HistoryStore
import com.example.sneakylinky.service.report.LocalCheck
import com.example.sneakylinky.service.report.RemoteStatus
import androidx.core.net.toUri

object LinkFlow {

    // One entry-point used by both activities
    suspend fun runLinkFlow(
        context: Context,
        raw: String,
        contextText: String? = null
    ) {
        // 0) Create a new history row for this run
        val runId = HistoryStore.createRun(context, raw, contextText)

        val finalUrl = resolveFinalUrl(raw) ?: run {
            rememberUrl(context, raw)
            HistoryStore.markLocal(context, runId, LocalCheck.ERROR, null, null)
            UiNotices.showWarning(context, raw, "Internal error: could not verify link safely")
            return
        }

        val canon = canonicalizeOrNull(finalUrl) ?: run {
            rememberUrl(context, finalUrl)
            HistoryStore.markLocal(context, runId, LocalCheck.ERROR, finalUrl, null)
            UiNotices.showWarning(context, finalUrl, "Internal error: could not verify link safely")
            return
        }

        // Mark local check start/result
        val isSafe = withContext(Dispatchers.IO) { canon.isLocalSafe() }
        // import android.net.Uri
        val host = finalUrl.toUri().host
        HistoryStore.markLocal(context, runId,
            if (isSafe) LocalCheck.SAFE else LocalCheck.SUSPICIOUS,
            finalUrl,
            host
        )


        if (!isSafe) {
            rememberUrl(context, finalUrl)
            UiNotices.showWarning(context, finalUrl, "Link found to be suspicious. Proceed with caution.")
            return
        }

        // Safe locally -> open browser
        rememberUrl(context, finalUrl)
        launchInSelectedBrowser(context, finalUrl)
        HistoryStore.markOpened(context, runId, true)

        // Remote/AI scan
        HistoryStore.markRemote(context, runId, RemoteStatus.RUNNING, null)
        SneakyLinkyApp.appScope.launch {
            runCatching { UrlAnalyzer.analyze(finalUrl) }
                .onSuccess { ai ->
                    val status = if (ai.phishingScore >= 0.5f) RemoteStatus.RISK else RemoteStatus.SAFE
                    HistoryStore.markRemote(context, runId, status, ai.phishingScore)
                    UiNotices.safeToast(
                        context,
                        if (status == RemoteStatus.RISK) "Potential risk detected. Stay alert!⚠️" else "Sneaky Approves ✅"
                    )
                }
                .onFailure {
                    HistoryStore.markRemote(context, runId, RemoteStatus.ERROR, null)
                    UiNotices.safeToast(context, "Remote scan error")
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

    private fun canonicalizeOrNull(url: String): com.example.sneakylinky.service.urlanalyzer.CanonUrl? =
        when (val r = url.canonicalize()) {
            is CanonicalParseResult.Success -> r.canonUrl
            else -> null
        }

    // Remember last URL in memory + persist for process restarts
    private fun rememberUrl(context: Context, url: String) {
        com.example.sneakylinky.ui.MainActivity.lastOpenedLink = url
        val prefs = context.getSharedPreferences("sneaky_linky_prefs", Context.MODE_PRIVATE)
        prefs.edit { putString("last_url", url) }
    }
}
