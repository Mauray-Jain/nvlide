package com.nvlide.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import com.nvlide.MainActivity
import com.nvlide.NvlideApp

/**
 * The accessibility-overlay window beside the stock volume panel: a pill that expands into the app card.
 * Follows [overlayContent] whenever players or the backend change. Hides [TIMEOUT_MS] after the last key
 * press or touch, or on a tap outside.
 */
class Overlay(private val context: Context, private val app: NvlideApp) {
    private val windows = context.getSystemService(WindowManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val hide = Runnable { dismiss() }
    private val onChange = { if (root != null) render() }
    private var root: FrameLayout? = null
    private var pill: PillView? = null
    private var card: AppCardView? = null
    private var expanded = false
    private var previewing = false

    fun onVolumeKey() {
        previewing = false
        render()
        bumpTimeout()
    }

    /** Shows the pill where it would appear now, so its offset can be tuned from the setup screen. */
    fun previewPosition() {
        previewing = true
        render()
        root?.let { windows.updateViewLayout(it, params()) }
        bumpTimeout()
    }

    fun onConfigurationChanged() {
        val r = root ?: return
        card?.let { size(it) }
        windows.updateViewLayout(r, params())
    }

    fun dismiss() {
        main.removeCallbacks(hide)
        app.controller.listeners -= onChange
        app.backend.listeners -= onChange
        root?.let { windows.removeView(it) }
        root = null
        pill = null
        card = null
        expanded = false
        previewing = false
    }

    private fun content(): OverlayContent {
        val c = overlayContent(app.backend.state, app.controller.apps().isNotEmpty(), expanded)
        return if (c == OverlayContent.NONE && previewing) OverlayContent.COUNT else c
    }

    private fun render() {
        val c = content()
        if (c != OverlayContent.CARD) expanded = false
        if (c == OverlayContent.NONE) return dismiss()
        val r = root ?: touchRoot().also {
            root = it
            windows.addView(it, params())
            app.controller.listeners += onChange
            app.backend.listeners += onChange
        }
        if (c == OverlayContent.CARD) {
            (card ?: showCard(r)).update(app.controller.apps())
            return
        }
        val p = pill ?: PillView(context).also {
            r.removeAllViews()
            card = null
            it.setOnClickListener { onPillTap() }
            r.addView(it)
            pill = it
        }
        when (c) {
            OverlayContent.START_SHIZUKU -> p.showHint("Start Shizuku")
            OverlayContent.SET_UP -> p.showHint("Set up nvlide")
            else -> p.showCount(app.controller.playingCount())
        }
    }

    private fun onPillTap() {
        when (content()) {
            OverlayContent.COUNT -> {
                expanded = true
                render()
                bumpTimeout()
            }
            OverlayContent.START_SHIZUKU -> launch(context.packageManager.getLaunchIntentForPackage(SHIZUKU))
            OverlayContent.SET_UP -> launch(Intent(context, MainActivity::class.java))
            else -> Unit
        }
    }

    private fun showCard(r: FrameLayout): AppCardView {
        val c = AppCardView(context, app.controller)
        size(c)
        r.removeAllViews()
        pill = null
        card = c
        c.alpha = 0f
        r.addView(c)
        c.post {
            c.translationX = c.width.toFloat()
            c.alpha = 1f
            c.animate().translationX(0f).setDuration(Style.SLIDE_MS).start()
        }
        return c
    }

    /** Card height follows the screen: shorter sliders in landscape. Width is capped so it never leaves the screen. */
    private fun size(c: AppCardView) {
        val bounds = windows.currentWindowMetrics.bounds
        val landscape = bounds.width() > bounds.height()
        val share = if (landscape) 0.45f else 0.28f
        c.setSliderHeight((bounds.height() * share).toInt().coerceIn(context.dp(120), context.dp(280)))
        c.maxWidth = bounds.width() - context.dp(app.overlayPrefs.offset(landscape).xDp) - context.dp(16)
        c.requestLayout()
    }

    private fun params(): WindowManager.LayoutParams {
        val bounds = windows.currentWindowMetrics.bounds
        val offset = app.overlayPrefs.offset(landscape = bounds.width() > bounds.height())
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = context.dp(offset.xDp)
            y = context.dp(offset.yDp)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun touchRoot() = object : FrameLayout(context) {
        override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
            if (ev.actionMasked == MotionEvent.ACTION_OUTSIDE && !previewing) {
                dismiss()
                return true
            }
            bumpTimeout()
            return super.dispatchTouchEvent(ev)
        }
    }.apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }

    private fun bumpTimeout() {
        main.removeCallbacks(hide)
        if (root != null) main.postDelayed(hide, TIMEOUT_MS)
    }

    private fun launch(intent: Intent?) {
        dismiss()
        intent ?: return
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private companion object {
        const val TIMEOUT_MS = 3_000L
        const val SHIZUKU = "moe.shizuku.privileged.api"
    }
}
