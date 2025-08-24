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
import com.example.sneakylinky.service.report.LinkReport
import com.example.sneakylinky.service.report.ReportDispatcher
import com.example.sneakylinky.service.report.UserVerdict
import com.example.sneakylinky.service.report.Verdict
import kotlinx.coroutines.launch

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

    override fun onBindViewHolder(holder: VH, position: Int) {
        val url = items[position]
        holder.view.text = url

        holder.view.setOnClickListener {
            showReportDialog(holder.view, url)
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
