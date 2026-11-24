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
import com.nvlide.backend.ShizukuAudio

/**
 * The accessibility-overlay window beside the stock volume panel: a pill that expands into the app card.
 * Hides [TIMEOUT_MS] after the last key press or touch, or on a tap outside.
 */
class Overlay(private val context: Context, private val app: NvlideApp) {
    private val windows = context.getSystemService(WindowManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val hide = Runnable { dismiss() }
    private var root: FrameLayout? = null
    private var pill: PillView? = null
    private var card: AppCardView? = null
    private val onChange = { refresh() }

    fun onVolumeKey() {
        if (root == null) {
            if (app.backend.state == ShizukuAudio.State.READY && app.controller.apps().isEmpty()) return
            if (app.backend.state == ShizukuAudio.State.CONNECTING) return
            show()
        }
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
        root?.let { windows.removeView(it) }
        root = null
        pill = null
        card = null
    }

    private fun show() {
        val p = PillView(context).apply { setOnClickListener { onPillTap() } }
        val r = touchRoot().apply { addView(p) }
        pill = p
        root = r
        windows.addView(r, params())
        app.controller.listeners += onChange
        refresh()
    }

    private fun onPillTap() {
        when (app.backend.state) {
            ShizukuAudio.State.READY -> expand()
            ShizukuAudio.State.NOT_RUNNING -> launch(context.packageManager.getLaunchIntentForPackage(SHIZUKU))
            else -> launch(Intent(context, MainActivity::class.java))
        }
    }

    private fun expand() {
        val r = root ?: return
        val c = AppCardView(context, app.controller)
        size(c)
        c.update(app.controller.apps())
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
        bumpTimeout()
    }

    private fun refresh() {
        val state = app.backend.state
        pill?.let {
            when (state) {
                ShizukuAudio.State.READY -> it.showCount(app.controller.playingCount())
                ShizukuAudio.State.NOT_RUNNING -> it.showHint("Start Shizuku")
                else -> it.showHint("Set up nvlide")
            }
        }
        card?.update(app.controller.apps())
        if (state == ShizukuAudio.State.READY && pill != null && app.controller.apps().isEmpty()) dismiss()
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
            if (ev.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                dismiss()
                return true
            }
            bumpTimeout()
            return super.dispatchTouchEvent(ev)
        }
    }.apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }

    private fun bumpTimeout() {
        main.removeCallbacks(hide)
        main.postDelayed(hide, TIMEOUT_MS)
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
