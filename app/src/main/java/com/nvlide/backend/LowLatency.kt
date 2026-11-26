package com.nvlide.backend

import android.util.Log

private val CLIENT = Regex("""client: pid = (\d+) has (\d+) streams""")

/**
 * Pids with an open low-latency (MMAP) AAudio stream, from `dumpsys media.aaudio`. Those streams go straight to the
 * audio hardware, past the mixer where player volumes apply, so nvlide can't change their level.
 */
fun lowLatencyPids(dump: String): Set<Int> =
    CLIENT.findAll(dump).filter { it.groupValues[2].toInt() > 0 }.map { it.groupValues[1].toInt() }.toSet()

/** Runs the dump; only works in the shell-uid service (it has the DUMP permission). Empty on failure. */
internal fun readLowLatencyPids(): Set<Int> = try {
    val process = ProcessBuilder("dumpsys", "media.aaudio").redirectErrorStream(true).start()
    lowLatencyPids(process.inputStream.bufferedReader().readText()).also { process.waitFor() }
} catch (e: Exception) {
    Log.w("nvlide.audio", "cannot read AAudio clients", e)
    emptySet()
}
