package com.example.sneakylinky.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.sneakylinky.R

class HistoryAdapter(private val items: List<String>) :
    RecyclerView.Adapter<HistoryAdapter.VH>() {

    inner class VH(val view: TextView) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val ctx = parent.context
        val density = ctx.resources.displayMetrics.density

        // create TextView with margins & padding
        val textView = TextView(ctx).apply {
            // set item layout params with margin
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                val m = (8 * density).toInt()
                setMargins(m, m/2, m, m/2)
            }

            // padding for text
            setPadding(
                (16 * density).toInt(),
                (12 * density).toInt(),
                (16 * density).toInt(),
                (12 * density).toInt()
            )
            setLineSpacing(10f, 1.2f)
            textSize = 18f
            setTextColor(ContextCompat.getColor(ctx, R.color.near_white))

            // create semi-transparent white rounded background
            background = GradientDrawable().apply {
                cornerRadius = 12 * density        // 12dp corner radius
                setColor(Color.parseColor("#20FFFFFF"))  // 12% white
            }

            // make it clickable (for ripple in some themes)
            isClickable = true
            isFocusable = true
        }

        return VH(textView)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val url = items[position]
        holder.view.text = url

        holder.view.setOnClickListener {
            AlertDialog.Builder(holder.view.context)
                .setTitle("Report Link")
                .setMessage(url)
                .setPositiveButton("Link OK") { dialog, _ ->
                    // user marked the link as safe
                    Toast.makeText(
                        holder.view.context,
                        "Marked OK",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                .setNegativeButton("Link Suspicious") { dialog, _ ->
                    // user marked the link as suspicious
                    Toast.makeText(
                        holder.view.context,
                        "Marked Suspicious",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                .setNeutralButton("Cancel") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }
    }

    override fun getItemCount() = items.size
}
