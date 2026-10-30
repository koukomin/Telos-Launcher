package com.telos.desktop.session

import com.telos.desktop.linux.Sys

object IdleInhibit {
    @Volatile
    private var held = false

    fun update(playing: Boolean) {
        if (playing && !held) {
            held = Sys.runBg("systemd-inhibit", "--what=idle:sleep", "--who=Telos", "--why=media", "sleep", "infinity")
            if (!held) held = false
        }
        if (!playing && held) {
            Sys.run("pkill", "-f", "systemd-inhibit --what=idle:sleep --who=Telos")
            held = false
        }
    }
}
