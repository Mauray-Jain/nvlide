package com.nvlide

import android.media.AudioManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamVolumesTest {
    @Test
    fun stockRowLabelsMapToStreams() {
        assertEquals(
            listOf(AudioManager.STREAM_MUSIC, AudioManager.STREAM_RING, AudioManager.STREAM_NOTIFICATION, AudioManager.STREAM_ALARM),
            streamsFor(listOf("Media", "Ring", "Notification", "Alarm")),
        )
    }

    @Test
    fun anUnknownRowMeansNoStreams() {
        // Better to leave the stock panel alone than to cover a row nvlide can't drive.
        assertNull(streamsFor(listOf("Media", "Something new")))
        assertNull(streamsFor(emptyList()))
    }

    @Test
    fun levelAndIndexConvertBothWaysAsAShareOfTheMaximumLikeTheStockRow() {
        assertEquals(25, levelFor(index = 4, max = 16))
        assertEquals(4, indexFor(level = 25, min = 0, max = 16))
        // Alarm can't go below 1 of 16: the stock bar shows 11/16 as 69 %, not (11-1)/15.
        assertEquals(69, levelFor(index = 11, max = 16))
        assertEquals(16, indexFor(level = 100, min = 1, max = 16))
        assertEquals(1, indexFor(level = 0, min = 1, max = 16))
    }

    @Test
    fun eachStreamUsesTheStockIconAndItsStruckThroughVersionAtZero() {
        assertEquals("ic_volume_media", stockIconName(AudioManager.STREAM_MUSIC, level = 6))
        assertEquals("ic_volume_media_mute", stockIconName(AudioManager.STREAM_MUSIC, level = 0))
        assertEquals("ic_ring_volume", stockIconName(AudioManager.STREAM_RING, level = 50))
        assertEquals("ic_ring_volume_off", stockIconName(AudioManager.STREAM_RING, level = 0))
        assertEquals("ic_volume_ringer_mute", stockIconName(AudioManager.STREAM_NOTIFICATION, level = 0))
        assertEquals("ic_volume_alarm", stockIconName(AudioManager.STREAM_ALARM, level = 100))
    }
}
