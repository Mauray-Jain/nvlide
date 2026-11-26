package com.nvlide.backend

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.RemoteException
import android.util.Log
import com.nvlide.BuildConfig
import com.nvlide.Player
import rikka.shizuku.Shizuku

/** App-side end of the backend: Shizuku permission, binding the privileged service, forwarding calls. */
class ShizukuAudio(
    private val onPlayers: (List<Player>) -> Unit,
    private val onLost: () -> Unit,
    private val onConnected: () -> Unit,
) {
    enum class State { NOT_RUNNING, NO_PERMISSION, CONNECTING, READY }

    /** Called on the main thread when [state] may have changed. */
    val listeners = mutableSetOf<() -> Unit>()

    private val main = Handler(Looper.getMainLooper())
    private var service: IPrivilegedAudio? = null
    private var bound = false

    private val args = Shizuku.UserServiceArgs(
        ComponentName(BuildConfig.APPLICATION_ID, PrivilegedAudioService::class.java.name),
    ).daemon(false).processNameSuffix("audio").debuggable(BuildConfig.DEBUG).version(BuildConfig.VERSION_CODE)

    private val listener = object : IPlayersListener.Stub() {
        override fun onPlayers(packages: Array<String>, piids: IntArray, playing: BooleanArray, lowLatency: BooleanArray) {
            val list = packages.indices.map { Player(packages[it], piids[it], playing[it], lowLatency[it]) }
            main.post { if (service != null) onPlayers(list) }
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val s = IPrivilegedAudio.Stub.asInterface(binder)
            try {
                s.setListener(listener)
                service = s
                onConnected()
            } catch (e: RemoteException) {
                Log.w(TAG, "setListener failed", e)
            }
            changed()
        }

        override fun onServiceDisconnected(name: ComponentName) = lost()
    }

    val state: State
        get() = when {
            !Shizuku.pingBinder() -> State.NOT_RUNNING
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> State.NO_PERMISSION
            service == null -> State.CONNECTING
            else -> State.READY
        }

    fun start() {
        Shizuku.addBinderReceivedListenerSticky({ bindIfAllowed() }, main)
        Shizuku.addBinderDeadListener({ bound = false; lost() }, main)
        Shizuku.addRequestPermissionResultListener({ _, _ -> bindIfAllowed() }, main)
    }

    fun requestPermission() {
        if (Shizuku.pingBinder()) Shizuku.requestPermission(0)
    }

    fun setLevel(pkg: String, volume: Float) {
        try {
            service?.setLevel(pkg, volume)
        } catch (e: RemoteException) {
            Log.w(TAG, "setLevel failed", e)
        }
    }

    private fun bindIfAllowed() {
        if (!bound && state == State.CONNECTING) {
            Shizuku.bindUserService(args, connection)
            bound = true
        }
        changed()
    }

    private fun lost() {
        if (service != null) {
            service = null
            onLost()
        }
        changed()
    }

    private fun changed() = listeners.toList().forEach { it() }

    private companion object {
        const val TAG = "nvlide.shizuku"
    }
}
