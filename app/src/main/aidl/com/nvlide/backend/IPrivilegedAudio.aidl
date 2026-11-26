package com.nvlide.backend;

import com.nvlide.backend.IPlayersListener;

interface IPrivilegedAudio {
    void setListener(IPlayersListener listener) = 1;
    // Level for every player of a package, now and for players that appear later. 1.0 = leave it alone again.
    void setLevel(String packageName, float volume) = 2;
    // Shizuku calls this transaction code when it unbinds the user service.
    void destroy() = 16777114;
}
