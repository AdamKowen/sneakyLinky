package com.example.sneakylinky.ui

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.sneakylinky.service.LinkFlow
import com.example.sneakylinky.service.MyAccessibilityService
import kotlinx.coroutines.launch

class LinkRelayActivity : AppCompatActivity() {

    private val TAG = "LinkRelay"
    private val SEP = " $$$ SEPERATOR! $$$ "

    override fun onCreate(savedInstanceState: Bundle?) {

        overridePendingTransition(0, 0)

        super.onCreate(savedInstanceState)

        val raw = intent?.dataString ?: run {
            Log.d(TAG, "No dataString in intent; finishing.")
            finish(); return
        }
        Log.d(TAG, "raw='$raw'")

        // Get whatever the service cached (joined messages string).
        val joined = com.example.sneakylinky.LinkContextCache.surroundingTxt
        val selectedMsg = joined?.let { pickMessageFromJoined(it, raw) }

        Log.d(TAG, "joinedNull=${joined == null} selectedLen=${selectedMsg?.length}")

        // Push the selected message to the UI card (if available)
        selectedMsg?.let { MyAccessibilityService.pushTextToUi(it) }

        lifecycleScope.launch {
            LinkFlow.runLinkFlow(
                context = this@LinkRelayActivity,
                raw = raw,
                contextText = selectedMsg // pass only the single matched message
            )
            finishAndRemoveTask()
        }

        overridePendingTransition(0, 0) // ensure no exit animation
    }

    // ---------------- helpers (English comments only) ----------------

    private fun pickMessageFromJoined(joined: String, raw: String): String? {
        // Normalize inputs
        fun stripTrail(s: String) = s.trim().trimEnd('.', ',', ';', ':', ')', ']', '}', '…', '！', '、')
        fun norm(s: String) = s.replace(Regex("""\s+"""), " ").trim()

        val needle = stripTrail(norm(raw))
        val noScheme = needle.replace(Regex("""^https?://"""), "")
        val domain = noScheme.substringBefore('/').lowercase()

        val parts = joined.split(SEP).map { it.trim() }.filter { it.isNotEmpty() }

        // 1) exact match (full URL)
        parts.firstOrNull { norm(it).contains(needle) }?.let { return it }
        // 2) no-scheme match
        parts.firstOrNull { norm(it).contains(noScheme) }?.let { return it }
        // 3) domain-only (last resort)
        if (domain.isNotBlank()) {
            parts.firstOrNull { norm(it).lowercase().contains(domain) }?.let { return it }
        }
        return null
    }


}
