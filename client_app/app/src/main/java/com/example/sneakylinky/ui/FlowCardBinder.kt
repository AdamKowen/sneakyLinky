package com.example.sneakylinky.ui.flow

import android.app.Activity
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.core.app.ActivityCompat.startActivityForResult
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.content.ContextCompat.startActivity
import androidx.lifecycle.Observer
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.sneakylinky.R
import com.example.sneakylinky.service.hotsetdatabase.HotsetSyncScheduler
import com.example.sneakylinky.util.UiNotices
import com.example.sneakylinky.util.getSelectedBrowser


class FlowCardBinder(
    private val activity: Activity,
    private val root: View,
    private val accessibilityService: ComponentName,
    private val openBrowserPicker: () -> Unit
) {
    private val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Views (lazy lookups)
    private val btnUpdateDb by lazy { root.findViewById<View>(R.id.btnUpdateDbNow) }
    private val btnSetDefault by lazy { root.findViewById<View>(R.id.btnSetDefault) }
    // comments in English only
    private val switchCloudLinks by lazy { root.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchCloudLinks) }
    private val switchMsgCheck   by lazy { root.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchMsgCheck) }

    private val btnChosenBrowser by lazy { root.findViewById<ImageButton>(R.id.btnChosenBrowser) }

    // Reentrancy guard for listeners
    private var updatingUi = false

    private val wm by lazy { WorkManager.getInstance(activity) }
    private var hotsetListObserver: Observer<List<WorkInfo>>? = null
    private var hotsetLiveData: androidx.lifecycle.LiveData<List<androidx.work.WorkInfo>>? = null
    private var lastHandledId: java.util.UUID? = null
    private var lastHandledState: androidx.work.WorkInfo.State? = null


    init {
        // Update DB button (unchanged)
        btnUpdateDb?.setOnClickListener {
            setBtnUpdating(true)           // handles isEnabled/alpha; no need for extra isEnabled=false
            HotsetSyncScheduler.runNow(activity)

            // Detach old observer (from the exact LiveData instance we attached to)
            detachHotsetObserver()

            // Attach to the current LiveData once
            val live = androidx.work.WorkManager.getInstance(activity)
                .getWorkInfosForUniqueWorkLiveData("hotset-manual-sync")
            hotsetLiveData = live

            val obs = androidx.lifecycle.Observer<List<androidx.work.WorkInfo>> { infos ->
                if (infos.isNullOrEmpty()) return@Observer

                // Pick the most relevant WorkInfo (latest attempt, then UUID tie-break)
                val info = infos.maxWithOrNull(
                    compareBy<androidx.work.WorkInfo> { it.runAttemptCount }
                        .thenBy { it.id.mostSignificantBits }
                ) ?: return@Observer

                // Drop duplicate emissions of the same (id,state)
                if (lastHandledId == info.id && lastHandledState == info.state) return@Observer
                lastHandledId = info.id
                lastHandledState = info.state

                // Only act on terminal states
                if (!info.state.isFinished) return@Observer

                val msg = when (info.state) {
                    androidx.work.WorkInfo.State.SUCCEEDED -> {
                        val status = info.outputData.getString("status") ?: "updated"
                        when (status) {
                            "up_to_date" -> "Database is in the latest version"
                            "updated"    -> "Updated to the latest version successfully"
                            else         -> "Unknown status: $status"
                        }
                    }
                    androidx.work.WorkInfo.State.FAILED -> {
                        val err = info.outputData.getString("error") ?: "unknown error"
                        "failed: $err"
                    }
                    androidx.work.WorkInfo.State.CANCELLED -> "update terminated"
                    else -> return@Observer
                }

                UiNotices.safeToast(activity, msg, 2500)
                setBtnUpdating(false)
                detachHotsetObserver() // stop listening after terminal state
            }

            hotsetListObserver = obs
            // Prefer lifecycle-aware observe; falls back to observeForever if needed
            if (activity is androidx.lifecycle.LifecycleOwner) {
                live.observe(activity, obs)
            } else {
                live.observeForever(obs)
            }
        }



        // Open browser picker card
        btnChosenBrowser?.setOnClickListener { openBrowserPicker() }

        // Request default browser role / settings
        btnSetDefault?.setOnClickListener { requestSetDefaultBrowser() }

        // Toggles → persist state
        switchCloudLinks?.setOnCheckedChangeListener { _, isChecked ->
            if (updatingUi) return@setOnCheckedChangeListener
            prefs.edit().putBoolean(KEY_REMOTE_LINKS, isChecked).apply()
        }

        switchMsgCheck?.setOnCheckedChangeListener { _, wantEnable ->
            if (updatingUi) return@setOnCheckedChangeListener
            if (wantEnable) {
                // Require accessibility permission for this feature
                if (!isAccessibilityEnabled(activity, accessibilityService)) {
                    // Open settings and keep switch OFF until granted
                    openAccessibilitySettings()
                    forceSwitchMsg(false)
                    prefs.edit().putBoolean(KEY_REMOTE_MSGS, false).apply()
                    UiNotices.safeToast(activity, "Grant Accessibility permission to enable message checks", 2500)
                } else {
                    prefs.edit().putBoolean(KEY_REMOTE_MSGS, true).apply()
                }
            } else {
                prefs.edit().putBoolean(KEY_REMOTE_MSGS, false).apply()
            }
        }

        // Initial paint
        syncUiFromState()
        refreshBrowserIcon()
    }

    fun onResume() {
        // When returning from Settings, re-sync permission and switches
        val granted = isAccessibilityEnabled(activity, accessibilityService)
        if (!granted) {
            // If permission was revoked, keep switch and pref off
            prefs.edit().putBoolean(KEY_REMOTE_MSGS, false).apply()
        }
        syncUiFromState()
        refreshBrowserIcon()
    }

    private fun syncUiFromState() {
        updatingUi = true
        try {
            val linksOn = prefs.getBoolean(KEY_REMOTE_LINKS, true) // default ON
            val msgsOnPref = prefs.getBoolean(KEY_REMOTE_MSGS, false) // default OFF
            val hasAcc = isAccessibilityEnabled(activity, accessibilityService)

            switchCloudLinks?.isChecked = linksOn
            switchMsgCheck?.isChecked = msgsOnPref && hasAcc
        } finally {
            updatingUi = false
        }
    }

    private fun refreshBrowserIcon() {
        val pkg = getSelectedBrowser(activity) ?: return // fallback to default vector if null
        try {
            val icon = activity.packageManager.getApplicationIcon(pkg)
            btnChosenBrowser?.setImageDrawable(icon)
        } catch (_: PackageManager.NameNotFoundException) {
            // keep default vector (R.drawable.browser)
        }
    }

    private fun forceSwitchMsg(checked: Boolean) {
        updatingUi = true
        try {
            switchMsgCheck?.isChecked = checked
        } finally {
            updatingUi = false
        }
    }


    private fun requestSetDefaultBrowser() {
        val isDefault = isDefaultBrowser(activity)
        val message = if (isDefault) {
            "SneakyLinky is the default browser"
        } else {
            "Go to phone settings to make SneakyLinky your default browser"
        }
        UiNotices.safeToast(activity, message)
    }

    private fun isDefaultBrowser(context: Context): Boolean {
        // Use RoleManager for a reliable check on modern Android
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            return roleManager?.isRoleHeld(RoleManager.ROLE_BROWSER) ?: false
        }
        // Fallback for older versions using a less reliable method
        val ourPackageName = context.packageName
        val defaultBrowser = getSelectedBrowser(context)
        return ourPackageName == defaultBrowser
    }



    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        activity.startActivity(intent)
    }


    private fun cleanupHotsetObserver() {
        val live = WorkManager.getInstance(activity)
            .getWorkInfosForUniqueWorkLiveData("hotset-manual-sync")
        hotsetListObserver?.let { live.removeObserver(it) }
        hotsetListObserver = null
        btnUpdateDb?.isEnabled = true
    }

    private fun setBtnUpdating(isUpdating: Boolean) {
        val btn = btnUpdateDb ?: return
        if (isUpdating) {
            btn.isEnabled = false
            btn.isClickable = false
            btn.alpha = 0.5f    // visually dim when disabled
        } else {
            btn.isEnabled = true
            btn.isClickable = true
            btn.alpha = 1.0f    // restore
        }
    }


    // comments in English only
    private fun detachHotsetObserver() {
        hotsetListObserver?.let { obs ->
            hotsetLiveData?.removeObserver(obs)
        }
        hotsetLiveData = null
        hotsetListObserver = null
        lastHandledId = null
        lastHandledState = null
    }



    companion object {
        private const val PREFS_NAME = "sneaky_linky_prefs"
        private const val KEY_REMOTE_LINKS = "remote_links_enabled"
        private const val KEY_REMOTE_MSGS = "remote_msgs_enabled"

        // Utility: check if our AccessibilityService is enabled
        fun isAccessibilityEnabled(context: Context, service: ComponentName): Boolean {
            val enabled = Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED, 0
            ) == 1
            if (!enabled) return false

            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServices)
            while (colonSplitter.hasNext()) {
                val comp = colonSplitter.next()
                if (comp.equals(service.flattenToString(), ignoreCase = true)) {
                    return true
                }
            }
            return false
        }

    }
}



/**
 * Global feature flags (simple getters for "if" checks).
 */
object FeatureFlags {
    private const val PREFS_NAME = "sneaky_linky_prefs"
    private const val KEY_REMOTE_LINKS = "remote_links_enabled"
    private const val KEY_REMOTE_MSGS = "remote_msgs_enabled"

    fun remoteLinkChecks(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_REMOTE_LINKS, true)

    fun remoteMessageChecks(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_REMOTE_MSGS, false)
}
