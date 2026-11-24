package com.nvlide.backend;

import com.nvlide.backend.IPlayersListener;

interface IPrivilegedAudio {
    void setListener(IPlayersListener listener) = 1;
    void setVolume(int piid, float volume) = 2;
    // Shizuku calls this transaction code when it unbinds the user service.
    void destroy() = 16777114;
}
