// comments in English only
package com.example.sneakylinky.util

import android.Manifest
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.sneakylinky.ui.LinkWarningActivity
import java.util.ArrayDeque
import java.util.LinkedList
import java.util.UUID
import kotlin.math.max

object UiNotices {

    private const val TAG = "UiNotices"

    // ---- Tuning knobs ----
    private const val MIN_GAP_MS_FOREGROUND = 3600L      // base gap when foreground
    private const val MIN_GAP_MS_BACKGROUND = 5200L      // safer gap when background (Samsung can be stricter)
    private const val DEFAULT_LONG_MS       = 3600L
    private const val DEDUP_WINDOW_MS       = 1500L
    private const val QUOTA_WINDOW_MS       = 10_000L
    private const val QUOTA_FG              = 2          // max shows / 10s when foreground
    private const val QUOTA_BG              = 1          // max shows / 10s when background
    private const val WATCHDOG_MS           = 300L       // detect "blocked by OS" quickly
    private const val RETRY_BACKOFF_MS      = 4000L      // if blocked, retry after this
    private const val CHANNEL_ID            = "sneaky_linky_checks"
    private const val CHANNEL_NAME          = "Link checks"
    // -----------------------

    private val mainHandler = Handler(Looper.getMainLooper())

    private data class ToastReq(
        val id: String,
        val text: CharSequence,
        val durationMs: Long,
        val createdAt: Long,
        val isChecking: Boolean,
        val isResult: Boolean,
        var retries: Int = 0
    )

    private val queue = LinkedList<ToastReq>()
    private var isDrainScheduled = false
    private var nextAvailableAt = 0L

    // Sliding-window accounting of actual show() attempts
    private val postedTimes = ArrayDeque<Long>() // when we attempted to show
    private val shownTimes  = ArrayDeque<Long>() // when onToastShown fired

    private fun now() = SystemClock.elapsedRealtime()
    private fun short(s: CharSequence, n: Int = 90) = if (s.length <= n) s else (s.substring(0, n) + "…")

    private fun procImportance(): Int {
        return try {
            val info = ActivityManager.RunningAppProcessInfo()
            ActivityManager.getMyMemoryState(info)
            info.importance
        } catch (_: Throwable) { ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE }
    }

    private fun importanceLabel(imp: Int): String = when (imp) {
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "FOREGROUND"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE    -> "VISIBLE"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE-> "PERCEPTIBLE"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE    -> "SERVICE"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED     -> "CACHED"
        else -> "OTHER($imp)"
    }

    private fun classify(msg: CharSequence): Pair<Boolean, Boolean> {
        val m = msg.toString()
        val isChecking = m.startsWith("Checking", true) || m.startsWith("בודקים") || m.startsWith("בדיקה")
        val isResult = m.startsWith("URL: ") || m.contains(" • Message: ") || m.startsWith("תוצאה")
        return isChecking to isResult
    }

    private fun minGapByState(imp: Int): Long =
        if (imp <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE) MIN_GAP_MS_FOREGROUND
        else MIN_GAP_MS_BACKGROUND

    private fun quotaByState(imp: Int): Int =
        if (imp <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE) QUOTA_FG else QUOTA_BG

    private fun prune(times: ArrayDeque<Long>, windowMs: Long) {
        val cutoff = now() - windowMs
        while (times.isNotEmpty() && times.first() < cutoff) times.removeFirst()
    }

    private fun canUseQuota(imp: Int): Boolean {
        prune(postedTimes, QUOTA_WINDOW_MS)
        val used = postedTimes.size
        val limit = quotaByState(imp)
        return used < limit
    }

    private fun recordPosted() {
        postedTimes.addLast(now())
        prune(postedTimes, QUOTA_WINDOW_MS)
    }

    private fun recordShown() {
        shownTimes.addLast(now())
        prune(shownTimes, QUOTA_WINDOW_MS)
    }

