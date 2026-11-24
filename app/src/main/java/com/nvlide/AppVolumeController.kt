package com.nvlide

import android.os.SystemClock

/** One open audio player, already mapped to the app that owns it. */
data class Player(val packageName: String, val piid: Int, val playing: Boolean)

enum class Activity { PLAYING, RECENT, IDLE }

/** One row of the app card. */
data class AppState(val packageName: String, val volume: AppVolume, val activity: Activity)

/**
 * Joins the open players with the saved levels, orders the apps and applies levels.
 * Only packages the user has adjusted ever get [setVolume] calls. Main thread only.
 */
class AppVolumeController(
    private val store: AppVolumeStore,
    private val setVolume: (piid: Int, volume: Float) -> Unit,
    private val isInstalled: (String) -> Boolean,
    private val ownPackage: String,
    private val now: () -> Long = SystemClock::elapsedRealtime,
) {
    /** Called on the main thread after anything shown in the card changed. */
    val listeners = mutableSetOf<() -> Unit>()

    private var players = emptyList<Player>()
    private val handled = mutableSetOf<Int>()
    private val stoppedAt = mutableMapOf<String, Long>()

    fun onPlayers(list: List<Player>) {
        val wasPlaying = playingPackages()
        players = list.filter { it.packageName != ownPackage && isInstalled(it.packageName) }
        val t = now()
        (wasPlaying - playingPackages()).forEach { stoppedAt[it] = t }

        handled.retainAll(players.map { it.piid }.toSet())
        for (p in players) {
            if (!handled.add(p.piid)) continue
            val v = store.get(p.packageName)
            if (v.isAdjusted) setVolume(p.piid, v.volume)
        }
        notifyChanged()
    }

    /** Call when the privileged service went away; its next player list is applied afresh. */
    fun onBackendLost() {
        players = emptyList()
        handled.clear()
        notifyChanged()
    }

    /** Re-applies every adjusted app, e.g. after the system reset player volumes during a call. */
    fun reapplyAll() {
        for (p in players) {
            val v = store.get(p.packageName)
            if (v.isAdjusted) setVolume(p.piid, v.volume)
        }
    }

    fun setLevel(pkg: String, level: Int) = update(pkg) { it.copy(level = level.coerceIn(0, 100)) }

    fun toggleMute(pkg: String) = update(pkg) { it.copy(muted = !it.muted) }

    fun playingCount() = playingPackages().size

    fun apps(): List<AppState> {
        val t = now()
        val playing = playingPackages()
        return players.map { it.packageName }.distinct().map { pkg ->
            val activity = when {
                pkg in playing -> Activity.PLAYING
                stoppedAt[pkg]?.let { t - it < RECENT_MS } == true -> Activity.RECENT
                else -> Activity.IDLE
            }
            AppState(pkg, store.get(pkg), activity)
        }.sortedBy { it.activity }
    }

    private fun update(pkg: String, change: (AppVolume) -> AppVolume) {
        val v = change(store.get(pkg))
        store.put(pkg, v)
        players.filter { it.packageName == pkg }.forEach { setVolume(it.piid, v.volume) }
        notifyChanged()
    }

    private fun notifyChanged() = listeners.toList().forEach { it() }

    private fun playingPackages() = players.filter { it.playing }.map { it.packageName }.toSet()

    private companion object {
        const val RECENT_MS = 10_000L
    }
}
