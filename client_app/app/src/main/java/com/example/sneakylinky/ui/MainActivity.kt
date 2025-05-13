package com.example.sneakylinky.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
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

        // connects to the accesbility service
        MyAccessibilityService.setActivity(this)

        editTextUrl = findViewById(R.id.editTextUrl)
        checkButton = findViewById(R.id.checkButton)

        checkButton.setOnClickListener {
            val url = editTextUrl.text.toString()
            if (url.isNotEmpty()) {
                checkUrl(url)
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
