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



    companion object {
        private var activityRef: WeakReference<MainActivity>? = null

        fun setActivity(activity: MainActivity) { activityRef = WeakReference(activity) }

        // Allow other components (e.g., LinkRelayActivity) to push text into the UI card.
        fun pushTextToUi(text: String) {
            activityRef?.get()?.runOnUiThread {
                activityRef?.get()?.updatePasteTextInAdapter(text)
            }
        }
    }

    private val TAG = "AccService"
    private val myPackage by lazy { applicationContext.packageName }
    private val BAD_PKGS = setOf(
        "com.android.settings",
        "com.google.android.apps.nexuslauncher",
        "com.android.systemui"
    )

    // lock state
    private var lockUntil: Long = 0

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val now = System.currentTimeMillis()

        // If still locked, skip everything
        if (now < lockUntil) {
            return
        }

        val fromPkg = event.packageName?.toString() ?: return
        if (fromPkg == myPackage || fromPkg in BAD_PKGS) return

        val relevant = event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
                event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        if (!relevant) return

        val root = event.source ?: rootInActiveWindow ?: return
        if (root.packageName?.toString() == myPackage || root.packageName?.toString() in BAD_PKGS) return

        // look for a link in this tree
        val text = collectNodeText(root)
        val url = Regex("""https?://\S+""").find(text)?.value ?: return

        // collect all messages
        val messages = collectMessagesGeneric(root)
        if (messages.isEmpty()) return

        val joined = messages.filter { it.isNotBlank() }.joinToString(" $$$ SEPERATOR! $$$ ")

        // Save to cache
        com.example.sneakylinky.LinkContextCache.lastLink = url
        com.example.sneakylinky.LinkContextCache.surroundingTxt = joined

        // lock for ~1 second to prevent being overwritten by the browser text
        lockUntil = now + 1000
    }

    override fun onInterrupt() {}

    // ---------- helpers below (unchanged logic, comments in English only) ----------

    private fun collectMessagesGeneric(root: AccessibilityNodeInfo): List<String> {
        val listContainer = findListContainer(root) ?: root

        val itemsByRow = mutableMapOf<Int, MutableList<AccessibilityNodeInfo>>()
        gatherCollectionItems(listContainer) { node, rowIndex ->
            itemsByRow.getOrPut(rowIndex) { mutableListOf() }.add(node)
        }

        val messages = mutableListOf<String>()
        if (itemsByRow.isNotEmpty()) {
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

        val textLeaves = gatherTextLeaves(listContainer)
        if (textLeaves.isEmpty()) return emptyList()

        val byAncestor = linkedMapOf<AccessibilityNodeInfo, MutableList<AccessibilityNodeInfo>>()
        for (leaf in textLeaves) {
            val itemRoot = findMessageItemRoot(leaf) ?: continue
            byAncestor.getOrPut(itemRoot) { mutableListOf() }.add(leaf)
        }

        if (byAncestor.isNotEmpty()) {
            for ((itemRoot, _) in byAncestor) {
                val txt = normalizeMessageText(collectNodeText(itemRoot))
                if (txt.isNotBlank()) messages.add(txt)
            }
            if (messages.isNotEmpty()) return messages
        }

        val density = resources.displayMetrics.density
        val gapThresholdPx = (12 * density).toInt()

        val leavesWithTop = textLeaves.mapNotNull { node ->
            val r = Rect()
            node.getBoundsInScreen(r)
            if (r.height() > 0) Pair(node, r.top) else null
        }.sortedBy { it.second }

        val clusters = mutableListOf<MutableList<AccessibilityNodeInfo>>()
        var current = mutableListOf<AccessibilityNodeInfo>()
        var lastTop: Int? = null

        for ((node, top) in leavesWithTop) {
            if (lastTop == null || kotlin.math.abs(top - lastTop!!) <= gapThresholdPx) {
                current.add(node)
            } else {
                if (current.isNotEmpty()) clusters.add(current)
                current = mutableListOf(node)
            }
            lastTop = top
        }
        if (current.isNotEmpty()) clusters.add(current)

        for (cluster in clusters) {
            val sb = StringBuilder()
            cluster.forEach { sb.append(it.text ?: "").append(" ") }
            val txt = normalizeMessageText(sb.toString())
            if (txt.isNotBlank()) messages.add(txt)
        }

        return messages
    }

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
                continue
            }
            for (i in 0 until cur.childCount) {
                cur.getChild(i)?.let { q.add(it) }
            }
        }
    }

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

    private fun normalizeMessageText(s: String): String =
        s.replace(Regex("""\s+"""), " ").trim()
}
