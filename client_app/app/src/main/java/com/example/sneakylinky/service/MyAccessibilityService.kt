package com.example.sneakylinky.service
import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.sneakylinky.LinkContextCache
import com.example.sneakylinky.ui.MainActivity
import java.lang.ref.WeakReference




class MyAccessibilityService : AccessibilityService() {


    companion object {
        private var activityRef: WeakReference<MainActivity>? = null

        fun setActivity(activity: MainActivity) {
            activityRef = WeakReference(activity)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {

        val relevant = event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
                event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED ||
                event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        if (!relevant) return

        val root = event.source ?: rootInActiveWindow ?: return
        val treeText = collectNodeText(root)


        val link = Regex("""https?://\S+""").find(treeText)?.value ?: return


        val nodeWithLink   = findNodeWithLink(root, link)
        val contextNode    = nodeWithLink?.parent ?: nodeWithLink
        val rawContextText = contextNode?.text?.toString()?.trim() ?: treeText


        val contextTxt = rawContextText
            .replace(Regex("""\s+"""), " ")      // רווח אחד
            .replace(Regex("""(https?://\S+)\s+(\1)+""")) { it.groupValues[1] } // חזרתיות של אותו קישור


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

}

