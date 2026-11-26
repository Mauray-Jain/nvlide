package com.nvlide.ui

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import com.nvlide.MainActivity
import com.nvlide.NvlideApp
import com.nvlide.StreamVolumes
import com.nvlide.streamsFor

/**
 * nvlide inside the expanded stock panel: a window exactly over the stock slider row with a copy of its stream
 * sliders (same system volumes) followed by the app sliders, swiped sideways. Everything else in the panel stays
 * Nothing's. Touching it keeps the stock panel open, so both stay and go together. Follows [overlayContent]
 * whenever players or the backend change, and stream volumes whenever they change.
 */
class Overlay(private val context: Context, private val app: NvlideApp) {
    private val windows = context.getSystemService(WindowManager::class.java)
    private val audio = context.getSystemService(AudioManager::class.java)
    private val streams = StreamVolumes(audio)
    private val onChange = { if (root != null) render() }
    private val volumeChanged = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) = onChange()
    }
    private var row: StockRow? = null
    private var root: FrameLayout? = null
    private var strip: PanelStripView? = null
    private var keptOpenAt = 0L
    private var hideMessage: Runnable? = null

    /** The stock panel's slider row moved, changed, appeared, or went away (null). */
    fun onStockPanel(row: StockRow?) {
        if (row == this.row) return
        this.row = row
        render()
    }

    fun dismiss() {
        root?.let {
            app.controller.listeners -= onChange
            app.backend.listeners -= onChange
            context.unregisterReceiver(volumeChanged)
            windows.removeView(it)
        }
        root = null
        strip = null
    }

    private fun render() {
        val r = row ?: return dismiss()
        val streamList = streamsFor(r.labels) ?: return dismiss()
        val apps = app.controller.apps()
        val c = overlayContent(app.backend.state, apps.isNotEmpty(), isExpanded(r.box, context.dp(Style.COLUMN_DP)))
        if (c == OverlayContent.NONE) return dismiss()
        val s = strip ?: show(r.box)
        when (c) {
            OverlayContent.START_SHIZUKU -> s.update(streamList, apps, "Start Shizuku") {
                launch(context.packageManager.getLaunchIntentForPackage(SHIZUKU))
            }
            OverlayContent.SET_UP -> s.update(streamList, apps, "Set up nvlide") {
                launch(Intent(context, MainActivity::class.java))
            }
            else -> s.update(streamList, apps)
        }
        root?.let { windows.updateViewLayout(it, params(r.box)) }
    }

    private fun show(box: Box): PanelStripView {
        // nvlide's own message over the row: toasts from a background service are dropped when its notifications are off.
        val message = TextView(context).apply {
            setTextColor(Style.FILL)
            setBackgroundColor(Style.SURFACE)
            gravity = Gravity.CENTER
            setPadding(context.dp(16), 0, context.dp(16), 0)
            visibility = View.GONE
            setOnClickListener { visibility = View.GONE }
        }
        val s = PanelStripView(context, app.controller, streams, app.systemUi) { text ->
            message.text = text
            message.visibility = View.VISIBLE
            message.removeCallbacks(hideMessage)
            hideMessage = Runnable { message.visibility = View.GONE }
            message.postDelayed(hideMessage, 3_000)
        }
        val r = touchRoot().apply {
            setBackgroundColor(Style.SURFACE)
            addView(s, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            addView(message, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }
        windows.addView(r, params(box))
        app.controller.listeners += onChange
        app.backend.listeners += onChange
        context.registerReceiver(volumeChanged, IntentFilter(VOLUME_CHANGED), Context.RECEIVER_NOT_EXPORTED)
        root = r
        strip = s
        return s
    }

    /** Exactly over the stock slider row, in screen coordinates like its bounds. */
    private fun params(box: Box) = WindowManager.LayoutParams(
        box.width,
        box.height,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.OPAQUE,
    ).apply {
        fitInsetsTypes = 0
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        gravity = Gravity.TOP or Gravity.START
        x = box.left
        y = box.top
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun touchRoot() = object : FrameLayout(context) {
        override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
            keepStockPanelOpen()
            return super.dispatchTouchEvent(ev)
        }
    }.apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }

    /**
     * Restarts the stock panel's hide timer, as a volume key would, without changing any volume: so it doesn't
     * close (taking nvlide with it) while a slider here is being dragged. At most once a second.
     */
    private fun keepStockPanelOpen() {
        val now = SystemClock.uptimeMillis()
        if (now - keptOpenAt < 1_000) return
        keptOpenAt = now
        audio.adjustSuggestedStreamVolume(AudioManager.ADJUST_SAME, AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI)
    }

    private fun launch(intent: Intent?) {
        dismiss()
        intent ?: return
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private companion object {
        const val SHIZUKU = "moe.shizuku.privileged.api"
        /** Sent by the system whenever a stream volume changes (keys, the stock sliders, other apps). */
        const val VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"
    }
}
