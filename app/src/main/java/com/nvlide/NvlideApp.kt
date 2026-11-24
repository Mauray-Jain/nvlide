package com.nvlide

import android.app.Application
import android.content.pm.PackageManager
import android.media.AudioManager
import com.nvlide.backend.ShizukuAudio
import com.nvlide.ui.Overlay

/** Owns the single store, backend and controller shared by the overlay and the setup screen. */
class NvlideApp : Application() {
    lateinit var controller: AppVolumeController
        private set
    lateinit var backend: ShizukuAudio
        private set
    lateinit var overlayPrefs: OverlayPrefs
        private set

    /** The overlay while the accessibility service is on, so the setup screen can preview the pill position. */
    var overlay: Overlay? = null

    override fun onCreate() {
        super.onCreate()
        overlayPrefs = OverlayPrefs(getSharedPreferences("overlay", MODE_PRIVATE))
        backend = ShizukuAudio(onPlayers = { controller.onPlayers(it) }, onLost = { controller.onBackendLost() })
        controller = AppVolumeController(
            store = AppVolumeStore(getSharedPreferences("volumes", MODE_PRIVATE)),
            setVolume = backend::setVolume,
            isInstalled = ::isInstalled,
            ownPackage = packageName,
        )
        // The system resets player volumes around calls; put ours back once the call is over.
        getSystemService(AudioManager::class.java).addOnModeChangedListener(mainExecutor) { mode ->
            if (mode == AudioManager.MODE_NORMAL) controller.reapplyAll()
        }
        backend.start()
    }

    private fun isInstalled(pkg: String) = try {
        packageManager.getApplicationInfo(pkg, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}
