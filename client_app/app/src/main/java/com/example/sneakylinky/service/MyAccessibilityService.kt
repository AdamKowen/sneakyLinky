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
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED) return
        val src = event.source ?: return

        val url = Regex("""https?://\S+""").find(event.text.joinToString(" "))
            ?: findLinkInNode(src)

        if (url != null) {
            val contextTxt = "${parentText(src)} ${src.text}"
            LinkContextCache.lastLink       = url.toString()
            LinkContextCache.surroundingTxt = contextTxt
            Log.d("AccService", "Saved context: $contextTxt")
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


    /** מחזיר את הטקסט של ה-parent של node (אם יש) */
    private fun parentText(node: AccessibilityNodeInfo?): String =
        node?.parent?.text?.toString().orEmpty()


    override fun onInterrupt() {
        Log.d("MyAccessibilityService", "Accessibility Service Interrupted")
    }
}

