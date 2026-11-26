package com.nvlide.backend;

// Pushed by the privileged service whenever the set of open audio players changes.
// The arrays are parallel: one entry per player. lowLatency: on an MMAP AAudio stream, which ignores setVolume.
oneway interface IPlayersListener {
    void onPlayers(in String[] packages, in int[] piids, in boolean[] playing, in boolean[] lowLatency);
}
