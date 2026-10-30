package com.telos.desktop.vault

import java.io.File

enum class BootMode {
    HostHome,
    UnlockExisting,
    CreateWizard,
    EphemeralGhost,
}

object PortableEnvironmentIsolator {
    @Volatile
    var active: Boolean = false
    var vaultHome: File? = null

    fun apply(mount: File, fullSessionHome: Boolean = TelosSessionHome.full) {
        // === TELOS_PENDING_REVIEW_START: telos_labwc_session_p0p1 ===
        val home = if (fullSessionHome) {
            mount
        } else {
            File(mount, "home").apply { mkdirs() }
        }
        File(home, "Desktop").mkdirs()
        File(home, ".config").mkdirs()
        File(home, ".local/share").mkdirs()
        File(home, ".cache").mkdirs()
        vaultHome = home
        active = true
        System.setProperty("user.home", home.absolutePath)
        System.setProperty("telos.vault.home", home.absolutePath)
        if (fullSessionHome) System.setProperty("telos.session.home", "full")
        // === TELOS_PENDING_REVIEW_END: telos_labwc_session_p0p1 ===
    }

    fun inject(pb: ProcessBuilder) {
        val home = vaultHome ?: return
        if (!active) return
        val env = pb.environment()
        env["HOME"] = home.absolutePath
        env["XDG_CONFIG_HOME"] = File(home, ".config").absolutePath
        env["XDG_DATA_HOME"] = File(home, ".local/share").absolutePath
        env["XDG_CACHE_HOME"] = File(home, ".cache").absolutePath
        env["HISTFILE"] = File(home, ".zsh_history").absolutePath
        env["XDG_DESKTOP_DIR"] = File(home, "Desktop").absolutePath
        pb.directory(home)
    }

    fun clear() {
        active = false
        vaultHome = null
    }
}
