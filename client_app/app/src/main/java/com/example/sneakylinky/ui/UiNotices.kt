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

    // Toast throttle/replace state (avoid OEM toast quota)
    @Volatile private var lastToast: Toast? = null
    @Volatile private var lastShownAt: Long = 0L
    @Volatile private var seq: Long = 0L
    private const val MIN_GAP_MS = 1600L // conservative, esp. on Samsung

    // Always post Toast to the main thread
    @JvmStatic
    fun safeToast(context: Context, msg: CharSequence, customDurationMs: Long? = null) {
        val appCtx = context.applicationContext
        val handler = mainHandler
        val now = android.os.SystemClock.uptimeMillis()
        val delay = (lastShownAt + MIN_GAP_MS - now).coerceAtLeast(0L)
        val cutoff = customDurationMs?.coerceAtLeast(200L) // avoid blinking

        val mySeq: Long = synchronized(UiNotices::class.java) {
            seq += 1
            // kill previous toast if any
            lastToast?.cancel()
            seq
        }

        val showRunnable = Runnable {
            if (mySeq != seq) return@Runnable // canceled in the meantime
            val t = Toast.makeText(appCtx, msg, Toast.LENGTH_LONG)
            lastToast = t
            lastShownAt = android.os.SystemClock.uptimeMillis()
            t.show()
            if (cutoff != null) {
                handler.postDelayed({ if (mySeq == seq) t.cancel() }, cutoff)
            }
        }

        if (delay == 0L) handler.post(showRunnable) else handler.postDelayed(showRunnable, delay)
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
