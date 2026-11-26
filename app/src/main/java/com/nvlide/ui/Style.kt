package com.nvlide.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable

/** Visual tokens measured from the stock Nothing OS volume panel (see PLAN.md). */
internal object Style {
    const val SURFACE = 0xFF303030.toInt()
    const val FILL = 0xFFF1F1F1.toInt()
    const val DOT = 0xFF474747.toInt()
    const val RED = 0xFFD71921.toInt()
    /** Stock stream icon tint (the media icon itself is partly transparent, so it reads dimmer). */
    const val ICON = 0xFFBFBFBF.toInt()
    /** One slider column of the stock panel. */
    const val COLUMN_DP = 58
    /** Stock slider row height, for the setup screen's copy; the overlay uses the live stock row instead. */
    const val ROW_HEIGHT_DP = 202
    /** Corner radius of the expanded stock panel's box. */
    const val BOX_RADIUS_DP = 22
    const val MUTED_ICON_ALPHA = 0.4f
    /** An app slider nvlide can't drive (low-latency audio). */
    const val UNAVAILABLE_ALPHA = 0.35f

    /** The stock panel's dark surface with corners of [radiusDp]. */
    fun surface(context: Context, radiusDp: Int) = GradientDrawable().apply {
        setColor(SURFACE)
        cornerRadius = context.dp(radiusDp).toFloat()
    }
}

internal fun Context.dp(value: Number) = (value.toFloat() * resources.displayMetrics.density + 0.5f).toInt()
