package com.example.sneakylinky.ui

import EdgeAwareCenterSnapHelper
import android.annotation.SuppressLint
import android.content.ComponentName
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
import com.example.sneakylinky.SneakyLinkyApp
import com.example.sneakylinky.service.hotsetdatabase.HotsetSyncScheduler
import com.example.sneakylinky.util.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

//  constructor accept a callback function for URL checking
class CardAdapter(private val context: Context,
                  private val onCheckUrl: (String) -> Unit,
                  private val onAnalyzeText: (String) -> Unit,
                  private val accessibilityService: ComponentName,
                  private val openBrowserPicker: () -> Unit) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {



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
        private const val LINK_CHECK_CARD = 0
        private const val CHECK_TEXT_CARD = 1
        private const val LINK_FLOW_CARD = 2
        private const val BROWSER_PICK_CARD = 3
        private const val HISTORY_CARD = 4
    }

    override fun getItemCount(): Int = 5

    override fun getItemViewType(position: Int): Int = when (position) {
        0 -> LINK_CHECK_CARD
        1 -> CHECK_TEXT_CARD
        2 -> LINK_FLOW_CARD
        3 -> BROWSER_PICK_CARD
        else -> HISTORY_CARD
    }



    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            LINK_CHECK_CARD -> {
                val view = inflater.inflate(R.layout.link_check_card, parent, false)
                val holder = Card1ViewHolder(view)
                // Store the reference to the EditText when the ViewHolder for the first card is created
                holder
            }
            CHECK_TEXT_CARD -> {
                val v = inflater.inflate(R.layout.check_text_card, parent, false)
                PasteViewHolder(v)
            }
            LINK_FLOW_CARD -> {
                val view = inflater.inflate(R.layout.link_flow_card, parent, false)
                FlowCardViewHolder(view)
            }
            BROWSER_PICK_CARD -> {
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





    @SuppressLint("ClickableViewAccessibility")
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


                holder.editText.apply {
                    setHorizontallyScrolling(true)
                    isHorizontalScrollBarEnabled = true
                    imeOptions = EditorInfo.IME_ACTION_DONE

                    // Prevent ViewPager2 from stealing horizontal drags while editing
                    setOnTouchListener { v, _ ->
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                        false
                    }

                    // Lift the whole card (field + button) above IME when focused
                    setOnFocusChangeListener { _, hasFocus ->
                        if (hasFocus) holder.itemView.post {
                            holder.itemView.requestRectangleOnScreen(
                                android.graphics.Rect(0, 0, holder.itemView.width, holder.itemView.height),
                                true
                            )
                        }
                    }
                }

                holder.checkButton.setOnClickListener {
                    val raw = holder.editText.text.toString()
                    onCheckUrl(raw)
                    // Ensure the button area is visible if IME stays open
                    holder.itemView.post {
                        holder.itemView.requestRectangleOnScreen(
                            android.graphics.Rect(0, 0, holder.itemView.width, holder.itemView.height),
                            true
                        )
                    }
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



                holder.edit.apply {
                    // Make the edit box scroll vertically inside (no card growth)
                    movementMethod = android.text.method.ScrollingMovementMethod.getInstance()
                    isVerticalScrollBarEnabled = true

                    // Prevent ViewPager2 from intercepting scroll gestures
                    setOnTouchListener { v, _ ->
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                        false
                    }

                    // Lift the whole item above IME when focused (not just the cursor line)
                    setOnFocusChangeListener { _, hasFocus ->
                        if (hasFocus) holder.itemView.post {
                            holder.itemView.requestRectangleOnScreen(
                                android.graphics.Rect(0, 0, holder.itemView.width, holder.itemView.height),
                                true
                            )
                        }
                    }
                }

                holder.btn.setOnClickListener {
                    val txt = holder.edit.text.toString()
                    onAnalyzeText(txt)
                    // Also ensure button is visible above IME right after click/focus changes
                    holder.itemView.post {
                        holder.itemView.requestRectangleOnScreen(
                            android.graphics.Rect(0, 0, holder.itemView.width, holder.itemView.height),
                            true
                        )
                    }
                }
            }
            is Card2ViewHolder -> {
                // --- setup ---
                val ctx = holder.itemView.context
                val rv = holder.recyclerBrowser
                val browsers = getInstalledBrowsers(ctx)
                val savedPkg = getSelectedBrowser(ctx)

                // LayoutManager (vertical list)
                if (rv.layoutManager == null) {
                    rv.layoutManager = LinearLayoutManager(ctx, LinearLayoutManager.VERTICAL, false)
                }

                // Snap: center items, but clamp first to TOP and last to BOTTOM (edge-aware)
                if (rv.onFlingListener == null) {
                    EdgeAwareCenterSnapHelper().attachToRecyclerView(rv)
                }

                // RecyclerView tuning
                rv.setHasFixedSize(true)
                rv.overScrollMode = View.OVER_SCROLL_NEVER
                rv.clipToPadding = false // no top/bottom padding; snap helper clamps edges

                // Adapter
                val adapter = BrowserCarouselAdapter(browsers, ctx) { browser ->
                    val pkgName = browser.activityInfo.packageName
                    saveSelectedBrowser(ctx, pkgName)
                    // Notify the flow card to refresh its browser icon
                    notifyItemChanged(2) // LINK_FLOW_CARD index
                    UiNotices.safeToast(ctx, "Selected Browser: ${browser.loadLabel(ctx.packageManager)}",)

                    // Optional: gently bring the picked item into place
                    rv.post {
                        val pos = browsers.indexOfFirst { it.activityInfo.packageName == pkgName }
                        if (pos >= 0) rv.smoothScrollToPosition(pos)
                    }
                }
                rv.adapter = adapter

                // Scroll to saved/default after layout pass so snap can position correctly
                val indexToScroll = browsers.indexOfFirst { it.activityInfo.packageName == savedPkg }
                if (indexToScroll >= 0) {
                    rv.post { rv.scrollToPosition(indexToScroll) }
                }
            }

            is Card3ViewHolder -> {
                val ctx = holder.itemView.context

                if (holder.recyclerView.layoutManager == null) {
                    holder.recyclerView.layoutManager = LinearLayoutManager(ctx)
                }

                // set an empty adapter immediately to avoid "No adapter attached" warning
                if (holder.recyclerView.adapter == null) {
                    holder.recyclerView.adapter = HistoryAdapter(ctx, emptyList())
                }

                // initial load + wire clean button
                refreshHistoryInto(holder)

                holder.itemView.findViewById<Button?>(R.id.btnClearHistory)?.setOnClickListener {
                    com.example.sneakylinky.SneakyLinkyApp.appScope.launch {
                        // delete on IO
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            val db = com.example.sneakylinky.data.AppDatabase.getInstance(ctx)
                            db.openHelper.writableDatabase.execSQL("DELETE FROM link_history")
                        }
                        // switch to Main for UI + Toast
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            refreshHistoryInto(holder)
                            UiNotices.safeToast(ctx, "History cleared", 2500)
                        }
                    }
                }
            }

            is FlowCardViewHolder -> {
                val activity = holder.itemView.context as android.app.Activity
                if (holder.binder == null) {
                    holder.binder = com.example.sneakylinky.ui.flow.FlowCardBinder(
                        activity = activity,
                        root = holder.itemView,
                        accessibilityService = accessibilityService,
                        openBrowserPicker = openBrowserPicker
                    )
                } else {
                    // re-sync when rebinding
                    holder.binder?.onResume()
                }
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

    class FlowCardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        var binder: com.example.sneakylinky.ui.flow.FlowCardBinder? = null
    }


    /**
     * Public function to set a URL that the first card should display,
     * and also add it to historyList (persisted) and refresh cards #1 and #3.
     */
    // External helper: set URL into card #1 and refresh history card UI.
    fun updateCard1Link(link: String) {
        pendingUrlForCard1 = link
        notifyItemChanged(0) // URL field
        notifyItemChanged(4) // history card
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


    // comments in English only
    private fun refreshHistoryInto(holder: Card3ViewHolder) {
        val ctx = holder.itemView.context
        SneakyLinkyApp.appScope.launch {
            val items = withContext(kotlinx.coroutines.Dispatchers.IO) {
                // Using recentDistinct since that's what your DAO currently exposes.
                // If you add LinkHistoryDao.recent(), switch to it to show every run.
                com.example.sneakylinky.data.AppDatabase
                    .getInstance(ctx)
                    .linkHistoryDao()
                    .recent(200)
            }
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                holder.recyclerView.adapter = HistoryAdapter(ctx, items)
            }
        }
    }

    /**
     * Public, simple hook you can call from anywhere (e.g., after a run in LinkFlow)
     * to force-refresh the history card UI.
     */
    fun refreshHistoryCardNow() {
        // just ask RecyclerView to rebind the history card
        notifyItemChanged(4) // HISTORY_CARD index
    }


    fun onHostResume() {
        notifyItemChanged(2) // rebind Flow card to resync switches and icon
    }

}



