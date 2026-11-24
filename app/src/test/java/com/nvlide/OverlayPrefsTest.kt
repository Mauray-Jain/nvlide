package com.nvlide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class OverlayPrefsTest {
    private val prefs = OverlayPrefs(FakePrefs())

    @Test
    fun portraitAndLandscapeOffsetsAreSeparate() {
        prefs.setOffset(landscape = false, Offset(80, -120))
        prefs.setOffset(landscape = true, Offset(140, 0))
        assertEquals(Offset(80, -120), prefs.offset(landscape = false))
        assertEquals(Offset(140, 0), prefs.offset(landscape = true))
    }

    @Test
    fun defaultsDifferPerOrientation() {
        assertNotEquals(prefs.offset(landscape = false), prefs.offset(landscape = true))
    }
}
