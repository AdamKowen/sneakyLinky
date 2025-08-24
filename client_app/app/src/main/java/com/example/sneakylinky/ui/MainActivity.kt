package com.example.sneakylinky.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.example.sneakylinky.R
import com.example.sneakylinky.service.LinkFlow
import com.example.sneakylinky.service.MyAccessibilityService
import com.example.sneakylinky.service.RetrofitClient
import com.example.sneakylinky.service.aianalysis.UrlAnalyzer
import com.example.sneakylinky.service.urlanalyzer.populateTestData
import com.example.sneakylinky.util.UiNotices
import com.example.sneakylinky.util.getInstalledBrowsers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    companion object {
        @Volatile var lastOpenedLink: String? = null
    }

    // dp extension for quick integer-to-dp conversion
    val Int.dp get() = (this * Resources.getSystem().displayMetrics.density).toInt()

    private var cardAdapter: CardAdapter? = null

    // Keep Retrofit service instance if needed elsewhere in the screen
    private val apiService = RetrofitClient.apiService

    override fun onCreate(savedInstanceState: Bundle?) {
        android.util.Log.d("ACT_TRACE", "Main started")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        // Keep classic insets behavior (not edge-to-edge) so adjustResize works predictably
        WindowCompat.setDecorFitsSystemWindows(window, true)

        val root = findViewById<View>(R.id.rootLayout)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val bottom = maxOf(ime.bottom, sys.bottom)  // use IME bottom when keyboard is visible
            v.setPadding(v.paddingLeft, sys.top, v.paddingRight, bottom)
            insets  // do not consume; let children get insets if they need
        }

        requestNotificationPermissionIfNeeded()

        // TEMP – seed local DB/test tables if your helper requires it
        populateTestData()

        // Set up cards; "Analyze" button now delegates to LinkFlow
        cardAdapter = CardAdapter(
            this,
            onCheckUrl = { raw ->
                lifecycleScope.launch {
                    LinkFlow.runLinkFlow(this@MainActivity, raw)
                }
            },
            onAnalyzeText = { pasted ->
                analyzeText(pasted)
            }
        )

        // Provide Activity ref to AccessibilityService (for UI updates if needed)
        MyAccessibilityService.setActivity(this)

        val viewPager = findViewById<ViewPager2>(R.id.viewPager).apply {
            clipToPadding = false
            val pageMarginPx = resources.getDimensionPixelOffset(R.dimen.pageMargin)
            val pageOffsetPx = resources.getDimensionPixelOffset(R.dimen.offset)

            setPadding(pageMarginPx, 0, pageMarginPx, 0)
            offscreenPageLimit = 3

            adapter = cardAdapter
        }

        // Card carousel behavior/transform
        viewPager.offscreenPageLimit = 3
        val pageMargin = resources.getDimensionPixelOffset(R.dimen.pageMargin)
        val pageOffset = resources.getDimensionPixelOffset(R.dimen.offset)

        viewPager.setPageTransformer { page, position ->
            // Slide cards sideways
            page.translationX = position * -40.dp.toFloat()
            // Slight scale for depth effect
            page.scaleY = 1f - 0.05f * abs(position)
            // Ensure center card is on top
            page.translationZ = -abs(position)
        }

        viewPager.apply {
            clipToPadding = false
            setPadding(0, 0, 0, 0)
        }

        // If MainActivity was opened as a browser target, handle incoming link
        handleIncomingLink(intent)

        // Optional – log installed browsers for debugging
        val browsers = getInstalledBrowsers(this@MainActivity)
        android.util.Log.d("Browsers", "Detected browsers: ${browsers.joinToString { it.activityInfo.packageName }}")


        // MainActivity.onCreate()
        window.decorView.systemUiVisibility = 0 // disable fullscreen flags if any


        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.viewPager)) { v, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, ime.bottom) // add bottom space when keyboard shows
            insets
        }

    }

    override fun onResume() {
        super.onResume()
        // Update UI with the last link opened (LinkFlow sets this)
        lastOpenedLink?.let { cardAdapter?.updateCard1Link(it) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingLink(intent)
    }

    // Handle external "view" intents and pass them into the unified flow
    fun handleIncomingLink(intent: Intent?) {
        val raw = intent?.dataString ?: return
        lifecycleScope.launch {
            LinkFlow.runLinkFlow(this@MainActivity, raw)
        }
    }

    // Analyze free text (not necessarily a URL) – keep as a separate action
    private fun analyzeText(text: String) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { UrlAnalyzer.analyze(text) }
            }

            result.onSuccess { ai ->
                if (ai.phishingScore >= 0.5f) {
                    UiNotices.safeToast(this@MainActivity, "Potential malicious text detected. Proceed with caution.")
                } else {
                    UiNotices.safeToast(this@MainActivity, "Text appears safe.")
                }
            }.onFailure { e ->
                Toast.makeText(
                    this@MainActivity,
                    "Error analyzing text: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // Keep notification permission request in UI layer
    private fun requestNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(permission), 1001)
            }
        }
    }

    // Public API for other components to push pasted text into the adapter
    fun updatePasteTextInAdapter(text: String) {
        cardAdapter?.updatePasteText(text)
    }
}
