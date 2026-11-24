package com.nvlide

import android.content.SharedPreferences

/** Pill position in dp: [xDp] from the right screen edge, [yDp] from the vertical centre (negative = up). */
data class Offset(val xDp: Int, val yDp: Int)

/** Where the pill sits beside the stock panel, one offset per orientation. */
class OverlayPrefs(private val prefs: SharedPreferences) {
    fun offset(landscape: Boolean): Offset {
        val default = if (landscape) DEFAULT_LANDSCAPE else DEFAULT_PORTRAIT
        val k = key(landscape)
        return Offset(prefs.getInt("$k/x", default.xDp), prefs.getInt("$k/y", default.yDp))
    }

    fun setOffset(landscape: Boolean, offset: Offset) {
        val k = key(landscape)
        prefs.edit().putInt("$k/x", offset.xDp).putInt("$k/y", offset.yDp).apply()
    }

    private fun key(landscape: Boolean) = if (landscape) "landscape" else "portrait"

    private companion object {
        val DEFAULT_PORTRAIT = Offset(88, -160)
        val DEFAULT_LANDSCAPE = Offset(88, -60)
    }
}
