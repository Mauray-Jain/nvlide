package com.nvlide.backend;

// Pushed by the privileged service whenever the set of open audio players changes.
// The three arrays are parallel: one entry per player.
oneway interface IPlayersListener {
    void onPlayers(in String[] packages, in int[] piids, in boolean[] playing);
}
