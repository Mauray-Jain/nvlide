package com.nvlide

import android.os.SystemClock

/** One open audio player, already mapped to the app that owns it; [lowLatency]: it ignores per-player volume. */
data class Player(val packageName: String, val piid: Int, val playing: Boolean, val lowLatency: Boolean = false)

enum class Activity { PLAYING, RECENT, IDLE }

/** One app's slider; [lowLatency]: nvlide can't change its level (it plays on a low-latency stream). */
data class AppState(val packageName: String, val volume: AppVolume, val activity: Activity, val lowLatency: Boolean = false)

/**
 * Joins the open players with the saved levels and orders the apps. Levels go to the backend by package
 * ([sendLevel]); the backend applies them to that package's players, new ones included, so a new player
 * is set without a round trip through the app. Only packages the user has adjusted are ever sent. Main thread only.
 */
class AppVolumeController(
    private val store: AppVolumeStore,
    private val sendLevel: (pkg: String, volume: Float) -> Unit,
    private val isInstalled: (String) -> Boolean,
    private val ownPackage: String,
    private val now: () -> Long = SystemClock::elapsedRealtime,
) {
    /** Called on the main thread after anything shown in the card changed. */
    val listeners = mutableSetOf<() -> Unit>()

    private var players = emptyList<Player>()
    private val stoppedAt = mutableMapOf<String, Long>()
    private val played = mutableSetOf<Int>()
    private var playedApps = emptySet<String>()

    fun onPlayers(list: List<Player>) {
        val wasPlaying = playingPackages()
        val wasListed = playedApps
        players = list.filter { it.packageName != ownPackage && isInstalled(it.packageName) }
        played.retainAll(players.map { it.piid }.toSet())
        players.filter { it.playing }.forEach { played += it.piid }
        playedApps = players.filter { it.piid in played || store.has(it.packageName) }.map { it.packageName }.toSet()
        // Marked when seen on a low-latency stream; cleared only when seen playing on the normal path.
        for (p in players) {
            if (p.lowLatency) store.setLowLatency(p.packageName, true)
            else if (p.playing && players.none { it.packageName == p.packageName && it.lowLatency }) store.setLowLatency(p.packageName, false)
        }
        val t = now()
        (wasPlaying - playingPackages()).forEach { stoppedAt[it] = t }
        (wasListed - playedApps).forEach { stoppedAt.putIfAbsent(it, t) }
        notifyChanged()
    }

    /** Call when the privileged service went away. */
    fun onBackendLost() {
        players = emptyList()
        played.clear()
        playedApps = emptySet()
        stoppedAt.clear()
        notifyChanged()
    }

    /** Sends every adjusted app: on (re)connect, and after the system reset player volumes during a call. */
    fun sendAll() {
        for ((pkg, v) in store.all()) if (v.isAdjusted) sendLevel(pkg, v.volume)
    }

    fun setLevel(pkg: String, level: Int) = update(pkg) { it.copy(level = level.coerceIn(0, 100)) }

    fun toggleMute(pkg: String) = update(pkg) { it.copy(muted = !it.muted) }

    fun apps(): List<AppState> {
        val t = now()
        val playing = playingPackages()
        // Only apps that played, or that the user adjusted before: idle players kept open for later (system sounds,
        // preloaded media) don't count. An app whose last player just went stays a while, so a player swap
        // (next Short) doesn't blink.
        val recentlyGone = stoppedAt.filter { (pkg, at) -> pkg !in playedApps && t - at < RECENT_MS }.keys
        return (players.map { it.packageName }.filter { it in playedApps }.distinct() + recentlyGone).map { pkg ->
            val activity = when {
                pkg in playing -> Activity.PLAYING
                stoppedAt[pkg]?.let { t - it < RECENT_MS } == true -> Activity.RECENT
                else -> Activity.IDLE
            }
            AppState(pkg, store.get(pkg), activity, store.isLowLatency(pkg))
        }.sortedBy { it.activity }
    }

    private fun update(pkg: String, change: (AppVolume) -> AppVolume) {
        val v = change(store.get(pkg))
        store.put(pkg, v)
        sendLevel(pkg, v.volume)
        notifyChanged()
    }

    private fun notifyChanged() = listeners.toList().forEach { it() }

    private fun playingPackages() = players.filter { it.playing }.map { it.packageName }.toSet()

    private companion object {
        const val RECENT_MS = 10_000L
    }
}
