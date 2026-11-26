package com.nvlide.ui

import com.nvlide.backend.ShizukuAudio.State
import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayContentTest {
    @Test
    fun nothingWhileTheStockPanelIsCollapsed() {
        for (state in State.entries) {
            assertEquals(OverlayContent.NONE, overlayContent(state, hasApps = true, expanded = false))
        }
    }

    @Test
    fun expandedAndReadyShowsTheAppsIfThereAreAny() {
        assertEquals(OverlayContent.CARD, overlayContent(State.READY, hasApps = true, expanded = true))
        assertEquals(OverlayContent.NONE, overlayContent(State.READY, hasApps = false, expanded = true))
    }

    @Test
    fun expandedWithShizukuProblemsShowsAHint() {
        assertEquals(OverlayContent.START_SHIZUKU, overlayContent(State.NOT_RUNNING, hasApps = true, expanded = true))
        assertEquals(OverlayContent.SET_UP, overlayContent(State.NO_PERMISSION, hasApps = false, expanded = true))
    }

    @Test
    fun connectingShowsNothing() {
        assertEquals(OverlayContent.NONE, overlayContent(State.CONNECTING, hasApps = true, expanded = true))
    }
}
