package com.example.sneakylinky.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Resources
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.example.sneakylinky.R
import com.example.sneakylinky.service.RetrofitClient
import com.example.sneakylinky.service.MyAccessibilityService
import kotlinx.coroutines.launch
import com.example.sneakylinky.util.*
import android.net.Uri
import android.util.Log
import com.example.sneakylinky.service.urlanalyzer.canonicalize
import kotlin.math.abs


import kotlinx.coroutines.delay

class MainActivity : AppCompatActivity() {


    val Int.dp get() = (this * Resources.getSystem().displayMetrics.density).toInt()

    private var cardAdapter: CardAdapter? = null

    // uses retrofit service
    private val apiService = RetrofitClient.apiService



    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        val viewPager = findViewById<ViewPager2>(R.id.viewPager).apply {
            clipToPadding = false // Keep false to show neighboring pages
            // Ensure you have R.dimen.pageMargin and R.dimen.offset defined in dimens.xml
            val pageMarginPx = resources.getDimensionPixelOffset(R.dimen.pageMargin)
            val pageOffsetPx = resources.getDimensionPixelOffset(R.dimen.offset) // Ensure this is defined

            setPadding(pageMarginPx, 0, pageMarginPx, 0)
            offscreenPageLimit = 3

            // Initialize the adapter and pass the callback function
            // The lambda (url -> checkUrl(url)) is the callback
            cardAdapter = CardAdapter { url ->
                // This is where the URL from the CardAdapter is received
                // and the checkUrl logic (in MainActivity) is triggered.
                checkUrl(url)
            }
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
            clipToPadding = false       // false to shpw neighboring pages
            setPadding(0, 0, 0, 0)      // no padding
        }


        handleIncomingLink(intent)


        val browsers = getInstalledBrowsers(this@MainActivity)
        Log.d("Browsers", "Detected browsers: ${browsers.joinToString { it.activityInfo.packageName }}")

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


    private fun handleIncomingLink(intent: Intent?) {
        intent?.data?.let { uri ->
            val url = uri.toString()
            lifecycleScope.launch {
                cardAdapter?.updateCard1Link(url)
                val parsed = url.canonicalize()
                println(parsed)
                Log.d("DEBUG", "Link handled: $url")
                delay(300)
                launchInSelectedBrowser(this@MainActivity, url)
            }
        }
    }


}
