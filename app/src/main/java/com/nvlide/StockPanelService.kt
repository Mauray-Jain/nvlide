package com.nvlide

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.nvlide.ui.Box
import com.nvlide.ui.Overlay
import com.nvlide.ui.StockRow
import com.nvlide.ui.restingBody

/**
 * Watches for the stock volume panel and hands its slider row (bounds and row labels) to the overlay for exactly
 * as long as the panel is on screen. Never sees or touches key events: key behaviour stays as it is.
 */
class StockPanelService : AccessibilityService() {
    private var overlay: Overlay? = null
    /** Whether the stock panel's window is up (resting or sliding); content changes only matter then. */
    private var panelUp = false
    private val main = Handler(Looper.getMainLooper())
    private var scanPending = false
    private val scan = Runnable {
        scanPending = false
        overlay?.onStockPanel(stockRow())
    }

    override fun onServiceConnected() {
        overlay = Overlay(this, application as NvlideApp)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Window changes say when the panel comes up. While it is up, System UI's content changes matter too:
        // they come first when it expands or starts sliding out (window changes only ~120 ms later). Skipping them
        // otherwise keeps nvlide idle while System UI updates the clock, notifications and so on.
        // A burst of events (the stock panel animates) costs one scan per frame, not one per event.
        if ((event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED || panelUp) && !scanPending) {
            scanPending = true
            main.postDelayed(scan, FRAME_MS)
        }
    }

    /** The stock panel's slider row, or null when the panel isn't showing or is sliding in or out. */
    private fun stockRow(): StockRow? {
        panelUp = false
        for (w in windows) {
            if (w.type != AccessibilityWindowInfo.TYPE_SYSTEM) continue
            val root = w.root ?: continue
            val row = root.findAccessibilityNodeInfosByViewId(ROW_ID).firstOrNull { it.isVisibleToUser } ?: continue
            panelUp = true
            val r = Rect().also(row::getBoundsInScreen)
            val box = restingBody(Box(r.left, r.top, r.right, r.bottom)) ?: return null
            return StockRow(box, root.findAccessibilityNodeInfosByViewId(SLIDER_ID).map(::label))
        }
        return null
    }

    /** A stock slider's label ("Media", "Ring", …): the first content description in it. */
    private fun label(node: AccessibilityNodeInfo): String {
        node.contentDescription?.takeIf { it.isNotEmpty() }?.let { return it.toString() }
        for (i in 0 until node.childCount) node.getChild(i)?.let(::label)?.takeIf { it.isNotEmpty() }?.let { return it }
        return ""
    }

    override fun onDestroy() {
        main.removeCallbacks(scan)
        overlay?.dismiss()
        overlay = null
        super.onDestroy()
    }

    override fun onInterrupt() {}

    private companion object {
        const val SYSTEM_UI = "com.android.systemui"
        const val FRAME_MS = 16L
        const val ROW_ID = "$SYSTEM_UI:id/horizontal_sliders_container"
        const val SLIDER_ID = "$SYSTEM_UI:id/volume_dialog_slider"
    }
}
