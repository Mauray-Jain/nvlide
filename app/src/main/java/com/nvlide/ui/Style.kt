package com.nvlide.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable

/** Visual tokens from PLAN.md, matching the Nothing OS volume panel. */
internal object Style {
    const val SURFACE = 0xFF1C1C1E.toInt()
    const val TRACK = 0xFF2A2A2C.toInt()
    const val FILL = 0xFFF2F2F2.toInt()
    const val DOT = 0xFF4A4A4C.toInt()
    const val RED = 0xFFD71921.toInt()
    const val SLIDE_MS = 220L
    const val PAUSED_ALPHA = 0.6f
    const val MUTED_ICON_ALPHA = 0.4f

    fun surface(context: Context, radiusDp: Int) = GradientDrawable().apply {
        setColor(SURFACE)
        cornerRadius = context.dp(radiusDp).toFloat()
    }
}

internal fun Context.dp(value: Number) = (value.toFloat() * resources.displayMetrics.density + 0.5f).toInt()
