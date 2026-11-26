package com.nvlide.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppSliderTest {
    @Test
    fun aMoveWithinTheTouchSlopIsUndecided() {
        assertNull(isLevelDrag(dx = 5f, dy = 6f, slop = 20f))
    }

    @Test
    fun mostlyVerticalIsALevelDragAndMostlySidewaysIsAScroll() {
        assertEquals(true, isLevelDrag(dx = 4f, dy = -30f, slop = 20f))
        assertEquals(false, isLevelDrag(dx = -40f, dy = 10f, slop = 20f))
    }

    @Test
    fun barGrowsFromARoundDotLikeTheStockOne() {
        // Measured on the stock row: 420 px track, 30 px bar; 0/16 → 30 px dot, 3/16 → 104, 11/16 → 298, 16/16 → 420.
        assertEquals(30f, barHeight(fraction = 0f, track = 420f, width = 30f), 0.5f)
        assertEquals(104f, barHeight(fraction = 3 / 16f, track = 420f, width = 30f), 1.5f)
        assertEquals(298f, barHeight(fraction = 11 / 16f, track = 420f, width = 30f), 0.5f)
        assertEquals(420f, barHeight(fraction = 1f, track = 420f, width = 30f), 0.5f)
    }
}
