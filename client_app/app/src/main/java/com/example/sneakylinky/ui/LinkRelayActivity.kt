package com.example.sneakylinky.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.sneakylinky.service.LinkFlow
import kotlinx.coroutines.launch

class LinkRelayActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val raw = intent?.dataString ?: run { finish(); return }

        //If you have captured context text for this exact link, pass it in:
        val contextTxt = com.example.sneakylinky.LinkContextCache
            .takeIf { it.lastLink == raw }
            ?.surroundingTxt

        lifecycleScope.launch {
            LinkFlow.runLinkFlow(
                context = this@LinkRelayActivity,
                raw = raw,
                contextText = contextTxt
            )
            // Finish regardless – the activity is invisible by design
            finishAndRemoveTask()
        }
    }
}
