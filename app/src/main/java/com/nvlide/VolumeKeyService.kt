package com.nvlide

import android.accessibilityservice.AccessibilityService
import android.content.res.Configuration
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.nvlide.ui.Overlay

/** Sees volume keys and shows the overlay. Never consumes a key: the stock panel and key behaviour stay as they are. */
class VolumeKeyService : AccessibilityService() {
    private var overlay: Overlay? = null

    override fun onServiceConnected() {
        overlay = Overlay(this, application as NvlideApp)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val volumeKey = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        if (volumeKey && event.action == KeyEvent.ACTION_DOWN) overlay?.onVolumeKey()
        return false
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        overlay?.onConfigurationChanged()
    }

    override fun onDestroy() {
        overlay?.dismiss()
        overlay = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {}

    override fun onInterrupt() {}
}
