package com.nvlide.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import android.widget.SeekBar
import kotlin.math.roundToInt

/** Vertical pill slider in the stock panel style: filled level, dotted empty part, dot at zero. */
@SuppressLint("ViewConstructor")
class AppSlider(context: Context, private val onLevel: (Int) -> Unit) : View(context) {
    var level = 100
        set(value) {
            if (field != value) { field = value; invalidate() }
        }
    var muted = false
        set(value) {
            if (field != value) { field = value; invalidate() }
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val track = RectF()
    private val clip = Path()
    private val dotRadius = context.dp(1).toFloat()
    private val dotPitch = context.dp(10).toFloat()
    private val handleRadius = context.dp(6).toFloat()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        track.set(0f, 0f, w.toFloat(), h.toFloat())
        clip.reset()
        clip.addRoundRect(track, w / 2f, w / 2f, Path.Direction.CW)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.save()
        canvas.clipPath(clip)
        paint.color = Style.TRACK
        canvas.drawRect(track, paint)

        val fillTop = h * (1 - level / 100f)
        paint.color = if (muted) Style.RED else Style.FILL
        canvas.drawRect(0f, fillTop, w, h, paint)

        paint.color = Style.DOT
        var y = dotPitch
        while (y < fillTop - dotPitch / 2) {
            canvas.drawCircle(w / 2, y, dotRadius, paint)
            y += dotPitch
        }
        canvas.restore()

        if (level == 0) {
            paint.color = if (muted) Style.RED else Style.FILL
            canvas.drawCircle(w / 2, h - w / 2, handleRadius, paint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent.requestDisallowInterceptTouchEvent(true)
                userSet(((1 - event.y / height) * 100).roundToInt())
            }
        }
        return true
    }

    private fun userSet(value: Int) {
        val new = value.coerceIn(0, 100)
        if (new == level) return
        if (new / 10 != level / 10 || new == 0 || new == 100) {
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
        level = new
        onLevel(new)
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = SeekBar::class.java.name
        info.rangeInfo = AccessibilityNodeInfo.RangeInfo.obtain(
            AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_PERCENT, 0f, 100f, level.toFloat(),
        )
        info.stateDescription = if (muted) "Muted, $level percent" else "$level percent"
        if (level < 100) info.addAction(AccessibilityAction.ACTION_SCROLL_FORWARD)
        if (level > 0) info.addAction(AccessibilityAction.ACTION_SCROLL_BACKWARD)
        info.addAction(AccessibilityAction.ACTION_SET_PROGRESS)
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        when (action) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> userSet(level + 10)
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> userSet(level - 10)
            android.R.id.accessibilityActionSetProgress ->
                userSet(arguments?.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE)?.roundToInt() ?: return false)
            else -> return super.performAccessibilityAction(action, arguments)
        }
        return true
    }
}
