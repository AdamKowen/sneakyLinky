package com.example.sneakylinky.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.setPadding
import androidx.recyclerview.widget.RecyclerView
import com.example.sneakylinky.R
import com.example.sneakylinky.SneakyLinkyApp
import com.example.sneakylinky.service.report.HistoryStore
import com.example.sneakylinky.service.report.ReportDispatcher
import com.example.sneakylinky.service.report.UserVerdict
import kotlinx.coroutines.launch
import androidx.core.graphics.toColorInt

class HistoryAdapter(private val items: List<String>) :
    RecyclerView.Adapter<HistoryAdapter.VH>() {

    inner class VH(val view: TextView) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val ctx = parent.context
        val density = ctx.resources.displayMetrics.density

        val textView = TextView(ctx).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                val m = (8 * density).toInt()
                setMargins(m, m / 2, m, m / 2)
            }

            setPadding(
                (16 * density).toInt(),
                (12 * density).toInt(),
                (16 * density).toInt(),
                (12 * density).toInt()
            )
            setLineSpacing(10f, 1.2f)
            textSize = 18f
            setTextColor(ContextCompat.getColor(ctx, R.color.near_white))

            background = GradientDrawable().apply {
                cornerRadius = 12 * density
                setColor(Color.parseColor("#20FFFFFF"))
            }

            isClickable = true
            isFocusable = true
        }

        return VH(textView)
    }

    // comments in English only
    override fun onBindViewHolder(holder: VH, position: Int) {
        val url = items[position]
        holder.view.text = url

        // default color until DB result arrives
        holder.view.setTextColor(ContextCompat.getColor(holder.view.context, R.color.near_white))

        // load latest history for this URL and colorize
        SneakyLinkyApp.appScope.launch {
            val ctx = holder.view.context
            val history = HistoryStore.latestForUrl(ctx, url) // suspend

            // Switch back to main thread to touch views
            (holder.view.context as? android.app.Activity)?.runOnUiThread {
                holder.view.setTextColor(colorFor(history, ctx))
            }
        }

        holder.view.setOnClickListener { showReportDialog(holder.view, url) }
    }


    // comments in English only
    private fun colorFor(history: com.example.sneakylinky.service.report.LinkHistory?, ctx: android.content.Context): Int {
        if (history == null) return ContextCompat.getColor(ctx, R.color.near_white)

        val red    = "#FF4D4D".toColorInt()
        val orange = "#FFA500".toColorInt()
        val yellow = "#FFD54F".toColorInt()
        val green  = "#4CAF50".toColorInt()

        return when {
            // Red: failed local check (suspicious or error)
            history.localCheck == com.example.sneakylinky.service.report.LocalCheck.SUSPICIOUS ||
                    history.localCheck == com.example.sneakylinky.service.report.LocalCheck.ERROR -> red

            // Orange: remote risk (combined indicates suspicion)  ← temporary stand-in for "message fail"
            history.remoteStatus == com.example.sneakylinky.service.report.RemoteStatus.RISK -> orange

            // Yellow: remote error (server side or analysis failed)
            history.remoteStatus == com.example.sneakylinky.service.report.RemoteStatus.ERROR -> yellow

            // Green: passed all (local SAFE and remote SAFE or not yet run)
            history.localCheck == com.example.sneakylinky.service.report.LocalCheck.SAFE &&
                    (history.remoteStatus == com.example.sneakylinky.service.report.RemoteStatus.SAFE) -> green

            else -> ContextCompat.getColor(ctx, R.color.near_white)
        }
    }



    override fun getItemCount() = items.size

    // --- private helpers ---


    private fun showReportDialog(anchorView: View, url: String) {
        val ctx = anchorView.context
        val density = ctx.resources.displayMetrics.density

        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * density).toInt())
        }

        val urlView = TextView(ctx).apply {
            text = url
            textSize = 16f
        }

        val radioGroup = RadioGroup(ctx).apply { orientation = RadioGroup.HORIZONTAL }
        val rbOk = RadioButton(ctx).apply { text = "OK" }
        val rbSuspicious = RadioButton(ctx).apply { text = "Suspicious" }
        radioGroup.addView(rbOk)
        radioGroup.addView(rbSuspicious)
        rbSuspicious.isChecked = true

        val reasonInput = EditText(ctx).apply {
            hint = "Reason (optional)"
            inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
            maxLines = 6
        }

        container.addView(urlView)
        container.addView(makeSpace(ctx, 8))
        container.addView(radioGroup)
        container.addView(makeSpace(ctx, 8))
        container.addView(reasonInput)

        AlertDialog.Builder(ctx)
            .setTitle("Report Link")
            .setView(container)
            .setPositiveButton("Report") { _, _ ->
                val verdict = if (rbOk.isChecked) UserVerdict.OK else UserVerdict.SUSPICIOUS
                val reason = reasonInput.text?.toString()?.trim().orEmpty()

                // Launch a coroutine to call suspend functions
                SneakyLinkyApp.appScope.launch {
                    // Try to find the latest history row for this URL; create one if missing
                    val latest = HistoryStore.latestForUrl(ctx, url)
                    val historyId = latest?.id ?: HistoryStore.createRun(ctx, url, null)

                    // Send report and update DB state before/after
                    ReportDispatcher.send(
                        context = ctx,
                        historyId = historyId,
                        url = url,
                        verdict = verdict,
                        reason = reason.ifBlank { null }
                    )
                }

                Toast.makeText(ctx, "Report sent", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun makeSpace(ctx: android.content.Context, heightDp: Int): View {
        return Space(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (heightDp * ctx.resources.displayMetrics.density).toInt()
            )
        }
    }
}
