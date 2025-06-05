package com.example.sneakylinky.ui


import android.content.Intent
import android.content.res.Resources
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.example.sneakylinky.R
import com.example.sneakylinky.service.RetrofitClient
import kotlinx.coroutines.launch
import com.example.sneakylinky.util.*
import android.net.Uri
import android.util.Log
import com.example.sneakylinky.service.LinkChecker
import com.example.sneakylinky.service.aianalysis.UrlAnalyzer
import com.example.sneakylinky.service.urlanalyzer.CanonUrl
import com.example.sneakylinky.service.urlanalyzer.canonicalize
import kotlin.math.abs


import com.example.sneakylinky.service.urlanalyzer.CanonicalParseResult
import com.example.sneakylinky.service.urlanalyzer.canonicalize
import com.example.sneakylinky.service.urlanalyzer.isLocalSafe
import com.example.sneakylinky.service.urlanalyzer.populateTestData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {


    companion object {
        @Volatile var lastOpenedLink: String? = null   // ← static field
    }

    val Int.dp get() = (this * Resources.getSystem().displayMetrics.density).toInt()

    private var cardAdapter: CardAdapter? = null

    // uses retrofit service
    private val apiService = RetrofitClient.apiService



//    override fun onCreate(savedInstanceState: Bundle?) {
//
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_main)
//
//        // ─── TEMPORARY: Populate test data into Room tables ─────────────────────
//        populateTestData()  // <— call the helper from urltesting.kt
//        // ─────────────────────────────────────────────────────────────────────────
//
//
//        val viewPager = findViewById<ViewPager2>(R.id.viewPager).apply {
//            clipToPadding = false // Keep false to show neighboring pages
//            // Ensure you have R.dimen.pageMargin and R.dimen.offset defined in dimens.xml
//            val pageMarginPx = resources.getDimensionPixelOffset(R.dimen.pageMargin)
//            val pageOffsetPx = resources.getDimensionPixelOffset(R.dimen.offset) // Ensure this is defined
//
//            setPadding(pageMarginPx, 0, pageMarginPx, 0)
//            offscreenPageLimit = 3
//
//            // Initialize the adapter and pass the callback function
//            // The lambda (url -> checkUrl(url)) is the callback
//            cardAdapter = CardAdapter { url ->
//                // This is where the URL from the CardAdapter is received
//                // and the checkUrl logic (in MainActivity) is triggered.
//
//                // ------------------------------------------------------------------------------------------------
//                val canonRes = url.canonicalize()   /// returns CanonicalParseResult which has two subclasses: Error and Success
//
//                if (canonRes is CanonicalParseResult.Success) {
//                    val canon = canonRes.canonUrl  /// CanonUrl is a data class with all the parsed URL components only if parsing was successful
//                    Log.d("DB_TEST", "canonicalize($url) = $canon")
//
//                    lifecycleScope.launch {
//                        val isSafe = canon.isLocalSafe()
//                        Log.d("URL_TEST", "isUrlLocalSafe($url) = $isSafe")
//                        /// we checked the db -> we run local static checks : true if passes all \ false if fails any (for now)
//                        /// todo handle the result of isLocalSafe
//                    }
//
//                } else if (canonRes is CanonicalParseResult.Error) {
//                    Log.d("DB_TEST", "Error parsing URL: ${canonRes.reason}")
//                    ///todo handle the error case
//                }
//                // ------------------------------------------------------------------------------------------------
//                checkUrl(url)
//            }
//            adapter = cardAdapter
//        }
//
//        // shows the next card from the side
//        viewPager.offscreenPageLimit = 3
//        val pageMargin = resources.getDimensionPixelOffset(R.dimen.pageMargin)
//        val pageOffset = resources.getDimensionPixelOffset(R.dimen.offset)
//
//
//
//        viewPager.setPageTransformer { page, position ->
//            // moves cards to the side
//            page.translationX = position * -40.dp.toFloat()
//
//            // shrinks the cards as they move to the side
//            page.scaleY = 1f - 0.05f * abs(position)
//
//            // the current card is in the middle
//            page.translationZ = -abs(position)
//        }
//
//
//        viewPager.apply {
//            clipToPadding = false       // false to show neighboring pages
//            setPadding(0, 0, 0, 0)      // no padding
//        }
//
//
//        handleIncomingLink(intent)
//
//
//        val browsers = getInstalledBrowsers(this@MainActivity)
//        Log.d("Browsers", "Detected browsers: ${browsers.joinToString { it.activityInfo.packageName }}")
//
//
//
//    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // ─── TEMPORARY: Populate test data into Room tables ─────────────────────
        populateTestData()  // <— call the helper from urltesting.kt
        // ─────────────────────────────────────────────────────────────────────────


        cardAdapter = CardAdapter { raw ->
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

    private fun checkUrl(url: String) {
        lifecycleScope.launch {
            try {
                val response = apiService.checkUrl(mapOf("url" to url))
                if (response.status == "safe") {
                    Toast.makeText(this@MainActivity,
                        "Safe link!\n${response.message}", Toast.LENGTH_LONG).show()
                } else {
                    val intent = Intent(this@MainActivity, LinkWarningActivity::class.java).apply {
                        putExtra("url", url)
                        putExtra("warningText", "Unsafe link!\n${response.message}")
                    }
                    startActivity(intent)
                }

            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }


    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingLink(intent)
    }


    private fun canonicalizeOrShowError(raw: String): CanonUrl? {
        val result = raw.canonicalize()
        return when (result) {
            is CanonicalParseResult.Success -> result.canonUrl
            is CanonicalParseResult.Error   -> {
                // minimal UX: toast + stay in app
                Toast.makeText(this, "Invalid URL", Toast.LENGTH_SHORT).show()
                null
            }
        }
    }


    private fun showWarning(url: String, reason: String) {
        val intent = Intent(this, LinkWarningActivity::class.java).apply {
            putExtra("url", url)
            putExtra("warningText", reason)   //-warningTextView
        }
        startActivity(intent)
    }


    private suspend fun localFlow(canon: CanonUrl) {
        if (!canon.isLocalSafe()) {
            showWarning(canon.originalUrl,
                "Local static checks failed. Proceed with caution.")
            return
        }
        launchInSelectedBrowser(this, canon.originalUrl)
        remoteCheckAsync(canon.originalUrl)
    }



    private fun handleIncomingLink(intent: Intent?) {
        val raw = intent?.dataString ?: return   // ← get URI string safely
        lifecycleScope.launch { resolveAndProcess(raw) }
    }

    private suspend fun resolveAndProcess(raw: String) {
        // 1) resolve redirects (network – run on IO)
//        val result = withContext(Dispatchers.IO) {
//            LinkChecker.resolveUrl(raw)
//        }
//
//        when (result) {
//            is LinkChecker.UrlResolutionResult.Success -> {
//                val finalUrl = result.finalUrl
//                val canon = canonicalizeOrShowError(finalUrl) ?: return
//                cardAdapter?.updateCard1Link(canon.originalUrl)
//                localFlow(canon)
//            }
//            is LinkChecker.UrlResolutionResult.Failure -> {
//                showWarning(raw, "Could not resolve URL (${result.error.description})")
//            }
//        }




        // added just for testing purposes! :
        val canon = canonicalizeOrShowError(raw) ?: return
        cardAdapter?.updateCard1Link(canon.originalUrl)
        localFlow(canon)   // ← still runs local DB + static checks

    }

    private fun remoteCheckAsync(url: String) = lifecycleScope.launch {
        withContext(Dispatchers.IO) {
            runCatching { UrlAnalyzer.analyze(url) }
        }.onSuccess { ai ->
            if (ai.phishingScore >= 0.5f) {    // threshold – tweak
                showHeadsUp("Potential risk detected. Stay alert!") // Snackbar/Dialog
            }
        }
    }

    private fun showHeadsUp(msg: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("SneakyLinky")
            .setMessage(msg)
            .setPositiveButton("OK", null)
            .show()
    }





}
