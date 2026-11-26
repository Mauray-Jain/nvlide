package com.nvlide.backend

import org.junit.Assert.assertEquals
import org.junit.Test

class LowLatencyTest {
    @Test
    fun pidsWithOpenStreamsAreReadFromTheAAudioServiceDump() {
        // Trimmed from `dumpsys media.aaudio` on the phone while melonDS played.
        val dump = """
            ------------ AAudio Service ------------
            Stream Handles:
                0x0000002d
            AAudioClientTracker:
              (unfrozen) client: pid = 2692 has 0 streams
              (unfrozen) client: pid = 27258 has 1 streams
                 stream: 0x0000002e
              (frozen) client: pid = 300 has 2 streams
            AAudioEndpointManager:
        """.trimIndent()
        assertEquals(setOf(27258, 300), lowLatencyPids(dump))
    }

    @Test
    fun anEmptyOrUnexpectedDumpMeansNone() {
        assertEquals(emptySet<Int>(), lowLatencyPids(""))
    }
}
