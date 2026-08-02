package com.jarvis.assistant

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class JarvisAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile var instance: JarvisAccessibilityService? = null
        @Volatile var lastScreenText: String = ""
        @Volatile var lastApp: String = ""
        fun isEnabled() = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg == packageName) return  // ignore Jarvis's own screens
        try {
            val root = rootInActiveWindow ?: return
            val sb = StringBuilder()
            collect(root, sb)
            val text = sb.toString().trim()
            if (text.length > 5) {
                lastScreenText = text.take(4000)
                lastApp = pkg
            }
        } catch (e: Exception) { /* ignore */ }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    /** Best-effort current screen text, falling back to the last cached screen. */
    fun currentScreenText(): String {
        return try {
            val root = rootInActiveWindow
            if (root != null) {
                val sb = StringBuilder()
                collect(root, sb)
                val t = sb.toString().trim()
                if (t.length > 5) t.take(4000) else lastScreenText
            } else lastScreenText
        } catch (e: Exception) {
            lastScreenText
        }
    }

    private fun collect(node: AccessibilityNodeInfo?, sb: StringBuilder) {
        node ?: return
        val t = node.text
        if (!t.isNullOrBlank()) sb.append(t).append('\n')
        val d = node.contentDescription
        if (!d.isNullOrBlank()) sb.append(d).append('\n')
        for (i in 0 until node.childCount) collect(node.getChild(i), sb)
    }
}
