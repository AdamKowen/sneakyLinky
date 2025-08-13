package com.example.sneakylinky.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.sneakylinky.LinkContextCache
import com.example.sneakylinky.ui.MainActivity
import java.lang.ref.WeakReference
import kotlin.math.abs

class MyAccessibilityService : AccessibilityService() {

    // --- state ---
    private val TAG = "AccService"
    private val myPackage by lazy { applicationContext.packageName }

    companion object {
        private var activityRef: WeakReference<MainActivity>? = null
        fun setActivity(activity: MainActivity) { activityRef = WeakReference(activity) }
    }

    // --- events ---
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Ignore our own app events
        if (event.packageName?.toString() == myPackage) return

        val relevant = event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
                event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        if (!relevant) return

        val root = event.source ?: rootInActiveWindow ?: return
        if (root.packageName?.toString() == myPackage) return

        // 1) Find a URL quickly (we only proceed if link exists)
        val treeText = collectNodeText(root)
        val link = Regex("""https?://\S+""").find(treeText)?.value ?: return

        // 2) Build list of messages (generic across apps)
        val messages: List<String> = collectMessagesGeneric(root)

        // 3) Join with $$$ and push to UI BEFORE any browser navigation
        val joined = messages.filter { it.isNotBlank() }.joinToString(separator = " $$$ SEPERATOR! $$$ ")
        LinkContextCache.lastLink = link
        LinkContextCache.surroundingTxt = joined
        Log.d(TAG, "Collected ${messages.size} messages. Preview: $joined")

        activityRef?.get()?.let { act ->
            act.runOnUiThread { act.updatePasteTextInAdapter(joined) }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Service Interrupted")
    }

    // --- generic message collection ---

    /**
     * Try to collect message bubbles as separate strings in a generic way:
     * 1) If we can find a collection (RecyclerView/ListView/ScrollView) with children that have
     *    CollectionItemInfo, use (rowIndex,columnIndex) as stable message grouping.
     * 2) Fallback: group by message "item root" derived from walking up to the list container,
     *    or by y/top bounds proximity to avoid merging the entire screen.
     */
    private fun collectMessagesGeneric(root: AccessibilityNodeInfo): List<String> {
        val listContainer = findListContainer(root) ?: root

        // First attempt: use CollectionItemInfo rows
        val itemsByRow = mutableMapOf<Int, MutableList<AccessibilityNodeInfo>>()
        gatherCollectionItems(listContainer) { node, rowIndex ->
            itemsByRow.getOrPut(rowIndex) { mutableListOf() }.add(node)
        }

        val messages = mutableListOf<String>()

        if (itemsByRow.isNotEmpty()) {
            // Build message text from each row item
            val sortedRows = itemsByRow.keys.sorted()
            for (row in sortedRows) {
                val rowNodes = itemsByRow[row] ?: continue
                val sb = StringBuilder()
                rowNodes.forEach { n -> sb.append(collectNodeText(n)).append(" ") }
                val txt = normalizeMessageText(sb.toString())
                if (txt.isNotBlank()) messages.add(txt)
            }
            if (messages.isNotEmpty()) return messages
        }

        // Fallback: group by "message item root" or by Y proximity bands
        val textLeaves = gatherTextLeaves(listContainer)
        if (textLeaves.isEmpty()) return emptyList()

        // Group by ancestor item root when possible
        val byAncestor = linkedMapOf<AccessibilityNodeInfo, MutableList<AccessibilityNodeInfo>>()
        for (leaf in textLeaves) {
            val itemRoot = findMessageItemRoot(leaf) ?: continue
            byAncestor.getOrPut(itemRoot) { mutableListOf() }.add(leaf)
        }

        if (byAncestor.isNotEmpty()) {
            for ((itemRoot, leaves) in byAncestor) {
                // Use the item's subtree text rather than just leaves to include labels/time etc.
                val txt = normalizeMessageText(collectNodeText(itemRoot))
                if (txt.isNotBlank()) messages.add(txt)
            }
            if (messages.isNotEmpty()) return messages
        }

        // Last resort: bounds-based vertical clustering
        val density = resources.displayMetrics.density
        val gapThresholdPx = (12 * density).toInt() // ~12dp vertical gap threshold

        val leavesWithTop = textLeaves.mapNotNull { node ->
            val r = Rect()
            node.getBoundsInScreen(r)
            if (r.height() > 0) Pair(node, r.top) else null
        }.sortedBy { it.second }

        val clusters = mutableListOf<MutableList<AccessibilityNodeInfo>>()
        var current = mutableListOf<AccessibilityNodeInfo>()
        var lastTop: Int? = null

        for ((node, top) in leavesWithTop) {
            if (lastTop == null || abs(top - lastTop!!) <= gapThresholdPx) {
                current.add(node)
            } else {
                if (current.isNotEmpty()) clusters.add(current)
                current = mutableListOf(node)
            }
            lastTop = top
        }
        if (current.isNotEmpty()) clusters.add(current)

        for (cluster in clusters) {
            // Merge text in cluster order
            val sb = StringBuilder()
            cluster.forEach { sb.append(it.text ?: "").append(" ") }
            val txt = normalizeMessageText(sb.toString())
            if (txt.isNotBlank()) messages.add(txt)
        }

        return messages
    }

