package com.nvlide.backend

import android.app.ActivityManager
import android.content.Context
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
 * maps each to the app that owns it and pushes the list to the app; sets a player's volume on request.
 * All state is touched on the main looper only.
 */
class PrivilegedAudioService(context: Context) : IPrivilegedAudio.Stub() {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val activities = context.getSystemService(ActivityManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var listener: IPlayersListener? = null
    private var players = emptyMap<Int, AudioPlaybackConfiguration>()

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

    override fun setVolume(piid: Int, volume: Float) {
        main.post {
            val config = players[piid] ?: return@post
            try {
                HiddenAudio.setVolume(config, volume)
            } catch (e: Exception) {
                Log.w(TAG, "setVolume($piid) failed", e)
            }
        }
    }

    override fun destroy() {
        audio.unregisterAudioPlaybackCallback(callback)
        exitProcess(0)
    }

    private fun push(configs: List<AudioPlaybackConfiguration>) {
        val owners = processOwners()
        val kept = mutableMapOf<Int, AudioPlaybackConfiguration>()
        val packages = mutableListOf<String>()
        val piids = mutableListOf<Int>()
        val playing = mutableListOf<Boolean>()
        for (c in configs) {
            val p = try {
                HiddenAudio.read(c)
            } catch (e: Exception) {
                Log.e(TAG, "cannot read player info", e)
                return
            }
            if (p.uid < Process.FIRST_APPLICATION_UID) continue
            val pkg = owners[p.pid] ?: continue
            kept[p.piid] = c
            packages += pkg
            piids += p.piid
            playing += p.playing
        }
        players = kept
        try {
            listener?.onPlayers(packages.toTypedArray(), piids.toIntArray(), playing.toBooleanArray())
        } catch (e: RemoteException) {
            listener = null
        }
        Log.d(TAG, "players: ${packages.zip(piids)} (${configs.size} configs)")
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
    }
}
