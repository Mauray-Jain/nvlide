package com.nvlide

import android.app.Application
import android.content.pm.PackageManager
import android.content.res.Resources
import android.media.AudioManager
import com.nvlide.backend.ShizukuAudio

/** Owns the single store, backend and controller shared by the overlay and the setup screen. */
class NvlideApp : Application() {
    lateinit var controller: AppVolumeController
        private set
    lateinit var backend: ShizukuAudio
        private set

    /** System UI's resources, for its stream icons. Opened once: its APK is large and this is slow. */
    val systemUi: Resources? by lazy {
        try {
            packageManager.getResourcesForApplication("com.android.systemui")
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    override fun onCreate() {
        super.onCreate()
        backend = ShizukuAudio(
            onPlayers = { controller.onPlayers(it) },
            onLost = { controller.onBackendLost() },
            onConnected = { controller.sendAll() },
        )
        controller = AppVolumeController(
            store = AppVolumeStore(getSharedPreferences("volumes", MODE_PRIVATE)),
            sendLevel = backend::setLevel,
            isInstalled = ::isInstalled,
            ownPackage = packageName,
        )
        // The system resets player volumes around calls; put ours back once the call is over.
        getSystemService(AudioManager::class.java).addOnModeChangedListener(mainExecutor) { mode ->
            if (mode == AudioManager.MODE_NORMAL) controller.sendAll()
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