    // --- helpers ---

    // English comments only:
    // Collect children that report CollectionItemInfo; callback provides row index.
    private fun gatherCollectionItems(
        node: AccessibilityNodeInfo,
        onItem: (item: AccessibilityNodeInfo, rowIndex: Int) -> Unit
    ) {
        val q = ArrayDeque<AccessibilityNodeInfo>()
        q.add(node)
        while (q.isNotEmpty()) {
            val cur = q.removeFirst()
            val itemInfo = cur.collectionItemInfo
            if (itemInfo != null) {
                onItem(cur, itemInfo.rowIndex)
                // Do not traverse deeper from an item; treat it as an atomic message container
                continue
            }
            for (i in 0 until cur.childCount) {
                cur.getChild(i)?.let { q.add(it) }
            }
        }
    }

    // Find the main scrolling container that holds messages
    private fun findListContainer(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        val cls = node.className?.toString().orEmpty()
        if (cls.contains("RecyclerView", true) ||
            cls.contains("ListView", true) ||
            cls.contains("ScrollView", true)) {
            return node
        }
        for (i in 0 until node.childCount) {
            findListContainer(node.getChild(i))?.let { return it }
        }
        return null
    }

    // Walk up until the message-item root (child of the list container or clickable wrapper)
    private fun findMessageItemRoot(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var cur = node ?: return null
        var candidate: AccessibilityNodeInfo? = cur
        while (cur.parent != null) {
            val p = cur.parent
            val cls = p.className?.toString().orEmpty()
            if (cls.contains("RecyclerView") || cls.contains("ListView") || cls.contains("ScrollView")) {
                return cur
            }
            if ((p.isClickable || p.isLongClickable) && p.childCount > 0) {
                candidate = p
            }
            cur = p
        }
        return candidate
    }

    // Gather all leaf text nodes under a container
    private fun gatherTextLeaves(container: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val out = mutableListOf<AccessibilityNodeInfo>()
        fun dfs(n: AccessibilityNodeInfo?) {
            if (n == null) return
            if (n.packageName?.toString() == myPackage) return
            val cls = n.className?.toString().orEmpty()
            if (cls.contains("EditText", true) || cls.contains("Button", true)) {
                // skip editable/button noise
            } else {
                val hasText = !n.text.isNullOrBlank() || !n.contentDescription.isNullOrBlank()
                if (hasText && n.childCount == 0) out.add(n)
                for (i in 0 until n.childCount) dfs(n.getChild(i))
            }
        }
        dfs(container)
        return out
    }

    // Collect all text within a node subtree (excluding our app & noisy classes)
    private fun collectNodeText(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        if (node.packageName?.toString() == myPackage) return ""
        val cls = node.className?.toString().orEmpty()
        if (cls.contains("EditText", true) || cls.contains("Button", true)) return ""

        val sb = StringBuilder()
        node.text?.let { sb.append(it).append(' ') }
        node.contentDescription?.let { sb.append(it).append(' ') }
        for (i in 0 until node.childCount) {
            sb.append(collectNodeText(node.getChild(i)))
        }
        return sb.toString()
    }

    // Normalize whitespace and remove duplicate spaces
    private fun normalizeMessageText(s: String): String =
        s.replace(Regex("""\s+"""), " ").trim()
}
