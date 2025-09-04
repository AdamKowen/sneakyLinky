package com.example.sneakylinky.ui

import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.sneakylinky.R
import com.example.sneakylinky.util.launchInSelectedBrowser

class LinkWarningActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_link_warning)

        val warningText = intent.getStringExtra("warningText") ?: "This link may be unsafe."
        val url = intent.getStringExtra("url")

        val warningTextView: TextView = findViewById(R.id.warningTextView)
        val proceedButton: Button = findViewById(R.id.proceedButton)
        val closeButton: Button = findViewById(R.id.closeButton)
        val checkBox: CheckBox = findViewById(R.id.checkboxRisks)

        warningTextView.text = warningText

        closeButton.setOnClickListener { finish() }

        proceedButton.setOnClickListener {
            if (!checkBox.isChecked) {
                Toast.makeText(
                    this,
                    "Please confirm that you understand the risks",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                url?.let {
                    launchInSelectedBrowser(this@LinkWarningActivity, it)
                    finish()
                }
            }
        }
    }
}
