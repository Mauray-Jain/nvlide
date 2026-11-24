package com.nvlide.ui

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView

/** The small pill beside the stock panel: "♫ 3", or a hint when nvlide can't work yet. */
class PillView(context: Context) : TextView(context) {
    init {
        background = Style.surface(context, 20)
        setTextColor(Style.FILL)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        gravity = Gravity.CENTER
        minHeight = context.dp(40)
        setPadding(context.dp(16), 0, context.dp(16), 0)
    }

    fun showCount(playing: Int) {
        text = "♫ $playing"
        contentDescription = "$playing apps playing. Open app volumes"
    }

    fun showHint(hint: String) {
        text = hint
        contentDescription = hint
    }
}
