package com.example.sneakylinky.service.report

import android.app.Dialog
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatRadioButton
import androidx.core.view.setPadding
import com.example.sneakylinky.R
import android.content.res.ColorStateList
import android.graphics.Color
import androidx.core.graphics.toColorInt


/**
 * A reusable dialog that lets the user mark a link as OK or Suspicious and add an optional reason.
 * Uses Fragment Result API to return the result.
 */
class ReportDialogFragment : DialogFragment() {

    companion object {
        const val ARG_URL = "arg_url"
        const val RESULT_KEY = "link_report_result"
        const val RESULT_URL = "result_url"
        const val RESULT_VERDICT = "result_verdict" // "OK" / "SUSPICIOUS"
        const val RESULT_REASON = "result_reason"

        fun newInstance(url: String): ReportDialogFragment {
            return ReportDialogFragment().apply {
                arguments = bundleOf(ARG_URL to url)
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val ctx = requireContext()
        val url = requireArguments().getString(ARG_URL) ?: ""

        // ---- Build content view programmatically (no XML needed) ----
        val density = resources.displayMetrics.density
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * density).toInt())
        }

        // URL preview (read-only)
        val urlView = TextView(ctx).apply {
            text = url
            textSize = 16f
            // Optional style
        }

        // RadioGroup for verdict selection
        val radioGroup = RadioGroup(ctx).apply {
            orientation = RadioGroup.HORIZONTAL
        }

        val rbOk = AppCompatRadioButton(ctx).apply {
            text = "OK"
            buttonTintList = ColorStateList.valueOf("#9C27B0".toColorInt()) // purple
        }

        val rbSuspicious = AppCompatRadioButton(ctx).apply {
            text = "Suspicious"
            buttonTintList = ColorStateList.valueOf("#9C27B0".toColorInt()) // purple
        }
        radioGroup.addView(rbOk)
        radioGroup.addView(rbSuspicious)
        rbSuspicious.isChecked = true // default to Suspicious

        // Reason EditText
        val reasonInput = EditText(ctx).apply {
            hint = ctx.getString(R.string.report_reason_hint) // e.g., "Why do you suspect this link?"
            inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
            maxLines = 6
        }

        // Add views to container
        container.addView(urlView)
        container.addView(spaceView())
        container.addView(radioGroup)
        container.addView(spaceView(heightDp = 8))
        container.addView(reasonInput)

        // ---- Build dialog ----
        return AlertDialog.Builder(ctx)
            .setTitle(ctx.getString(R.string.report_link_title)) // e.g., "Report Link"
            .setView(container)
            .setPositiveButton(ctx.getString(R.string.report_action)) { _, _ ->
                // Collect values
                val verdict = if (rbOk.isChecked) "OK" else "SUSPICIOUS"
                val reason = reasonInput.text?.toString()?.trim().orEmpty()

                // Return result via Fragment Result API
                parentFragmentManager.setFragmentResult(
                    RESULT_KEY,
                    bundleOf(
                        RESULT_URL to url,
                        RESULT_VERDICT to verdict,
                        RESULT_REASON to reason
                    )
                )
            }
            .setNegativeButton(ctx.getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .create()
    }

    private fun spaceView(heightDp: Int = 4): View {
        val v = Space(requireContext())
        val h = (heightDp * resources.displayMetrics.density).toInt()
        v.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            h
        )
        return v
    }
}