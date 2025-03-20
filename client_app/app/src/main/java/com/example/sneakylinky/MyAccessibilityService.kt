package com.example.sneakylinky
import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.lang.ref.WeakReference

class MyAccessibilityService : AccessibilityService() {

    companion object {
        private var activityRef: WeakReference<MainActivity>? = null

        fun setActivity(activity: MainActivity) {
            activityRef = WeakReference(activity)
        }
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let {
            Log.d("MyAccessibilityService", "Event received: ${it.eventType}")
            if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED ||
                event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
                event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {

                Log.d("MyAccessibilityService", "Scanning node tree for links...")
                scanNodeTree(rootInActiveWindow)
            }
        }
    }

    private fun scanNodeTree(node: AccessibilityNodeInfo?) {
        if (node == null) return

        // עובר על כל הצאצאים של ה־node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                // מחפש טקסט שמכיל לינק
                if (child.text != null && child.text.contains("http")) {
                    val link = child.text.toString()
                    Log.d("MyAccessibilityService", "Link found in node tree: $link")

                    // מעדכן את ה-TextBox באקטיביטי
                    activityRef?.get()?.updateLink(link)
                    return
                }
                // ממשיך בסריקה ברקורסיה
                scanNodeTree(child)
            }
        }
    }


    // 🔎 פונקציה רקורסיבית לחיפוש לינק בתוך ה־AccessibilityNodeInfo
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

    override fun onInterrupt() {
        Log.d("MyAccessibilityService", "Accessibility Service Interrupted")
    }
}

