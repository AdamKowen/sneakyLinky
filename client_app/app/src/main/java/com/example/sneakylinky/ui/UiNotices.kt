package com.example.sneakylinky.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.sneakylinky.ui.LinkWarningActivity

object UiNotices {

    private val mainHandler = Handler(Looper.getMainLooper())

    // Always post Toast to the main thread
    @JvmStatic
    fun safeToast(context: Context, msg: CharSequence, customDurationMs: Long? = null) {
        val appCtx = context.applicationContext
        val looper = android.os.Looper.getMainLooper()
        val handler = android.os.Handler(looper)

        // Always post to main thread
        handler.post {
            // Use LONG so we have time to cancel early if needed
            val toast = android.widget.Toast.makeText(appCtx, msg, android.widget.Toast.LENGTH_LONG)
            toast.show()

            if (customDurationMs != null) {
                val cutoff = customDurationMs.coerceAtLeast(200L) // avoid "blink"
                handler.postDelayed({ toast.cancel() }, cutoff)
            }
        }
    }

    // Start warning Activity on main thread (and NEW_TASK if called from non-Activity)
    fun showWarning(context: Context, url: String, reason: String) {
        mainHandler.post {
            val i = Intent(context, LinkWarningActivity::class.java).apply {
                putExtra("url", url)
                putExtra("warningText", reason)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(i)
        }
    }
}
