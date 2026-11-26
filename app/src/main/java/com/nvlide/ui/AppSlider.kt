package com.nvlide.ui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import android.view.ViewConfiguration
import android.widget.SeekBar
import kotlin.math.abs
import kotlin.math.roundToInt

/** null while a touch hasn't moved past [slop]; then true for a mostly vertical (level) drag, false for a sideways one. */
fun isLevelDrag(dx: Float, dy: Float, slop: Float): Boolean? =
    if (dx * dx + dy * dy < slop * slop) null else abs(dy) > abs(dx)

/**
 * Height of a level bar [width] wide on a [track], as the stock row draws it: a round dot at 0, growing to the
 * full track at 1 ([fraction] of the way). The same rule with the widened bar gives the stock drag look.
 */
fun barHeight(fraction: Float, track: Float, width: Float) = width + fraction * (track - width)

/**
 * Track of one slider, drawn like the stock Nothing panel: a narrow level bar under a dotted column.
 * While touched, the bar widens (and its rounded top rises by as much), then shrinks back, as the stock one does.
 */
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
    private val bar = RectF()
    private val restWidth = context.dp(11.5f).toFloat()
    private val dragWidth = context.dp(36).toFloat()
    private var grow = 0f
    private val growAnimator = ValueAnimator().apply {
        duration = 150
        addUpdateListener { grow = it.animatedValue as Float; invalidate() }
    }
    private val dotRadius = context.dp(2).toFloat()
    private val dotPitch = context.dp(10).toFloat()
    private val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private var downX = 0f
    private var downY = 0f
    private var dragging = false

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val h = height.toFloat()
        val barWidth = restWidth + (dragWidth - restWidth) * grow
        val barTop = h - barHeight(level / 100f, h, barWidth)

        paint.color = Style.DOT
        var y = dotRadius
        while (y < barTop - dotPitch / 2) {
            canvas.drawCircle(cx, y, dotRadius, paint)
            y += dotPitch
        }

        paint.color = if (muted) Style.RED else Style.FILL
        bar.set(cx - barWidth / 2, barTop, cx + barWidth / 2, h)
        canvas.drawRoundRect(bar, barWidth / 2, barWidth / 2, paint)
    }

    /**
     * Like the stock slider inside a sideways-scrolling row: a vertical drag sets the level, a sideways swipe is
     * left to the row (it takes the touch over), a tap jumps to where it lands.
     */
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false // greyed out: the column takes the tap
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                dragging = false
                animateGrow(1f)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && isLevelDrag(event.x - downX, event.y - downY, slop) == true) {
                    dragging = true
                    parent.requestDisallowInterceptTouchEvent(true)
                }
                if (dragging) userSet(levelAt(event.y))
            }
            MotionEvent.ACTION_UP -> {
                if (!dragging) userSet(levelAt(event.y))
                animateGrow(0f)
            }
            MotionEvent.ACTION_CANCEL -> animateGrow(0f)
        }
        return true
    }

    private fun levelAt(y: Float) = ((1 - y / height) * 100).roundToInt()

    private fun animateGrow(to: Float) {
        growAnimator.cancel()
        growAnimator.setFloatValues(grow, to)
        growAnimator.start()
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
