package com.nvlide.ui

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView

/** A round button in the stock panel's style, shown instead of the app card when nvlide can't work yet. */
class HintView(context: Context) : TextView(context) {
    init {
        background = Style.surface(context, Style.COLUMN_DP / 2)
        setTextColor(Style.FILL)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        gravity = Gravity.CENTER
        minHeight = context.dp(Style.COLUMN_DP)
        setPadding(context.dp(20), 0, context.dp(20), 0)
    }

    fun show(hint: String) {
        text = hint
        contentDescription = hint
    }
}
