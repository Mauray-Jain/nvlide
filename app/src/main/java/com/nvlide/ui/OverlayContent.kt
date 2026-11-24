package com.nvlide.ui

import com.nvlide.backend.ShizukuAudio.State

enum class OverlayContent { NONE, COUNT, START_SHIZUKU, SET_UP, CARD }

/** What the overlay shows for the backend [state], whether any app has a player open, and whether the user expanded it. */
fun overlayContent(state: State, hasApps: Boolean, expanded: Boolean) = when (state) {
    State.READY -> when {
        !hasApps -> OverlayContent.NONE
        expanded -> OverlayContent.CARD
        else -> OverlayContent.COUNT
    }
    State.NOT_RUNNING -> OverlayContent.START_SHIZUKU
    State.NO_PERMISSION -> OverlayContent.SET_UP
    State.CONNECTING -> OverlayContent.NONE
}
