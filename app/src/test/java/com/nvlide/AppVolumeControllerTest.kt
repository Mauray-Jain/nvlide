package com.nvlide

import org.junit.Assert.assertEquals
import org.junit.Test

class AppVolumeControllerTest {
    private val calls = mutableListOf<Pair<Int, Float>>()
    private val store = AppVolumeStore(FakePrefs())
    private var time = 0L
    private val installed = mutableSetOf("spotify", "youtube", "chrome", "com.nvlide")
    private val controller = AppVolumeController(
        store = store,
        setVolume = { piid, volume -> calls += piid to volume },
        isInstalled = { it in installed },
        ownPackage = "com.nvlide",
        now = { time },
    )

    private fun player(pkg: String, piid: Int, playing: Boolean = true) = Player(pkg, piid, playing)

    @Test
    fun appsThatWereNeverAdjustedGetNoCalls() {
        controller.onPlayers(listOf(player("spotify", 1), player("youtube", 2)))
        assertEquals(emptyList<Pair<Int, Float>>(), calls)
    }

    @Test
    fun savedLevelIsAppliedToNewPlayersOnce() {
        store.put("spotify", AppVolume(30, false))
        controller.onPlayers(listOf(player("spotify", 1), player("youtube", 2)))
        controller.onPlayers(listOf(player("spotify", 1, playing = false), player("youtube", 2)))
        assertEquals(listOf(1 to 0.3f), calls)
    }

    @Test
    fun nextTrackPlayerGetsTheLevelAgain() {
        store.put("spotify", AppVolume(30, false))
        controller.onPlayers(listOf(player("spotify", 1)))
        controller.onPlayers(listOf(player("spotify", 7)))
        assertEquals(listOf(1 to 0.3f, 7 to 0.3f), calls)
    }

    @Test
    fun mutedAppIsSetToZero() {
        store.put("spotify", AppVolume(100, true))
        controller.onPlayers(listOf(player("spotify", 1)))
        assertEquals(listOf(1 to 0f), calls)
    }

    @Test
    fun settingALevelAppliesToEveryPlayerOfThatAppAndIsSaved() {
        controller.onPlayers(listOf(player("spotify", 1), player("spotify", 2), player("youtube", 3)))
        controller.setLevel("spotify", 50)
        assertEquals(listOf(1 to 0.5f, 2 to 0.5f), calls)
        assertEquals(AppVolume(50, false), store.get("spotify"))
    }

    @Test
    fun movingBackToFullVolumeIsStillApplied() {
        controller.onPlayers(listOf(player("spotify", 1)))
        controller.setLevel("spotify", 50)
        controller.setLevel("spotify", 100)
        assertEquals(listOf(1 to 0.5f, 1 to 1f), calls)
    }

    @Test
    fun toggleMuteKeepsTheLevel() {
        store.put("spotify", AppVolume(60, false))
        controller.onPlayers(listOf(player("spotify", 1)))
        calls.clear()
        controller.toggleMute("spotify")
        controller.toggleMute("spotify")
        assertEquals(listOf(1 to 0f, 1 to 0.6f), calls)
        assertEquals(AppVolume(60, false), store.get("spotify"))
    }

    @Test
    fun modeBackToNormalReappliesOnlyAdjustedApps() {
        store.put("spotify", AppVolume(30, false))
        controller.onPlayers(listOf(player("spotify", 1), player("youtube", 2)))
        calls.clear()
        controller.reapplyAll()
        assertEquals(listOf(1 to 0.3f), calls)
    }

    @Test
    fun reconnectReappliesAdjustedApps() {
        store.put("spotify", AppVolume(30, false))
        controller.onPlayers(listOf(player("spotify", 1)))
        controller.onBackendLost()
        controller.onPlayers(listOf(player("spotify", 1)))
        assertEquals(listOf(1 to 0.3f, 1 to 0.3f), calls)
    }

    @Test
    fun backendLostClearsTheAppList() {
        controller.onPlayers(listOf(player("spotify", 1)))
        controller.onBackendLost()
        assertEquals(emptyList<AppState>(), controller.apps())
    }

    @Test
    fun ownAppAndUninstalledPackagesAreSkipped() {
        store.put("gone", AppVolume(30, false))
        controller.onPlayers(listOf(player("com.nvlide", 1), player("gone", 2), player("chrome", 3)))
        assertEquals(listOf("chrome"), controller.apps().map { it.packageName })
        assertEquals(emptyList<Pair<Int, Float>>(), calls)
    }

    @Test
    fun appsAreOrderedPlayingThenRecentlyStoppedThenIdle() {
        // chrome was never seen playing; youtube stops at t=1s.
        controller.onPlayers(listOf(player("youtube", 1), player("chrome", 2, playing = false)))
        time = 1_000
        controller.onPlayers(
            listOf(player("youtube", 1, playing = false), player("chrome", 2, playing = false), player("spotify", 3)),
        )
        time = 5_000
        assertEquals(
            listOf("spotify" to Activity.PLAYING, "youtube" to Activity.RECENT, "chrome" to Activity.IDLE),
            controller.apps().map { it.packageName to it.activity },
        )
        time = 11_001
        assertEquals(
            listOf(Activity.PLAYING, Activity.IDLE, Activity.IDLE),
            controller.apps().map { it.activity },
        )
    }

    @Test
    fun playingCountCountsAppsNotPlayers() {
        controller.onPlayers(listOf(player("spotify", 1), player("spotify", 2), player("youtube", 3, playing = false)))
        assertEquals(1, controller.playingCount())
    }

    @Test
    fun appStateCarriesTheSavedVolume() {
        store.put("spotify", AppVolume(20, true))
        controller.onPlayers(listOf(player("spotify", 1)))
        assertEquals(AppVolume(20, true), controller.apps().single().volume)
    }

    @Test
    fun changesNotifyTheListener() {
        var changes = 0
        controller.onChange = { changes++ }
        controller.onPlayers(listOf(player("spotify", 1)))
        controller.setLevel("spotify", 10)
        controller.toggleMute("spotify")
        controller.onBackendLost()
        assertEquals(4, changes)
    }
}
