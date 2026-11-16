package com.flex.assistant.device

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay

/** Only acts when FLEX is given a command. The user must enable it manually in Android settings. */
class FlexAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile var instance: FlexAccessibilityService? = null
    }

    override fun onServiceConnected() { super.onServiceConnected(); instance = this }
    override fun onUnbind(intent: Intent?): Boolean { instance = null; return super.onUnbind(intent) }
    override fun onDestroy() { instance = null; super.onDestroy() }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    private fun find(n: AccessibilityNodeInfo?, pred: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (n == null) return null
        if (pred(n)) return n
        for (i in 0 until n.childCount) {
            val r = find(n.getChild(i), pred)
            if (r != null) return r
        }
        return null
    }

    fun clickNow(needles: List<String>, exact: Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = find(root) { nd ->
            val labels = listOfNotNull(nd.text?.toString(), nd.contentDescription?.toString())
            labels.any { label -> needles.any { w -> if (exact) label.equals(w, true) else label.contains(w, true) } }
        }
        var cur = node
        while (cur != null && !cur.isClickable) cur = cur.parent
        return cur?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
    }

    suspend fun waitAndClick(needles: List<String>, exact: Boolean, timeoutMs: Long): Boolean {
        val end = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < end) {
            if (clickNow(needles, exact)) return true
            delay(400)
        }
        return false
    }

    fun readScreen(): String {
        val root = rootInActiveWindow ?: return ""
        val sb = StringBuilder()
        collect(root, sb)
        return sb.toString().take(3500)
    }

    private fun collect(n: AccessibilityNodeInfo?, sb: StringBuilder) {
        if (n == null || sb.length > 3500) return
        val t = n.text
        if (t != null) sb.append(t).append('\n')
        else n.contentDescription?.let { sb.append(it).append('\n') }
        for (i in 0 until n.childCount) collect(n.getChild(i), sb)
    }

    fun scroll(down: Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        val n = find(root) { it.isScrollable } ?: return false
        return n.performAction(
            if (down) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
    }
}
