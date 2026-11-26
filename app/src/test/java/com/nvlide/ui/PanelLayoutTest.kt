package com.nvlide.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PanelLayoutTest {
    // The stock slider row measured on a Nothing Phone in portrait: collapsed and expanded; one column is 152 px.
    private val collapsed = Box(32, 832, 184, 1362)
    private val expanded = Box(32, 832, 640, 1362)
    private val column = 152

    @Test
    fun stockPanelCountsAsExpandedOnceItIsWiderThanOneColumn() {
        assertEquals(false, isExpanded(collapsed, column))
        assertEquals(true, isExpanded(expanded, column))
    }

    @Test
    fun aRowThatTouchesTheScreenEdgeIsSlidingInOrOut() {
        assertNull(restingBody(Box(0, 832, 109, 1362)))
        assertEquals(expanded, restingBody(expanded))
    }
}
