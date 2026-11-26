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

    @Test
    fun lowLatencyMarkIsKeptPerPackage() {
        assertEquals(false, store.isLowLatency("a"))
        store.setLowLatency("a", true)
        assertEquals(true, store.isLowLatency("a"))
        assertEquals(false, store.isLowLatency("b"))
        store.setLowLatency("a", false)
        assertEquals(false, store.isLowLatency("a"))
    }

    @Test
    fun allListsEverySavedPackage() {
        store.put("a", AppVolume(40, true))
        store.put("b", AppVolume(100, false))
        assertEquals(mapOf("a" to AppVolume(40, true), "b" to AppVolume(100, false)), store.all())
    }
}
