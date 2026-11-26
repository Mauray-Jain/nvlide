package com.nvlide.ui

import com.nvlide.backend.ShizukuAudio.State

enum class OverlayContent { NONE, START_SHIZUKU, SET_UP, CARD }

/** What nvlide shows for the backend [state], whether any app qualifies, and whether the stock panel is expanded. */
fun overlayContent(state: State, hasApps: Boolean, expanded: Boolean) = when {
    !expanded -> OverlayContent.NONE
    state == State.READY -> if (hasApps) OverlayContent.CARD else OverlayContent.NONE
    state == State.NOT_RUNNING -> OverlayContent.START_SHIZUKU
    state == State.NO_PERMISSION -> OverlayContent.SET_UP
    else -> OverlayContent.NONE
}
