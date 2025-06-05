package com.example.sneakylinky.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.widget.Toast

fun saveSelectedBrowser(context: Context, packageName: String) {
    context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        .edit().putString("selected_browser", packageName).apply()
}

fun getSelectedBrowser(context: Context): String? {
    return context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        .getString("selected_browser", null)
}

/* open URL in chosen browser */
fun launchInSelectedBrowser(context: Context, url: String) {
    val selectedPackage = getSelectedBrowser(context)
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        selectedPackage?.let { setPackage(it) }
    }

    Toast.makeText(context, "Sneaky got link!", Toast.LENGTH_SHORT).show()

    try {
        context.startActivity(intent)
        if (context is Activity) {
            // remove enter animation ↔ prevents brief UI flash
            context.overridePendingTransition(0, 0)
        }
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "Cannot open link", Toast.LENGTH_SHORT).show()
    }
}



fun getInstalledBrowsers(context: Context): List<ResolveInfo> {
    val pm = context.packageManager

    // API 30+ : the official way
    val intent = Intent.makeMainSelectorActivity(
        Intent.ACTION_MAIN,
        Intent.CATEGORY_APP_BROWSER
    )

    // Query all activities that can handle the selector
    val result = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)

    // Fallback for API < 30 (rare on new Play images)
    val fallback = if (result.isEmpty()) {
        val httpIntent = Intent(Intent.ACTION_VIEW, Uri.parse("http://www.example.com"))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        pm.queryIntentActivities(httpIntent, PackageManager.MATCH_ALL)
    } else result

    // Remove Sneaky Linky itself
    return fallback.filter { it.activityInfo.packageName != context.packageName }
}



