// comments in English only
package com.example.sneakylinky.ui

import android.annotation.SuppressLint
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
import com.example.sneakylinky.service.LinkFlow
import com.example.sneakylinky.service.report.HistoryStore
import com.example.sneakylinky.service.report.LinkHistory
import com.example.sneakylinky.service.report.LocalCheck
import com.example.sneakylinky.service.report.RemoteStatus
import com.example.sneakylinky.service.report.ReportDispatcher
import com.example.sneakylinky.service.report.ReportSendState
import com.example.sneakylinky.service.report.UserVerdict
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryAdapter(
    private val context: Context,
    private val initialItems: List<LinkHistory>
) : RecyclerView.Adapter<HistoryAdapter.VH>() {

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val urlView: TextView = itemView.findViewById(R.id.tvUrl)
        val statusIcon: ImageView = itemView.findViewById(R.id.ivStatus)
    }

    private val items: MutableList<LinkHistory> = initialItems.toMutableList()


    // Public setter to refresh the list from DB/Flow
    @SuppressLint("NotifyDataSetChanged")
    fun setData(newItems: List<LinkHistory>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
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


                    val fresh = withContext(kotlinx.coroutines.Dispatchers.IO) {
                        com.example.sneakylinky.service.report.HistoryStore.recent(ctx, 200)
                    }
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        this@HistoryAdapter.setData(fresh)
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
    // comments in English only
    private fun iconFor(history: com.example.sneakylinky.service.report.LinkHistory): Triple<Int, Int, String> {
        // if you use the breakdown map:
        val breakdown = com.example.sneakylinky.service.LinkFlow.remoteBreakdownFor(history.id)

        // 1) compute base first (icon/tint/label by status)
        val base: Triple<Int, Int, String> = when {
            history.localCheck == com.example.sneakylinky.service.report.LocalCheck.SUSPICIOUS ||
                    history.localCheck == com.example.sneakylinky.service.report.LocalCheck.ERROR ->
                Triple(R.drawable.x, android.R.color.holo_red_light, "Failed local check")

            breakdown == com.example.sneakylinky.service.LinkFlow.RemoteBreakdown.BOTH_FAIL ->
                Triple(R.drawable.warning, android.R.color.holo_orange_light, "Both remote checks failed")

            breakdown == com.example.sneakylinky.service.LinkFlow.RemoteBreakdown.URL_FAIL ->
                Triple(R.drawable.link, android.R.color.holo_orange_light, "URL check failed")

            breakdown == com.example.sneakylinky.service.LinkFlow.RemoteBreakdown.MESSAGE_FAIL ->
                Triple(R.drawable.message, android.R.color.holo_orange_light, "Message check failed")

            history.localCheck == com.example.sneakylinky.service.report.LocalCheck.SAFE &&
                    history.remoteStatus == com.example.sneakylinky.service.report.RemoteStatus.SAFE ->
                Triple(R.drawable.check, android.R.color.holo_green_light, "All checks passed")

            history.remoteStatus == com.example.sneakylinky.service.report.RemoteStatus.RISK ->
                Triple(R.drawable.warning, android.R.color.holo_orange_light, "Remote risk detected")

            history.remoteStatus == com.example.sneakylinky.service.report.RemoteStatus.ERROR ->
                Triple(R.drawable.link, android.R.color.holo_orange_light, "Remote check failed")

            else ->
                Triple(R.drawable.link, android.R.color.darker_gray, "No data")
        }

        // 2) if reported successfully -> keep the same ICON, only tint blue and change label
        val isReported = history.reportSendState ==
                com.example.sneakylinky.service.report.ReportSendState.SENT_OK

        // Option A: using copy (Triple is a data class)
        return if (isReported) {
            base.copy(
                second = android.R.color.holo_blue_light,
                third = "User reported"
            )
        } else {
            base
        }

        // Option B (equivalent, if you prefer without copy):
        // return if (isReported) Triple(base.first, android.R.color.holo_blue_light, "User reported") else base
    }


    // comments in English only
    private fun refreshHistoryInto(holder: CardAdapter.Card3ViewHolder) {
        val ctx = holder.itemView.context
        SneakyLinkyApp.appScope.launch {
            val items = withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.example.sneakylinky.service.report.HistoryStore.recent(ctx, 200)
            }
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                val existing = holder.recyclerView.adapter as? HistoryAdapter
                if (existing == null) {
                    holder.recyclerView.adapter = HistoryAdapter(ctx, items)
                } else {
                    existing.setData(items)     // <-- update instead of replacing adapter
                }
            }
        }
    }

}