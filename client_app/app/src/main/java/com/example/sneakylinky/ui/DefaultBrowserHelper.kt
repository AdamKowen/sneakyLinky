package com.example.sneakylinky.ui
import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * A utility class to request the user to set the app as the default browser.
 * This class handles different Android versions to provide the best user experience.
 */
object DefaultBrowserHelper {

    private const val TAG = "DefaultBrowserHelper"
    private const val REQUEST_CODE_ROLE_MANAGER = 123
    private const val REQUEST_CODE_SETTINGS = 124

    /**
     * Requests the user to set the current app as the default browser.
     * The behavior changes based on the Android version.
     *
     * @param activity The activity context.
     */
    fun requestSetDefaultBrowser(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10 (API 29) and above: Use RoleManager for a direct system pop-up.
            val roleManager = activity.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_BROWSER)) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER)
                activity.startActivityForResult(intent, REQUEST_CODE_ROLE_MANAGER)
                Log.d(TAG, "Starting role manager intent for Android 10+")
                return
            } else {
                Log.w(TAG, "RoleManager is not available. Falling back to settings.")
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // Android 7-9 (API 24-28): Open the "Default Apps" settings screen.
            val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            activity.startActivityForResult(intent, REQUEST_CODE_SETTINGS)
            Log.d(TAG, "Starting default apps settings intent for Android 7-9")
        } else {
            // Fallback for Android versions below 7: Send user to generic Settings.
            // There is no specific API for default apps on these versions.
            val intent = Intent(Settings.ACTION_SETTINGS)
            activity.startActivityForResult(intent, REQUEST_CODE_SETTINGS)
            Log.d(TAG, "Starting generic settings intent for Android < 7")
        }
    }
}

