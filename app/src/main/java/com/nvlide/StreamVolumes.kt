package com.nvlide

import android.media.AudioManager
import kotlin.math.roundToInt

/** The system stream behind each row of the expanded stock panel, by the row's label. */
private val STREAMS = mapOf(
    "Media" to AudioManager.STREAM_MUSIC,
    "Call" to AudioManager.STREAM_VOICE_CALL,
    "Ring" to AudioManager.STREAM_RING,
    "Notification" to AudioManager.STREAM_NOTIFICATION,
    "Alarm" to AudioManager.STREAM_ALARM,
)

/** System UI's own icon for each stream row; the stock panel shows the "_mute"/"_off" one at 0 %. */
private val ICONS = mapOf(
    AudioManager.STREAM_MUSIC to ("ic_volume_media" to "ic_volume_media_mute"),
    AudioManager.STREAM_VOICE_CALL to ("ic_volume_voice_chat" to "ic_volume_voice_chat_mute"),
    AudioManager.STREAM_RING to ("ic_ring_volume" to "ic_ring_volume_off"),
    AudioManager.STREAM_NOTIFICATION to ("ic_volume_ringer" to "ic_volume_ringer_mute"),
    AudioManager.STREAM_ALARM to ("ic_volume_alarm" to "ic_volume_alarm_mute"),
)

/** Name of System UI's drawable for [stream] at [level], as the stock panel shows it. */
fun stockIconName(stream: Int, level: Int): String {
    val (on, off) = ICONS.getValue(stream)
    return if (level == 0) off else on
}

/** Streams for the stock panel's row [labels], or null if any row is one nvlide can't drive (then it stays out). */
fun streamsFor(labels: List<String>): List<Int>? =
    labels.map { STREAMS[it] ?: return null }.takeIf { it.isNotEmpty() }

/** Stream volume index ↔ slider level (0–100 %), as a share of the maximum like the stock row draws it. */
fun levelFor(index: Int, max: Int) = (index * 100f / max).roundToInt()

fun indexFor(level: Int, min: Int, max: Int) = (level * max / 100f).roundToInt().coerceAtLeast(min)

/** Reads and sets system stream volumes through the public AudioManager API, for nvlide's copy of the stock rows. */
class StreamVolumes(private val audio: AudioManager) {
    fun level(stream: Int) = levelFor(audio.getStreamVolume(stream), audio.getStreamMaxVolume(stream))

    fun setLevel(stream: Int, level: Int) {
        val index = indexFor(level, audio.getStreamMinVolume(stream), audio.getStreamMaxVolume(stream))
        try {
            audio.setStreamVolume(stream, index, 0)
        } catch (e: SecurityException) {
            // Do Not Disturb: changing ring/notification to silent needs a permission nvlide doesn't ask for.
        }
    }
}
