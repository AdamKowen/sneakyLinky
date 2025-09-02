package com.example.sneakylinky.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.example.sneakylinky.R
import com.example.sneakylinky.service.LinkFlow
import com.example.sneakylinky.service.MyAccessibilityService
import com.example.sneakylinky.service.RetrofitClient
import com.example.sneakylinky.service.hotsetdatabase.HotsetSyncScheduler
import com.example.sneakylinky.service.serveranalysis.UrlAnalyzer
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


    // At the top of MainActivity (inside the class)
    private var tabsAreVisible = true



    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        android.util.Log.d("ACT_TRACE", "Main started")
        super.onCreate(savedInstanceState)

        HotsetSyncScheduler.scheduleWeekly(this)

        // This is the key line to handle edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)


        // Apply insets to your real content root (the root layout inside activity_main)
        val contentRoot = findViewById<ViewGroup>(android.R.id.content)
        contentRoot.applyInsetsToFirstChild()

        // Make sure scrolling content can scroll above IME
        // (if you have RecyclerViews / ViewPager2, this helps)
        contentRoot.clipToPadding = false

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

        val tabs = findViewById<com.google.android.material.tabs.TabLayout>(R.id.bottomTabs)

        try {
            com.google.android.material.tabs.TabLayoutMediator(tabs, viewPager) { tab, position ->
                val v = layoutInflater.inflate(R.layout.tab_icon, tabs, false)
                val iv = v.findViewById<ImageView>(R.id.tabIcon)
                iv.setImageResource(iconFor(position))

                iv.imageTintList = ContextCompat.getColorStateList(this, R.color.tab_icon_tint)


                // if you ever override size from code:
                val lp = iv.layoutParams
                lp.width  = (28 * resources.displayMetrics.density).toInt()
                lp.height = (28 * resources.displayMetrics.density).toInt()
                iv.layoutParams = lp
                iv.requestLayout()


                tab.customView = v
            }.attach()
        } catch (e: Throwable) {
            // Fallback to manual wiring if mediator class is missing
            setupTabsManually(tabs, viewPager)
        }


        // Close keyboard as soon as user starts swiping between cards
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrollStateChanged(state: Int) {
                // When user starts dragging, hide IME to avoid "squeezed" look
                if (state == ViewPager2.SCROLL_STATE_DRAGGING) {
                    hideKeyboardAndClearFocus()
                }
            }
        })

        installImeAwareTabsHider(tabs)






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



    }

    // Small pop animation on selection
    private fun animateTab(tab: com.google.android.material.tabs.TabLayout.Tab?, selected: Boolean) {
        val view = tab?.customView ?: tab?.view   // prefer customView if exists
        val up = -4 * resources.displayMetrics.density // -4dp
        val scale = if (selected) 1.15f else 1f
        val transY = if (selected) up else 0f

        view?.animate()
            ?.scaleX(scale)
            ?.scaleY(scale)
            ?.translationY(transY)
            ?.setDuration(150)
            ?.start()
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


    // Hide IME and clear focus (comments in English only)
    private fun hideKeyboardAndClearFocus() {
        val view = currentFocus ?: window.decorView.rootView
        val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
        view.clearFocus()
    }



    private fun iconFor(position: Int): Int {
        return when (position) {
            0 -> R.drawable.link
            1 -> R.drawable.message
            2 -> R.drawable.comb_arrow
            3 -> R.drawable.browser
            else -> R.drawable.recent
        }
    }


    // Manual wiring when TabLayoutMediator is not available
    private fun setupTabsManually(
        tabs: com.google.android.material.tabs.TabLayout,
        viewPager: androidx.viewpager2.widget.ViewPager2
    ) {
        // Create tabs with icons
        repeat(4) { idx ->
            val tab = tabs.newTab().setIcon(iconFor(idx))
            tabs.addTab(tab, idx == 0)
        }

        // Sync: tab -> pager
        tabs.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                tab.view.isSelected = true
                viewPager.currentItem = tab.position
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                tab.view.isSelected = false
            }
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
        })

        // Sync: pager -> tab
        viewPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (position in 0 until tabs.tabCount) {
                    val tab = tabs.getTabAt(position)
                    if (tab != null && !tab.isSelected) {
                        tabs.selectTab(tab)
                    }
                }
            }
        })

    }

    // Call this from onCreate() AFTER you get 'tabs'
    private fun installImeAwareTabsHider(tabs: com.google.android.material.tabs.TabLayout) {
        val rootDecor = window.decorView // decorView reliably receives IME insets

        // 1) Listen to window insets and toggle tabs
        ViewCompat.setOnApplyWindowInsetsListener(rootDecor) { _, insets ->
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            setTabsVisible(!imeVisible, tabs)
            insets // don't consume
        }

        // 2) Initialize state once (covers the case when IME is already showing)
        tabs.post {
            val current = ViewCompat.getRootWindowInsets(tabs)
            val imeVisibleNow = current?.isVisible(WindowInsetsCompat.Type.ime()) == true
            setTabsVisible(!imeVisibleNow, tabs)
        }

        // 3) Ensure an initial pass of insets happens
        ViewCompat.requestApplyInsets(rootDecor)
    }



    // Smoothly toggle tabs visibility
    // Show with a short animation; hide instantly when IME appears.
    private fun setTabsVisible(visible: Boolean, tabs: com.google.android.material.tabs.TabLayout) {
        if (visible == tabsAreVisible) return
        tabsAreVisible = visible

        // cancel any running animations to avoid "stutter"
        tabs.animate().cancel()

        if (!visible) {
            // IME is visible -> hide immediately (no animation)
            // (instant hide prevents the keyboard from overlapping mid-animation)
            tabs.visibility = View.GONE
            tabs.alpha = 1f
            tabs.translationY = 0f
            return
        }

        // Becoming visible -> animate in (nice but not blocking IME)
        if (tabs.height == 0 || !tabs.isLaidOut) {
            // If not laid out yet, just show without animation
            tabs.visibility = View.VISIBLE
            return
        }

        tabs.apply {
            if (visibility != View.VISIBLE) visibility = View.VISIBLE
            alpha = 0f
            translationY = height.toFloat()
            animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(140L)
                .start()
        }
    }


    /**
     * Apply padding for system bars top/bottom and switch bottom to IME when keyboard is visible.
     * Works with edge-to-edge (decorFitsSystemWindows=false).
     */
    fun View.applySystemBarsAndImePadding() {
        // Animate with IME for smooth transition
        val cb = object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
            private var startBottom = 0
            override fun onPrepare(animation: WindowInsetsAnimationCompat) {
                startBottom = paddingBottom
            }
            override fun onProgress(
                insets: WindowInsetsCompat,
                runningAnimations: MutableList<WindowInsetsAnimationCompat>
            ): WindowInsetsCompat {
                val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
                setPadding(paddingLeft, paddingTop, paddingRight, maxOf(sys, ime))
                return insets
            }
        }
        ViewCompat.setWindowInsetsAnimationCallback(this, cb)

        // Initial + non-animated changes
        ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(sys.left, sys.top, sys.right, maxOf(sys.bottom, ime.bottom))
            WindowInsetsCompat.CONSUMED
        }

        // Ensure the first pass happens
        requestApplyInsetsWhenAttached()
    }

    /** Convenience for view hierarchies where the root child is the content container. */
    fun ViewGroup.applyInsetsToFirstChild() {
        (getChildAt(0) ?: this).applySystemBarsAndImePadding()
    }

    private fun View.requestApplyInsetsWhenAttached() {
        if (isAttachedToWindow) {
            requestApplyInsets()
        } else {
            addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    v.removeOnAttachStateChangeListener(this)
                    v.requestApplyInsets()
                }
                override fun onViewDetachedFromWindow(v: View) {}
            })
        }
    }


}




