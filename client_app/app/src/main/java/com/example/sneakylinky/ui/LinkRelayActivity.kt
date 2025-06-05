package com.example.sneakylinky.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.sneakylinky.service.aianalysis.UrlAnalyzer
import com.example.sneakylinky.service.urlanalyzer.CanonicalParseResult
import com.example.sneakylinky.service.urlanalyzer.canonicalize
import com.example.sneakylinky.service.urlanalyzer.isLocalSafe
import com.example.sneakylinky.util.launchInSelectedBrowser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class LinkRelayActivity : AppCompatActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val raw = intent?.dataString
        if (raw == null) {
            finish(); return
        }

        // 1) canonicalize
        val canon = when (val res = raw.canonicalize()) {
            is CanonicalParseResult.Success -> res.canonUrl
            else -> { showWarning(raw, "Invalid URL"); finish(); return }
        }

        // 2) very quick local check (DB/static) – block ≤ 15 ms
        val isSafe = runBlocking {
            withContext(Dispatchers.IO) { canon.isLocalSafe() }
        }

        if (isSafe) {
            // open chosen browser immediately (no animation)
            launchInSelectedBrowser(this, raw)

            // deep AI scan in background
            lifecycleScope.launch(Dispatchers.IO) { remoteScanAsync(raw) }
        } else {
            showWarning(raw, "Local checks flagged this link as risky.")
        }


        MainActivity.lastOpenedLink = raw  // save link for UI


        // nothing visual to show – close relay
        finish()
    }


    /* -------------------- helpers -------------------- */

    private suspend fun remoteScanAsync(url: String) {
        runCatching { UrlAnalyzer.analyze(url) }
            .onSuccess { ai ->
                if (ai.phishingScore >= 0.5f) {
                    showHeadsUp("Potential risk detected. Stay alert!")
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

    private fun showHeadsUp(msg: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("SneakyLinky")
            .setMessage(msg)
            .setPositiveButton("OK", null)
            .show()
    }
}
