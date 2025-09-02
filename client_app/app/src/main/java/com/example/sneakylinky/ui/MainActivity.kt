package com.example.sneakylinky.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

import android.content.Intent
import android.content.res.Resources
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.example.sneakylinky.R
import com.example.sneakylinky.service.RetrofitClient
import kotlinx.coroutines.launch
import com.example.sneakylinky.util.*
import android.util.Log
import com.example.sneakylinky.service.LinkChecker
import com.example.sneakylinky.service.serveranalysis.UrlAnalyzer
import com.example.sneakylinky.service.urlanalyzer.CanonUrl
import kotlin.math.abs
import com.example.sneakylinky.service.urlanalyzer.toCanonUrlOrNull


import com.example.sneakylinky.service.urlanalyzer.populateTestData
import com.example.sneakylinky.service.urlanalyzer.DecisionSource
import com.example.sneakylinky.service.urlanalyzer.Verdict
import com.example.sneakylinky.service.urlanalyzer.evaluateUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {


    companion object {
        @Volatile var lastOpenedLink: String? = null   // ← static field
    }

    val Int.dp get() = (this * Resources.getSystem().displayMetrics.density).toInt()

    private var cardAdapter: CardAdapter? = null

    // uses retrofit service
    private val apiService = RetrofitClient.apiService


    override fun onCreate(savedInstanceState: Bundle?) {

        android.util.Log.d("ACT_TRACE", "Main started")

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestNotificationPermissionIfNeeded()

        // ─── TEMPORARY: Populate test data into Room tables ─────────────────────
        populateTestData()  // <— call the helper from urltesting.kt
        // ─────────────────────────────────────────────────────────────────────────


        cardAdapter = CardAdapter(this) { raw ->
            lifecycleScope.launch {
                resolveAndProcess(raw)
            }
        }


        val viewPager = findViewById<ViewPager2>(R.id.viewPager).apply {
            clipToPadding = false // Keep false to show neighboring pages
            val pageMarginPx = resources.getDimensionPixelOffset(R.dimen.pageMargin)
            val pageOffsetPx = resources.getDimensionPixelOffset(R.dimen.offset) // Ensure this is defined

            setPadding(pageMarginPx, 0, pageMarginPx, 0)
            offscreenPageLimit = 3

            adapter = cardAdapter
        }

        // shows the next card from the side
        viewPager.offscreenPageLimit = 3
        val pageMargin = resources.getDimensionPixelOffset(R.dimen.pageMargin)
        val pageOffset = resources.getDimensionPixelOffset(R.dimen.offset)

        viewPager.setPageTransformer { page, position ->
            // moves cards to the side
            page.translationX = position * -40.dp.toFloat()

            // shrinks the cards as they move to the side
            page.scaleY = 1f - 0.05f * abs(position)

            // the current card is in the middle
            page.translationZ = -abs(position)
        }

        viewPager.apply {
            clipToPadding = false       // false to show neighboring pages
            setPadding(0, 0, 0, 0)      // no padding
        }


        handleIncomingLink(intent)


        val browsers = getInstalledBrowsers(this@MainActivity)
        Log.d("Browsers", "Detected browsers: ${browsers.joinToString { it.activityInfo.packageName }}")
    }

    override fun onResume() {
        super.onResume()
        lastOpenedLink?.let { cardAdapter?.updateCard1Link(it) }
    }


    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingLink(intent)
    }


    fun handleIncomingLink(intent: Intent?) {
        val raw = intent?.dataString ?: return   // ← get URI string safely
        lifecycleScope.launch { resolveAndProcess(raw) }
    }

    suspend fun resolveAndProcess(raw: String) {
        val result = withContext(Dispatchers.IO) {
            LinkChecker.resolveUrl(raw)
        }

        when (result) {
            is LinkChecker.UrlResolutionResult.Success -> {
                val canon = canonicalizeOrShowError(result.finalUrl) ?: return
                cardAdapter?.updateCard1Link(result.finalUrl)
                isLinkSafeLocally(canon)
            }
            is LinkChecker.UrlResolutionResult.Failure -> {
                showWarning(raw, "Internal error: could not verify link safely")
            }
        }
    }

    fun canonicalizeOrShowError(raw: String): CanonUrl? {
        val canon = raw.toCanonUrlOrNull()
        return canon ?: run {
            showWarning(raw, "Internal error: could not verify link safely")
            null
        }
    }


    suspend fun isLinkSafeLocally(canon: CanonUrl) {
        val eval = evaluateUrl(canon.originalUrl)
        if (eval.verdict != Verdict.SAFE) {
            showWarning(
                canon.originalUrl,
                "Link found to be suspicious. Proceed with caution."
            )
            return
        }
        launchInSelectedBrowser(this, canon.originalUrl)
        remoteCheckAsync(canon.originalUrl)
    }



    fun showWarning(url: String, reason: String) {
        val intent = Intent(this, LinkWarningActivity::class.java).apply {
            putExtra("url", url)
            putExtra("warningText", reason)   //-warningTextView
        }
        startActivity(intent)
    }


    fun remoteCheckAsync(url: String) = lifecycleScope.launch {
        withContext(Dispatchers.IO) {
            runCatching { UrlAnalyzer.analyze(url) }
        }.onSuccess { ai ->
            if (ai.phishingScore >= 0.5f) {    // threshold – tweak
                showHeadsUp("Potential risk detected. Stay alert!") // Snackbar/Dialog
                //Toast.makeText(this@MainActivity,
                   // "Potential risk detected. Stay alert!", Toast.LENGTH_LONG).show()
            }
        }
    }


    /* heads-up via Toast – safe even if Activity already finished */
    private fun showHeadsUp(msg: String) =
        android.widget.Toast.makeText(applicationContext, msg, android.widget.Toast.LENGTH_LONG).show()



    private fun requestNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(permission), 1001)
            }
        }
    }


}
