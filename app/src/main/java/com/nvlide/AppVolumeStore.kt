package com.nvlide

import android.content.SharedPreferences

/** A package's saved level (0–100 %) and mute flag. */
data class AppVolume(val level: Int = 100, val muted: Boolean = false) {
    val isAdjusted get() = level != 100 || muted
    val volume get() = if (muted) 0f else level / 100f
}

/** Package → saved level and mute, kept forever. */
class AppVolumeStore(private val prefs: SharedPreferences) {
    fun get(pkg: String) = AppVolume(prefs.getInt("$pkg/level", 100), prefs.getBoolean("$pkg/muted", false))

    fun put(pkg: String, volume: AppVolume) {
        prefs.edit().putInt("$pkg/level", volume.level).putBoolean("$pkg/muted", volume.muted).apply()
    }
}
