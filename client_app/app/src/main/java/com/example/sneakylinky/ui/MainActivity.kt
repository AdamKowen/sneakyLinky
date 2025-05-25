package com.example.sneakylinky.ui

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
import kotlin.math.abs

import androidx.compose.ui.unit.dp

class MainActivity : AppCompatActivity() {

    private lateinit var editTextUrl: EditText
    private lateinit var checkButton: Button
    val Int.dp get() = (this * Resources.getSystem().displayMetrics.density).toInt()



    // uses retrofit service
    private val apiService = RetrofitClient.apiService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        val viewPager = findViewById<ViewPager2>(R.id.viewPager).apply {
            // let neighbours peek & give every page the same gutter
            clipToPadding = false           // keeps pages from filling the whole viewport
            setPadding(32.dp, 0, 32.dp, 0)  // 16 dp each side + the Card’s own 16 dp margin
            offscreenPageLimit = 3
            adapter = CardAdapter()
        }

        // אפקט שמראה את הכרטיס הבא מבצבץ
        viewPager.offscreenPageLimit = 3
        val pageMargin = resources.getDimensionPixelOffset(R.dimen.pageMargin)
        val pageOffset = resources.getDimensionPixelOffset(R.dimen.offset)




        viewPager.setPageTransformer { page, position ->
            // מרחיק קלות את הכרטיסים האחרים הצידה
            page.translationX = position * -40.dp.toFloat()

            // כיווץ קטן לכרטיסים מאחור
            page.scaleY = 1f - 0.05f * abs(position)

            // הנוכחי תמיד למעלה
            page.translationZ = -abs(position)
        }
        viewPager.setPageTransformer { page, position ->
            page.translationX = position * -40.dp.toFloat()
            page.scaleY = 0.95f + (1 - abs(position)) * 0.05f
        }

        viewPager.apply {
            clipToPadding = false       // להשאיר false כדי שייראו את הבא מאחור
            setPadding(0, 0, 0, 0)      // לא צריך padding צדדי
            offscreenPageLimit = 3
        }



        val intentData: Uri? = intent?.data
        intentData?.let { uri ->
            val url = uri.toString()
            // checks if link is safe and open in browser
            launchInSelectedBrowser(this, url)
        }


        val browsers = getInstalledBrowsers(this@MainActivity)
        Log.d("Browsers", "Detected browsers: ${browsers.joinToString { it.activityInfo.packageName }}")

    }

    private fun checkUrl(url: String) {
        lifecycleScope.launch {
            try {
                val response = apiService.checkUrl(mapOf("url" to url))
                if (response.status == "safe") {
                    Toast.makeText(this@MainActivity,
                        "Safe link!\n${response.message}\nSafe: ${response.details.safe}/${response.details.total}\nMore info: ${response.permalink}",
                        Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@MainActivity,
                        "Unsafe link!\n${response.message}",
                        Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun updateLink(link: String) {
        runOnUiThread {
            editTextUrl.setText(link)
        }
    }
}
