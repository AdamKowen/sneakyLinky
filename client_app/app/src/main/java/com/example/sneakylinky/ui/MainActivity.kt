package com.example.sneakylinky.ui

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

class MainActivity : AppCompatActivity() {

    private lateinit var editTextUrl: EditText
    private lateinit var checkButton: Button

    // uses retrofit service
    private val apiService = RetrofitClient.apiService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val viewPager = findViewById<ViewPager2>(R.id.viewPager)
        viewPager.adapter = CardAdapter()

        // אפקט שמראה את הכרטיס הבא מבצבץ
        viewPager.offscreenPageLimit = 3
        val pageMargin = resources.getDimensionPixelOffset(R.dimen.pageMargin)
        val pageOffset = resources.getDimensionPixelOffset(R.dimen.offset)

        viewPager.setPageTransformer { page, position ->
            val offset = position * -(2 * pageOffset + pageMargin)
            if (viewPager.orientation == ViewPager2.ORIENTATION_HORIZONTAL) {
                page.translationX = offset
            } else {
                page.translationY = offset
            }
        }
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
