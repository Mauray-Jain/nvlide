package com.nvlide

import org.junit.Assert.assertEquals
import org.junit.Test

class AppVolumeStoreTest {
    private val store = AppVolumeStore(FakePrefs())

    @Test
    fun unknownPackageIsFullVolumeUnmuted() {
        assertEquals(AppVolume(100, false), store.get("a"))
    }

    @Test
    fun savedLevelAndMuteAreReadBack() {
        store.put("a", AppVolume(40, true))
        assertEquals(AppVolume(40, true), store.get("a"))
        assertEquals(AppVolume(100, false), store.get("b"))
    }
}
