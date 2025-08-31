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
    fun safeToast(context: Context, msg: String) {
        val ok = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true

        if (!ok) return

        mainHandler.post {
            Toast.makeText(context.applicationContext, msg, Toast.LENGTH_LONG).show()
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
