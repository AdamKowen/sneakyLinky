package com.example.sneakylinky.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
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
class CardAdapter(private val context: Context, private val onCheckUrl: (String) -> Unit, private val onAnalyzeText: (String) -> Unit) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {



    // SharedPreferences keys
    private val PREFS_NAME = "sneaky_linky_prefs"
    private val KEY_HISTORY = "history_urls"

    // Load persisted history into a mutable list
    private val historyList: MutableList<String> = loadHistoryFromPrefs().toMutableList()


    // This variable will hold the URL that needs to be displayed in the first card.
    private var pendingUrlForCard1: String? = null


    // Paste
    private var pendingTextForCardPaste: String? = null

    companion object {
        private const val TYPE_CARD_1 = 0
        private const val TYPE_CARD_PASTE = 1
        private const val TYPE_CARD_2 = 2
        private const val TYPE_CARD_3 = 3
    }

    override fun getItemCount(): Int = 4

    override fun getItemViewType(position: Int): Int = when (position) {
        0 -> TYPE_CARD_1
        1 -> TYPE_CARD_PASTE
        2 -> TYPE_CARD_2
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
            TYPE_CARD_PASTE -> {
                val v = inflater.inflate(R.layout.check_text_card, parent, false)
                PasteViewHolder(v)
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


    class PasteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val edit: EditText = itemView.findViewById(R.id.longTextInput)
        val btn : Button   = itemView.findViewById(R.id.analyzeButton)
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

                // in Card1ViewHolder binding (comments in English only)
                holder.editText.apply {
                    setHorizontallyScrolling(true)
                    isHorizontalScrollBarEnabled = true
                    // Optional: auto-scroll to end when focused
                    setOnFocusChangeListener { _, hasFocus ->
                        if (hasFocus) setSelection(text?.length ?: 0)
                    }
                    imeOptions = EditorInfo.IME_ACTION_DONE
                }



            }
            is PasteViewHolder -> {
                pendingTextForCardPaste?.let { txt ->
                    holder.edit.setText(txt)
                    pendingTextForCardPaste = null
                }

                // English hint for the empty EditText
                holder.edit.hint = "Enter text here"
                // English label for the analyze button
                holder.btn.text = "Analyze"

                holder.btn.setOnClickListener {
                    val txt = holder.edit.text.toString()
                    // delegate the entered text to the analysis function
                    onAnalyzeText(txt)
                }
            }
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
                holder.recyclerView.adapter = HistoryAdapter(historyList)
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
     * Public function to set a URL that the first card should display,
     * and also add it to historyList (persisted) and refresh cards #1 and #3.
     */
    fun updateCard1Link(link: String) {
        pendingUrlForCard1 = link

        // 1) Add the new link at index 0 (so newest appear at top)
        historyList.add(0, link)
        // 2) Persist the updated history to SharedPreferences
        saveHistoryToPrefs()

        // 3) Refresh card #1 to update EditText, and card #3 to update history list
        notifyItemChanged(0)
        notifyItemChanged(3)
    }

    /**
     * Load the persisted history from SharedPreferences.
     * Returns a List<String> of URLs (newest first).
     */
    private fun loadHistoryFromPrefs(): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val allUrlsString = prefs.getString(KEY_HISTORY, "") ?: ""
        if (allUrlsString.isEmpty()) {
            return emptyList()
        }
        // Split by '\n' into a list; newest at index 0
        return allUrlsString.split("\n")
    }

    /**
     * Save the current historyList into SharedPreferences as a single
     * newline-separated string.
     */
    private fun saveHistoryToPrefs() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // Join the list by '\n'
        val joined = historyList.joinToString(separator = "\n")
        prefs.edit()
            .putString(KEY_HISTORY, joined)
            .apply()
    }

    fun updatePasteText(text: String) {
        pendingTextForCardPaste = text
        // force rebind of card #1=TYPE_CARD_PASTE (index=1)
        notifyItemChanged(1)
    }

    // 1. Creating an inner adapter for ViewPager2 to show browser names
    // 2. Scroll to the saved/default index without animation
    // 3. Register OnPageChangeCallback to save the package name when page changes
    class BrowserItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textView: TextView = itemView.findViewById(R.id.browserNameText)
    }
}



