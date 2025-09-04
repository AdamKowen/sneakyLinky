// comments in English only
package com.example.sneakylinky.ui

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Space
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.setPadding
import androidx.recyclerview.widget.RecyclerView
import com.example.sneakylinky.R
import com.example.sneakylinky.SneakyLinkyApp
import com.example.sneakylinky.service.report.HistoryStore
import com.example.sneakylinky.service.report.LinkHistory
import com.example.sneakylinky.service.report.LocalCheck
import com.example.sneakylinky.service.report.RemoteStatus
import com.example.sneakylinky.service.report.ReportDispatcher
import com.example.sneakylinky.service.report.UserVerdict
import kotlinx.coroutines.launch

class HistoryAdapter(
    private val context: Context,
    private val items: List<LinkHistory>
) : RecyclerView.Adapter<HistoryAdapter.VH>() {

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val urlView: TextView = itemView.findViewById(R.id.tvUrl)
        val statusIcon: ImageView = itemView.findViewById(R.id.ivStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val history = items[position]
        holder.urlView.text = history.url

        val (iconRes, tintRes, desc) = iconFor(history)

        holder.statusIcon.setImageResource(iconRes)
        holder.statusIcon.imageTintList =
            ContextCompat.getColorStateList(holder.itemView.context, tintRes)

        holder.statusIcon.setOnClickListener {
            AlertDialog.Builder(context)
                .setTitle("Status info")
                .setMessage(desc)
                .setPositiveButton("Close") { d, _ -> d.dismiss() }
                .show()
        }

        // Optional: clicking the URL itself for report dialog
        holder.urlView.setOnClickListener {
            showReportDialog(holder.itemView, history.url)
        }
    }

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

                com.example.sneakylinky.SneakyLinkyApp.appScope.launch {
                    // 1) toast "sending..." on Main
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        Toast.makeText(ctx.applicationContext, "Sending...", Toast.LENGTH_SHORT).show()
                    }

                    // 2) ensure row exists
                    val historyId = run {
                        val latest = com.example.sneakylinky.service.report.HistoryStore.latestForUrl(ctx, url)
                        latest?.id ?: com.example.sneakylinky.service.report.HistoryStore.createRun(ctx, url, null)
                    }

                    // 3) send (runs on IO inside ReportDispatcher)
                    com.example.sneakylinky.service.report.ReportDispatcher.send(
                        context = ctx,
                        historyId = historyId,
                        url = url,
                        verdict = verdict,
                        reason = reason.ifBlank { null }
                    )

                    // 4) read final state from DB and toast on Main
                    val finalState = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        com.example.sneakylinky.service.report.HistoryStore.latestForUrl(ctx, url)?.reportSendState
                    }

                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        val msg = when (finalState) {
                            com.example.sneakylinky.service.report.ReportSendState.SENT_OK    -> "Sent Successfully"
                            com.example.sneakylinky.service.report.ReportSendState.SENT_ERROR -> "Error - could not send"
                            com.example.sneakylinky.service.report.ReportSendState.SENDING    -> "sending..."
                            else -> "Unknown status"
                        }
                        Toast.makeText(ctx.applicationContext, msg, Toast.LENGTH_SHORT).show()
                    }
                }
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

    override fun getItemCount() = items.size

    // Map DB state to icon + description + tint
    private fun iconFor(history: LinkHistory): Triple<Int, Int, String> {
        // NOTE: using R.drawable.link as placeholder for all; swap to your icons later
        return when {
            history.localCheck == LocalCheck.SUSPICIOUS || history.localCheck == LocalCheck.ERROR ->
                Triple(R.drawable.x, android.R.color.holo_red_light, "Failed local check")

            history.remoteStatus == RemoteStatus.RISK ->
                Triple(R.drawable.warning, android.R.color.holo_orange_light, "Remote risk detected")

            history.remoteStatus == RemoteStatus.ERROR ->
                Triple(R.drawable.link, android.R.color.holo_orange_light, "Remote check failed")

            history.localCheck == LocalCheck.SAFE && history.remoteStatus == RemoteStatus.SAFE ->
                Triple(R.drawable.check, android.R.color.holo_green_light, "All checks passed")

            else ->
                Triple(R.drawable.link, android.R.color.darker_gray, "No data")
        }
    }
}
