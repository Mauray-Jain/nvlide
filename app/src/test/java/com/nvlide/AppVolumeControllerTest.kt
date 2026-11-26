package com.nvlide

import org.junit.Assert.assertEquals
import org.junit.Test

class AppVolumeControllerTest {
    private val calls = mutableListOf<Pair<String, Float>>()
    private val store = AppVolumeStore(FakePrefs())
    private var time = 0L
    private val installed = mutableSetOf("spotify", "youtube", "chrome", "com.nvlide")
    private val controller = AppVolumeController(
        store = store,
        sendLevel = { pkg, volume -> calls += pkg to volume },
        isInstalled = { it in installed },
        ownPackage = "com.nvlide",
        now = { time },
    )

    private fun player(pkg: String, piid: Int, playing: Boolean = true, lowLatency: Boolean = false) =
        Player(pkg, piid, playing, lowLatency)

    @Test
    fun playerChangesNeverSetVolumes() {
        store.put("spotify", AppVolume(30, false))
        controller.onPlayers(listOf(player("spotify", 1), player("youtube", 2)))
        controller.onPlayers(listOf(player("spotify", 7)))
        assertEquals(emptyList<Pair<String, Float>>(), calls)
    }

    @Test
    fun settingALevelSendsItForThePackageAndSavesIt() {
        controller.setLevel("spotify", 50)
        assertEquals(listOf("spotify" to 0.5f), calls)
        assertEquals(AppVolume(50, false), store.get("spotify"))
    }

    @Test
    fun movingBackToFullVolumeIsStillSent() {
        controller.setLevel("spotify", 50)
        controller.setLevel("spotify", 100)
        assertEquals(listOf("spotify" to 0.5f, "spotify" to 1f), calls)
    }

    @Test
    fun toggleMuteKeepsTheLevel() {
        store.put("spotify", AppVolume(60, false))
        controller.toggleMute("spotify")
        controller.toggleMute("spotify")
        assertEquals(listOf("spotify" to 0f, "spotify" to 0.6f), calls)
        assertEquals(AppVolume(60, false), store.get("spotify"))
    }

    @Test
    fun sendAllSendsEveryAdjustedAppEvenWhenNotPlaying() {
        store.put("spotify", AppVolume(30, false))
        store.put("youtube", AppVolume(100, true))
        store.put("chrome", AppVolume(100, false))
        controller.sendAll()
        assertEquals(setOf("spotify" to 0.3f, "youtube" to 0f), calls.toSet())
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
    }

    @Test
    fun appsShowOnlyOnceOneOfTheirPlayersHasPlayed() {
        // An app that only keeps an idle player open (e.g. a system app's preloaded sounds) never shows.
        controller.onPlayers(listOf(player("chrome", 1, playing = false)))
        assertEquals(emptyList<String>(), controller.apps().map { it.packageName })
        controller.onPlayers(listOf(player("chrome", 1)))
        controller.onPlayers(listOf(player("chrome", 1, playing = false)))
        assertEquals(listOf("chrome"), controller.apps().map { it.packageName })
    }

    @Test
    fun anAdjustedAppShowsEvenBeforeItPlays() {
        // e.g. nvlide restarted while YouTube was paused: it was adjusted before, so it still gets its slider.
        store.put("youtube", AppVolume(30, false))
        controller.onPlayers(listOf(player("youtube", 1, playing = false), player("chrome", 2, playing = false)))
        assertEquals(listOf("youtube"), controller.apps().map { it.packageName })
    }

    @Test
    fun anAppIsLowLatencyIfAnyOfItsPlayersIs() {
        controller.onPlayers(listOf(player("spotify", 1, lowLatency = true), player("youtube", 2)))
        assertEquals(
            listOf("spotify" to true, "youtube" to false),
            controller.apps().map { it.packageName to it.lowLatency },
        )
    }

    @Test
    fun lowLatencyStaysWhileTheAppIsIdleAndClearsOnlyWhenItPlaysNormally() {
        // melonDS closes its low-latency stream in the background, but its player stays: still low-latency.
        controller.onPlayers(listOf(player("spotify", 1, lowLatency = true)))
        controller.onPlayers(listOf(player("spotify", 1, playing = false)))
        assertEquals(true, controller.apps().single().lowLatency)
        // Seen playing on the normal path (low-latency mode turned off in the app): it can be controlled again.
        controller.onPlayers(listOf(player("spotify", 2)))
        assertEquals(false, controller.apps().single().lowLatency)
    }

    @Test
    fun appsAreOrderedPlayingThenRecentlyStoppedThenIdle() {
        // chrome played and stopped long ago; youtube stops at t=1s.
        controller.onPlayers(listOf(player("chrome", 2)))
        time = -20_000
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
    fun anAppStaysListedForTenSecondsAfterItsLastPlayerIsReleased() {
        // YouTube releases its player between Shorts and makes a new one; the slider shouldn't blink out.
        controller.onPlayers(listOf(player("youtube", 1)))
        time = 1_000
        controller.onPlayers(emptyList())
        assertEquals(listOf("youtube" to Activity.RECENT), controller.apps().map { it.packageName to it.activity })
        time = 11_001
        assertEquals(emptyList<AppState>(), controller.apps())
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
        controller.listeners += { changes++ }
        controller.onPlayers(listOf(player("spotify", 1)))
        controller.setLevel("spotify", 10)
        controller.toggleMute("spotify")
        controller.onBackendLost()
        assertEquals(4, changes)
    }
}
