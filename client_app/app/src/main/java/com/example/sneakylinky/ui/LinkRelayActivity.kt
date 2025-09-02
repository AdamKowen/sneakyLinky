package com.example.sneakylinky.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.sneakylinky.SneakyLinkyApp
import com.example.sneakylinky.service.LinkChecker
import com.example.sneakylinky.service.serveranalysis.UrlAnalyzer
import com.example.sneakylinky.service.urlanalyzer.toCanonUrlOrNull
import com.example.sneakylinky.util.launchInSelectedBrowser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.sneakylinky.service.urlanalyzer.DecisionSource
import com.example.sneakylinky.service.urlanalyzer.Verdict
import com.example.sneakylinky.service.urlanalyzer.evaluateUrl


class LinkRelayActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // no setContentView → activity remains invisible
        val raw = intent?.dataString ?: run { finish(); return }

        /* ---------- blocking but fast (≤ ~50 ms) ---------- */
        val finalUrl = runBlocking {
            withContext(Dispatchers.IO) {
                when (val res = LinkChecker.resolveUrl(raw)) {
                    is LinkChecker.UrlResolutionResult.Success -> res.finalUrl
                    else -> raw                      // keep original if resolve failed
                }
            }
        }

        /* local decision: parse → lists → heuristics */
        val eval = runBlocking {
            withContext(Dispatchers.IO) { evaluateUrl(finalUrl) }
        }

        if (eval.verdict == Verdict.SAFE) {
            launchInSelectedBrowser(this, finalUrl)                // open browser
            SneakyLinkyApp.appScope.launch { remoteScanAsync(finalUrl) } // AI scan (bg)
        } else {
            val msg = if (eval.source == DecisionSource.PARSE_ERROR) {
                "Internal error: could not parse link"
            } else {
                "Link found to be suspicious. Proceed with caution."
            }
            showWarning(finalUrl, msg)
        }

        MainActivity.lastOpenedLink = finalUrl                     // remember for UI
        finishAndRemoveTask()

        /*
            /* toCanonUrlOrNull */
            val canon = finalUrl.toCanonUrlOrNull()
                ?: run { showWarning(finalUrl, "Internal error: could not parse link"); finish(); return }

            /* local DB / static check */
            val isSafe = runBlocking {
                withContext(Dispatchers.IO) { canon.isLocalSafe() }
            }

            if (isSafe) {
                launchInSelectedBrowser(this, finalUrl)                // open browser
                SneakyLinkyApp.appScope.launch { remoteScanAsync(finalUrl) } // AI scan (bg)
            } else {
                showWarning(finalUrl, "Link found to be suspicious. Proceed with caution.")
            }
            MainActivity.lastOpenedLink = finalUrl                     // remember for UI
            finishAndRemoveTask()
        */
                                   // vanish
}



private suspend fun remoteScanAsync(url: String) {
    runCatching { UrlAnalyzer.analyze(url) }
        .onSuccess { ai ->
            if (ai.phishingScore >= 0.5f) {
                withContext(Dispatchers.Main) {      // run on UI thread
                    safeShowToast("Potential risk detected. Stay alert!⚠\uFE0F ")
                }
            }
            else
            {
                withContext(Dispatchers.Main) {
                    showHeadsUp("Sneaky Approves ✅")
                }
            }
        }
}



private fun showWarning(url: String, msg: String) {
    // remember last link for MainActivity
    MainActivity.lastOpenedLink = url
    val i = Intent(this, LinkWarningActivity::class.java).apply {
        putExtra("url", url)
        putExtra("warningText", msg)
    }
    startActivity(i)
}


/* heads-up via Toast – safe even if Activity already finished */
private fun showHeadsUp(msg: String) =
    android.widget.Toast.makeText(applicationContext, msg, android.widget.Toast.LENGTH_LONG).show()


private fun safeShowToast(msg: String) {
    val hasPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else true

    if (hasPermission) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }
}


}
