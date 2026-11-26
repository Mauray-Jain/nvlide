package com.nvlide.backend

import android.app.ActivityManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.RemoteException
import android.util.Log
import kotlin.system.exitProcess

/**
 * Shizuku user service, running in its own process as the shell uid. Watches open audio players,
 * maps each to the app that owns it and pushes the list to the app. Keeps the level of every adjusted
 * package and sets it on a new player in the same callback that reports it, so it plays loud as briefly as
 * possible. All state is touched on the main looper only.
 */
class PrivilegedAudioService(context: Context) : IPrivilegedAudio.Stub() {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val activities = context.getSystemService(ActivityManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var listener: IPlayersListener? = null
    private class Open(val pkg: String, val config: AudioPlaybackConfiguration)

    private var players = emptyMap<Int, Open>()
    private val levels = mutableMapOf<String, Float>()

    private val callback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: List<AudioPlaybackConfiguration>) = push(configs)
    }

    override fun setListener(listener: IPlayersListener) {
        main.post {
            this.listener = listener
            audio.unregisterAudioPlaybackCallback(callback)
            audio.registerAudioPlaybackCallback(callback, main)
            push(audio.activePlaybackConfigurations)
        }
    }

    override fun setLevel(packageName: String, volume: Float) {
        main.post {
            if (volume == 1f) levels -= packageName else levels[packageName] = volume
            for ((piid, p) in players) if (p.pkg == packageName) setVolume(piid, p.config, volume)
        }
    }

    override fun destroy() {
        audio.unregisterAudioPlaybackCallback(callback)
        exitProcess(0)
    }

    private fun push(configs: List<AudioPlaybackConfiguration>) {
        val owners = processOwners()
        val kept = mutableMapOf<Int, Open>()
        val packages = mutableListOf<String>()
        val piids = mutableListOf<Int>()
        val playing = mutableListOf<Boolean>()
        val aaudioPids = mutableListOf<Int?>()
        for (c in configs) {
            val p = try {
                HiddenAudio.read(c)
            } catch (e: Exception) {
                Log.e(TAG, "cannot read player info", e)
                return
            }
            if (p.uid < Process.FIRST_APPLICATION_UID || c.audioAttributes.usage !in APP_AUDIO) continue
            val pkg = owners[p.pid] ?: continue
            kept[p.piid] = Open(pkg, c)
            if (p.piid !in players) levels[pkg]?.let { setVolume(p.piid, c, it) }
            packages += pkg
            piids += p.piid
            playing += p.playing
            aaudioPids += p.pid.takeIf { p.aaudio }
        }
        // Only AAudio players can be on a low-latency stream; skip the dump when there are none.
        val lowLatencyPids = if (aaudioPids.any { it != null }) readLowLatencyPids() else emptySet()
        val lowLatency = aaudioPids.map { it != null && it in lowLatencyPids }
        players = kept
        try {
            listener?.onPlayers(packages.toTypedArray(), piids.toIntArray(), playing.toBooleanArray(), lowLatency.toBooleanArray())
        } catch (e: RemoteException) {
            listener = null
        }
        Log.d(TAG, "players: ${packages.indices.map { "${packages[it]}#${piids[it]}${if (playing[it]) " ▶" else ""}${if (lowLatency[it]) " low-latency" else ""}" }} (${configs.size} configs)")
    }

    private fun setVolume(piid: Int, config: AudioPlaybackConfiguration, volume: Float) {
        try {
            HiddenAudio.setVolume(config, volume)
        } catch (e: Exception) {
            Log.w(TAG, "setVolume($piid) failed", e)
        }
    }

    /** pid → package of every running app process; handles shared uids and `pkg:service` processes. */
    private fun processOwners(): Map<Int, String> =
        activities.runningAppProcesses.orEmpty().mapNotNull { p ->
            val pkgs = p.pkgList ?: return@mapNotNull null
            val pkg = pkgs.firstOrNull { it == p.processName.substringBefore(':') } ?: pkgs.firstOrNull()
            pkg?.let { p.pid to it }
        }.toMap()

    private companion object {
        const val TAG = "nvlide.audio"

        /** Media and game audio; leaves out calls, ringtones, alarms, notifications and UI sounds. */
        val APP_AUDIO = setOf(AudioAttributes.USAGE_MEDIA, AudioAttributes.USAGE_GAME, AudioAttributes.USAGE_UNKNOWN)
    }
}
