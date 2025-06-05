package com.example.sneakylinky.ui

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.sneakylinky.R
import com.example.sneakylinky.util.*

//  constructor accept a callback function for URL checking
class CardAdapter(private val onCheckUrl: (String) -> Unit) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    // This variable will hold the URL that needs to be displayed in the first card.
    private var pendingUrlForCard1: String? = null


    companion object {
        private const val TYPE_CARD_1 = 0
        private const val TYPE_CARD_2 = 1
        private const val TYPE_CARD_3 = 2
    }

    override fun getItemCount(): Int = 3

    override fun getItemViewType(position: Int): Int = when (position) {
        0 -> TYPE_CARD_1
        1 -> TYPE_CARD_2
        else -> TYPE_CARD_3
    }



    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_CARD_1 -> {
                val view = inflater.inflate(R.layout.link_check_card, parent, false)
                val holder = Card1ViewHolder(view)
                // Store the reference to the EditText when the ViewHolder for the first card is created
                holder
            }
            TYPE_CARD_2 -> {
                val view = inflater.inflate(R.layout.browser_pick_card, parent, false)
                Card2ViewHolder(view)
            }
            else -> {
                val view = inflater.inflate(R.layout.history_card, parent, false)
                Card3ViewHolder(view)
            }
        }
    }






    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is Card1ViewHolder -> {

                pendingUrlForCard1?.let { url ->
                    holder.editText.setText(url)
                    // Clear the pending URL after setting it to avoid re-applying it
                    // unless you specifically want it to be persistent across re-binds.
                    pendingUrlForCard1 = null
                }



                holder.checkButton.setOnClickListener {
                    val raw = holder.editText.text.toString()
                    onCheckUrl(raw)          // delegate to activity – no coroutines here
                }


            }
//
            is Card2ViewHolder -> {
                val context = holder.itemView.context
                val browsers = getInstalledBrowsers(context)
                val savedPkg = getSelectedBrowser(context)

                // Layout Manager in vertical layout
                holder.recyclerBrowser.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)

                // Snap for the selected item
                val snapHelper = PagerSnapHelper()
                snapHelper.attachToRecyclerView(holder.recyclerBrowser)

                val adapter = BrowserCarouselAdapter(browsers, context) { browser ->
                    val pkgName = browser.activityInfo.packageName
                    saveSelectedBrowser(context, pkgName)
                    Toast.makeText(context, "Selected Browser: ${browser.loadLabel(context.packageManager)}", Toast.LENGTH_SHORT).show()
                }

                holder.recyclerBrowser.adapter = adapter

                // scrollinhg to the saved/default browser
                val indexToScroll = browsers.indexOfFirst { it.activityInfo.packageName == savedPkg }
                if (indexToScroll >= 0) {
                    holder.recyclerBrowser.scrollToPosition(indexToScroll)
                }
            }

            is Card3ViewHolder -> {
                holder.recyclerView.layoutManager = LinearLayoutManager(holder.itemView.context)
                holder.recyclerView.adapter = HistoryAdapter(
                    listOf("https://google.com", "https://example.com", "https://suspicious.com")
                )
            }
        }
    }


    class Card1ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val editText: EditText = itemView.findViewById(R.id.editTextUrl)
        val checkButton: Button = itemView.findViewById(R.id.checkButton)
    }

    class Card2ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val recyclerBrowser: RecyclerView = itemView.findViewById(R.id.recyclerBrowser)
    }


    class Card3ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val recyclerView: RecyclerView = itemView.findViewById(R.id.historyRecycler)
    }


    /**
     * Public function to set a URL that the first card should display.
     * This method saves the URL and then notifies the adapter to re-bind the first item.
     */
    fun updateCard1Link(link: String) {
        pendingUrlForCard1 = link
        // This tells the RecyclerView to re-bind the first item (position 0),
        // which will trigger onBindViewHolder for TYPE_CARD_1.
        notifyItemChanged(0)
    }


    // 1. Creating an inner adapter for ViewPager2 to show browser names
    // 2. Scroll to the saved/default index without animation
    // 3. Register OnPageChangeCallback to save the package name when page changes
    class BrowserItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textView: TextView = itemView.findViewById(R.id.browserNameText)
    }
}
