package com.example.sneakylinky.service
import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.sneakylinky.LinkContextCache
import com.example.sneakylinky.ui.MainActivity
import java.lang.ref.WeakReference




class MyAccessibilityService : AccessibilityService() {

    private var lastCandidateLink: String? = null
    private var lastCandidateNodeRef: WeakReference<AccessibilityNodeInfo>? = null
    private var lastCandidateTime: Long = 0
    private fun now() = System.currentTimeMillis()

    private val myPackage by lazy { applicationContext.packageName }
    companion object {
        private var activityRef: WeakReference<MainActivity>? = null

        fun setActivity(activity: MainActivity) {
            activityRef = WeakReference(activity)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {



        // 1.a ignore our own app events
        if (event.packageName?.toString() == myPackage) return   // <-- ignore self


        val relevant = event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
                event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED ||
                event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        if (!relevant) return


        val root = event.source ?: rootInActiveWindow ?: return

        // 1.b also bail if the root belongs to us (extra safety)
        if (root.packageName?.toString() == myPackage) return    // <-- extra guard


        val treeText = collectNodeText(root)


        val link = Regex("""https?://\S+""").find(treeText)?.value ?: return


        val nodeWithLink   = findNodeWithLink(root, link)
        val contextNode    = nodeWithLink?.parent ?: nodeWithLink
        val rawContextText = contextNode?.text?.toString()?.trim() ?: treeText


        val contextTxt = rawContextText
            .replace(link, "")                        // remove the link itself
            .replace(Regex("""\bCHECK\b"""), "")      // remove CHECK tokens
            .replace(Regex("""\s+"""), " ")           // normalize spaces
            .trim()




        LinkContextCache.lastLink       = link
        LinkContextCache.surroundingTxt = contextTxt
        Log.d("AccService", "Saved context: $contextTxt")

        activityRef?.get()?.let { act ->
            act.runOnUiThread { act.updatePasteTextInAdapter(contextTxt) }
        }

    }




    private fun scanNodeTree(node: AccessibilityNodeInfo?) {
        if (node == null) return

        // all of the nodes
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                // looks for node with text for link
                if (child.text != null && child.text.contains("http")) {
                    val link = child.text.toString()
                    Log.d("MyAccessibilityService", "Link found in node tree: $link")

                    // updates the text box
                    //activityRef?.get()?.updateLink(link)
                    return
                }
                // keep checking recurcively
                scanNodeTree(child)
            }
        }
    }


    // looks for link in AccessibilityNodeInfo
    private fun findLinkInNode(node: AccessibilityNodeInfo): String? {
        if (node.text != null && node.text.toString().contains("http")) {
            return node.text.toString()
        }

        for (i in 0 until node.childCount) {
            val result = findLinkInNode(node.getChild(i) ?: continue)
            if (result != null) return result
        }
        return null
    }



    private fun parentText(node: AccessibilityNodeInfo?): String =
        node?.parent?.text?.toString().orEmpty()


    override fun onInterrupt() {
        Log.d("MyAccessibilityService", "Accessibility Service Interrupted")
    }



    private fun collectNodeText(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""

        // skip nodes from our own app (safety)
        if (node.packageName?.toString() == myPackage) return ""   // <-- new

        // skip inputs/buttons that tend להחזיר טקסט לא-רלוונטי
        val cls = node.className?.toString().orEmpty()
        if (cls.contains("EditText", ignoreCase = true) ||
            cls.contains("Button",   ignoreCase = true)) {
            return ""                                               // <-- new
        }

        val sb = StringBuilder()
        node.text?.let { sb.append(it).append(" ") }
        node.contentDescription?.let { sb.append(it).append(" ") }
        for (i in 0 until node.childCount) {
            sb.append(collectNodeText(node.getChild(i)))
        }
        return sb.toString()
    }


    private fun findNodeWithLink(node: AccessibilityNodeInfo?, link: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.text?.contains(link) == true) return node
        for (i in 0 until node.childCount) {
            findNodeWithLink(node.getChild(i), link)?.let { return it }
        }
        return null
    }

    // Walk up until we reach the row/container of the chat message.
// We stop when the parent is a RecyclerView/ListView/ScrollView (the list),
// and return the child just below it (the message item).
    private fun findMessageItemRoot(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var cur = node ?: return null
        var candidate: AccessibilityNodeInfo? = cur

        while (cur.parent != null) {
            val p = cur.parent
            val cls = p.className?.toString().orEmpty()

            // If parent is the list container, current is the item root
            if (cls.contains("RecyclerView") ||
                cls.contains("ListView")     ||
                cls.contains("ScrollView")) {
                return cur
            }

            // Many chat apps wrap a message in a clickable/longClickable container.
            // We keep the last such container as a candidate.
            if ((p.isClickable || p.isLongClickable) && p.childCount > 0) {
                candidate = p
            }

            cur = p
        }
        return candidate
    }



    // comments in English only:
// Extract first URL from a CharSequence using a regex.
    private fun firstUrl(text: CharSequence?): String? {
        if (text == null) return null
        val m = Regex("""https?://\S+""").find(text)
        return m?.value
    }

    // Walk upwards from the clicked node to find a node whose text contains a URL.
// We check the node and a few ancestors (to catch URLSpan on a TextView wrapper).
    private fun findUrlNodeUpwards(start: AccessibilityNodeInfo?): Pair<AccessibilityNodeInfo, String>? {
        var cur = start
        var hops = 0
        while (cur != null && hops < 8) { // small, safe limit
            val link = firstUrl(cur.text)
            if (link != null) return Pair(cur, link)

            cur = cur.parent
            hops++
        }
        return null
    }


    // comments in English only:
// DFS downwards from a node to find a child whose text contains a URL.
    private fun findUrlNodeDownwards(node: AccessibilityNodeInfo?, maxDepth: Int): Pair<AccessibilityNodeInfo, String>? {
        if (node == null || maxDepth < 0) return null

        // check this node
        firstUrl(node.text)?.let { return Pair(node, it) }
        firstUrl(node.contentDescription)?.let { return Pair(node, it) }

        // then children
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findUrlNodeDownwards(child, maxDepth - 1)?.let { return it }
        }
        return null
    }


}

