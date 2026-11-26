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

    /** Whether [pkg] was last seen on a low-latency stream (its level can't be applied); kept across restarts. */
    fun isLowLatency(pkg: String) = prefs.getBoolean("$pkg/lowLatency", false)

    fun setLowLatency(pkg: String, on: Boolean) {
        if (on != isLowLatency(pkg)) prefs.edit().putBoolean("$pkg/lowLatency", on).apply()
    }

    /** Whether the user ever adjusted [pkg]. */
    fun has(pkg: String) = prefs.contains("$pkg/level")

    /** Every package the user ever adjusted. */
    fun all(): Map<String, AppVolume> =
        prefs.all.keys.filter { it.endsWith("/level") }.map { it.removeSuffix("/level") }.associateWith(::get)

    fun put(pkg: String, volume: AppVolume) {
        prefs.edit().putInt("$pkg/level", volume.level).putBoolean("$pkg/muted", volume.muted).apply()
    }
}