    // --- Public API (unchanged signature) ---
    @JvmStatic
    fun safeToast(context: Context, msg: CharSequence, customDurationMs: Long? = null) {
        val (isChecking, isResult) = classify(msg)
        val id = UUID.randomUUID().toString().takeLast(6)
        val created = now()
        val duration = (customDurationMs ?: DEFAULT_LONG_MS).coerceAtLeast(200L)
        val imp = procImportance()
        Log.v(TAG, "enqueue#$id text='${short(msg)}' len=${msg.length} checking=$isChecking result=$isResult dur=${duration}ms importance=${importanceLabel(imp)}")

        mainHandler.post {
            // Prefer results: drop any queued "Checking…" that haven't shown yet
            if (isResult) {
                val removed = queue.removeAll { it.isChecking }
                if (removed) Log.v(TAG, "enqueue#$id removed pending Checking… items to prefer result")
            }

            // De-dup identical "Checking…" spam arriving very close
            if (isChecking && queue.isNotEmpty()) {
                val last = queue.last
                if (last.isChecking && last.text == msg && created - last.createdAt <= DEDUP_WINDOW_MS) {
                    Log.v(TAG, "enqueue#$id drop duplicate Checking… within ${DEDUP_WINDOW_MS}ms")
                    return@post
                }
            }

            queue.add(ToastReq(id, msg, duration, created, isChecking, isResult))
            scheduleDrain(context.applicationContext)
        }
    }

    fun showWarning(context: Context, url: String, reason: String) {
        val callId = UUID.randomUUID().toString().takeLast(6)
        Log.d(TAG, "showWarning#$callId post start url='${short(url)}' reasonLen=${reason.length}")
        mainHandler.post {
            val i = Intent(context, LinkWarningActivity::class.java).apply {
                putExtra("url", url)
                putExtra("warningText", reason)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(i)
                Log.d(TAG, "showWarning#$callId startActivity OK")
            } catch (t: Throwable) {
                Log.w(TAG, "showWarning#$callId startActivity failed: ${t.message}", t)
            }
        }
    }

    // --- Internal ---

    private fun scheduleDrain(appCtx: Context) {
        if (isDrainScheduled) return
        isDrainScheduled = true

        mainHandler.post(object : Runnable {
            override fun run() {
                isDrainScheduled = false
                if (queue.isEmpty()) {
                    Log.v(TAG, "drain: queue empty")
                    return
                }

                val now = now()
                val imp = procImportance()
                val gap = minGapByState(imp)
                val waitGap = (nextAvailableAt - now).coerceAtLeast(0L)

                // Enforce sliding-window quota before pulling from queue
                if (!canUseQuota(imp)) {
                    prune(postedTimes, QUOTA_WINDOW_MS)
                    val oldest = postedTimes.firstOrNull() ?: now
                    val nextQuotaAt = oldest + QUOTA_WINDOW_MS
                    val waitQuota = (nextQuotaAt - now).coerceAtLeast(QUOTA_WINDOW_MS / 2)
                    Log.v(TAG, "drain: quota-blocked (used=${postedTimes.size}/limit=${quotaByState(imp)}) → delay ${waitQuota}ms")
                    isDrainScheduled = true
                    mainHandler.postDelayed(this, waitQuota)
                    return
                }

                if (waitGap > 0L) {
                    Log.v(TAG, "drain: delaying by gap ${waitGap}ms (gap=$gap) nextAt=$nextAvailableAt now=$now")
                    isDrainScheduled = true
                    mainHandler.postDelayed(this, waitGap)
                    return
                }

                val req = queue.pollFirst()!!
                Log.v(TAG, "drain: showing#${req.id} text='${short(req.text)}' checking=${req.isChecking} result=${req.isResult} retries=${req.retries}")

                // Build + show Toast
                val toast = Toast.makeText(appCtx, req.text, Toast.LENGTH_LONG)
                var shown = false

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    toast.addCallback(object : Toast.Callback() {
                        override fun onToastShown() {
                            shown = true
                            recordShown()
                            Log.v(TAG, "toast#${req.id} onToastShown()")
                        }
                        override fun onToastHidden() {
                            Log.v(TAG, "toast#${req.id} onToastHidden()")
                        }
                    })
                }

                try {
                    toast.show()
                    recordPosted()
                    Log.v(TAG, "toast#${req.id} show() called (dur=${req.durationMs}ms) imp=${importanceLabel(imp)}")
                } catch (t: Throwable) {
                    Log.w(TAG, "toast#${req.id} show() threw ${t.javaClass.simpleName}: ${t.message}", t)
                }

                // Compute next allowed time by gap
                val extra = 200L
                nextAvailableAt = SystemClock.elapsedRealtime() + max(req.durationMs, gap) + extra

                // Watchdog: detect "blocked by OS quota" (no onToastShown shortly after show())
                mainHandler.postDelayed({
                    if (!shown) {
                        Log.w(TAG, "toast#${req.id} likely BLOCKED by OS quota; handling fallback (checking=${req.isChecking} result=${req.isResult})")
                        handleBlocked(appCtx, req, imp)
                    }
                }, WATCHDOG_MS)

                // Schedule next drain (if items remain)
                if (queue.isNotEmpty()) {
                    val delay = (nextAvailableAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
                    Log.v(TAG, "drain: reschedule next in ${delay}ms (queue=${queue.size})")
                    isDrainScheduled = true
                    mainHandler.postDelayed(this, delay)
                } else {
                    Log.v(TAG, "drain: done (queue empty)")
                }
            }
        })
    }

