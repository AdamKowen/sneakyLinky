package com.example.sneakylinky.ui

import android.content.Context
import android.content.pm.ResolveInfo
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.sneakylinky.R
import com.example.sneakylinky.util.getSelectedBrowser
import com.example.sneakylinky.util.saveSelectedBrowser

class BrowserCarouselAdapter(
    private val browsers: List<ResolveInfo>,
    private val context: Context,
    private val onBrowserPicked: (ResolveInfo) -> Unit
) : RecyclerView.Adapter<BrowserCarouselAdapter.VH>() {

    private var selectedPackageName: String? = getSelectedBrowser(context)

    inner class VH(item: View) : RecyclerView.ViewHolder(item) {
        val icon: ImageView = item.findViewById(R.id.browserIcon)
        val label: TextView = item.findViewById(R.id.browserLabel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_browser_card, parent, false)
        return VH(view)


    }

    override fun getItemCount() = browsers.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val browser = browsers[position]
        val packageName = browser.activityInfo.packageName

        holder.label.text = browser.loadLabel(context.packageManager)
        holder.icon.setImageDrawable(browser.loadIcon(context.packageManager))

        val isSelected = packageName == selectedPackageName
        holder.itemView.setBackgroundColor(
            if (isSelected)
                ContextCompat.getColor(context, R.color.card_selected_background)
            else
                ContextCompat.getColor(context, R.color.card_default_background)
        )

        holder.itemView.setOnClickListener {
            saveSelectedBrowser(context, packageName)
            selectedPackageName = packageName
            notifyDataSetChanged()
            onBrowserPicked(browser)
        }
    }
}
