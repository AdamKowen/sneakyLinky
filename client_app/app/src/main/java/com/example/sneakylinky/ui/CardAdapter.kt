package com.example.sneakylinky.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
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
                val view = inflater.inflate(R.layout.settings_card, parent, false)
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

                // Set the click listener for the check button
                holder.checkButton.setOnClickListener {
                    val url = holder.editText.text.toString()
                    onCheckUrl(url) // Invoke the callback passed from MainActivity
                    Toast.makeText(holder.itemView.context, "Checked: $url", Toast.LENGTH_SHORT).show()
                }
            }
            is Card2ViewHolder -> {
                val context = holder.itemView.context
                val browsers = getInstalledBrowsers(context)
                val browserNames = browsers.map { it.loadLabel(context.packageManager).toString() }
                val packageNames = browsers.map { it.activityInfo.packageName }

                // 1. מגדירים Adapter פנימי ל-ViewPager2
                val pagerAdapter = object : RecyclerView.Adapter<BrowserItemViewHolder>() {
                    override fun onCreateViewHolder(
                        parent: ViewGroup,
                        viewType: Int
                    ): BrowserItemViewHolder {
                        val view = LayoutInflater.from(parent.context)
                            .inflate(R.layout.browser_carousel_item, parent, false)
                        return BrowserItemViewHolder(view)
                    }
                    override fun getItemCount(): Int = browserNames.size
                    override fun onBindViewHolder(itemHolder: BrowserItemViewHolder, pos: Int) {
                        itemHolder.textView.text = browserNames[pos]
                    }
                }
                holder.viewPagerBrowser.adapter = pagerAdapter
                holder.viewPagerBrowser.orientation = ViewPager2.ORIENTATION_VERTICAL


                // 2. מגלגלים (scroll) ברגע שהמשתמש נכנס לכרטיס אל הדפדפן השמור (או ל-0 אם אין ערך)
                val savedPkg = getSelectedBrowser(context)
                val defaultIndex = if (savedPkg != null && savedPkg in packageNames) {
                    packageNames.indexOf(savedPkg)
                } else {
                    0
                }
                // מגדירים ללא אנימציה, כדי שהפריט ייקפוץ ישר
                holder.viewPagerBrowser.setCurrentItem(defaultIndex, /* smoothScroll= */ false)

                // 3. מאזינים לשינוי עמוד (page) ב־ViewPager2 וכשיש עצירה מעדכנים את הבחירה
                holder.viewPagerBrowser.registerOnPageChangeCallback(
                    object : ViewPager2.OnPageChangeCallback() {
                        override fun onPageSelected(positionSelected: Int) {
                            super.onPageSelected(positionSelected)
                            // שומרים את ה-package של הדפדפן שנבחר ברגע שהעמוד השתנה
                            val pkgToSave = packageNames[positionSelected]
                            saveSelectedBrowser(context, pkgToSave)
                            Toast.makeText(
                                context,
                                "בדפדפן שנבחר: ${browserNames[positionSelected]}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
            }

            is Card3ViewHolder -> {
                // currently empty will contain settings in the future
            }
        }
    }


    class Card1ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val editText: EditText = itemView.findViewById(R.id.editTextUrl)
        val checkButton: Button = itemView.findViewById(R.id.checkButton)
    }

    class Card2ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val viewPagerBrowser: ViewPager2 = itemView.findViewById(R.id.viewPagerBrowser)
    }

    class Card3ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // empty for now, will be used for settings in the future
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
