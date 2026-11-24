package com.nvlide.ui

import com.nvlide.backend.ShizukuAudio.State
import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayContentTest {
    @Test
    fun readyWithAppsShowsCountThenCard() {
        assertEquals(OverlayContent.COUNT, overlayContent(State.READY, hasApps = true, expanded = false))
        assertEquals(OverlayContent.CARD, overlayContent(State.READY, hasApps = true, expanded = true))
    }

    @Test
    fun readyWithoutAppsShowsNothingEvenWhenExpanded() {
        assertEquals(OverlayContent.NONE, overlayContent(State.READY, hasApps = false, expanded = false))
        assertEquals(OverlayContent.NONE, overlayContent(State.READY, hasApps = false, expanded = true))
    }

    @Test
    fun shizukuProblemsShowAHintInsteadOfTheCard() {
        assertEquals(OverlayContent.START_SHIZUKU, overlayContent(State.NOT_RUNNING, hasApps = true, expanded = true))
        assertEquals(OverlayContent.SET_UP, overlayContent(State.NO_PERMISSION, hasApps = false, expanded = false))
    }

    @Test
    fun connectingShowsNothing() {
        assertEquals(OverlayContent.NONE, overlayContent(State.CONNECTING, hasApps = true, expanded = true))
    }
}