    private fun handleBlocked(appCtx: Context, req: ToastReq, imp: Int) {
        // For "checking…" we simply backoff or drop if a result will follow.
        if (req.isChecking) {
            Log.v(TAG, "blocked: drop or backoff 'Checking…' (no user harm)")
            return
        }

        // For results → try to show as notification if permission exists; else retry later.
        if (canNotify(appCtx)) {
            ensureChannel(appCtx)
            val nm = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notifId = 10_001 + (req.text.hashCode() and 0x7FFF)

            val n: Notification = NotificationCompat.Builder(appCtx, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle("Link scan result")
                .setContentText(req.text.toString())
                .setStyle(NotificationCompat.BigTextStyle().bigText(req.text.toString()))
                .setAutoCancel(true)
                .build()

            nm.notify(notifId, n)
            Log.w(TAG, "blocked: escalated to Notification id=$notifId text='${short(req.text)}'")
        } else {
            // No POST_NOTIFICATIONS permission → retry later with backoff (bounded)
            val retryDelay = RETRY_BACKOFF_MS + (req.retries * 1500L)
            if (req.retries <= 2) {
                val re = req.copy(retries = req.retries + 1)
                Log.w(TAG, "blocked: cannot notify (no permission). requeue result with backoff ${retryDelay}ms (retries=${re.retries})")
                queue.addFirst(re)
                isDrainScheduled = true
                mainHandler.postDelayed({ scheduleDrain(appCtx) }, retryDelay)
            } else {
                Log.w(TAG, "blocked: drop after retries=${req.retries} text='${short(req.text)}'")
            }
        }
    }

    private fun canNotify(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            Log.v(TAG, "canNotify: T+ permission=${granted}")
            if (!granted) return false
        }
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val enabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            nm.areNotificationsEnabled()
        } else {
            true // For API < 24, notifications were always enabled.
        }
        Log.v(TAG, "canNotify: enabled=$enabled")
        return enabled
    }

    private fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val existing = nm.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    // Set the importance to HIGH to enable heads-up notifications
                    NotificationManager.IMPORTANCE_HIGH
                )
                ch.description = "Results of link/message scans"
                nm.createNotificationChannel(ch)
                Log.v(TAG, "created NotificationChannel '$CHANNEL_ID'")
            }
        }
    }
}