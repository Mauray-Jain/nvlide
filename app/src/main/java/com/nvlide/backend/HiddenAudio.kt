package com.nvlide.backend

import android.media.AudioPlaybackConfiguration

/**
 * Reflection on the @SystemApi parts of [AudioPlaybackConfiguration] and PlayerProxy.
 * Only works in the shell-uid service: other callers get anonymized configurations.
 */
internal object HiddenAudio {
    class Info(val pid: Int, val uid: Int, val piid: Int, val playing: Boolean)

    private const val PLAYER_STATE_STARTED = 2

    private val cls = AudioPlaybackConfiguration::class.java
    private val getPid = cls.getMethod("getClientPid")
    private val getUid = cls.getMethod("getClientUid")
    private val getPiid = cls.getMethod("getPlayerInterfaceId")
    private val getState = cls.getMethod("getPlayerState")
    private val getProxy = cls.getMethod("getPlayerProxy")
    private val proxySetVolume = Class.forName("android.media.PlayerProxy")
        .getMethod("setVolume", Float::class.javaPrimitiveType)

    fun read(c: AudioPlaybackConfiguration) = Info(
        pid = getPid.invoke(c) as Int,
        uid = getUid.invoke(c) as Int,
        piid = getPiid.invoke(c) as Int,
        playing = getState.invoke(c) as Int == PLAYER_STATE_STARTED,
    )

    fun setVolume(c: AudioPlaybackConfiguration, volume: Float) {
        val proxy = getProxy.invoke(c) ?: error("no player proxy (anonymized configuration?)")
        proxySetVolume.invoke(proxy, volume)
    }
}
