package com.example.sneakylinky.ui

import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.sneakylinky.R

class HistoryAdapter(private val items: List<String>) : RecyclerView.Adapter<HistoryAdapter.VH>() {

    inner class VH(val view: TextView) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val textView = TextView(parent.context).apply {
            setPadding(32, 16, 32, 16)
            setLineSpacing(10f, 1.2f)
            textSize = 20f
            setTextColor(ContextCompat.getColor(parent.context, R.color.near_white))
        }
        return VH(textView)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.view.text = items[position]
    }

    override fun getItemCount() = items.size
}
