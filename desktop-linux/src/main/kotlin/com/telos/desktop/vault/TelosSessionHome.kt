package com.telos.desktop.vault

import java.io.File
import java.nio.file.Path
import kotlin.io.path.Path

// === TELOS_PENDING_REVIEW_START: telos_labwc_session_p0p1 ===
object TelosSessionHome {
    val session: Boolean
        get() = System.getenv("TELOS_SESSION") == "1" ||
            System.getProperty("telos.session") == "1"

    val full: Boolean
        get() = session || System.getenv("TELOS_SESSION_HOME") == "full"

    val alreadyUnlocked: Boolean
        get() = System.getenv("TELOS_VAULT_UNLOCKED") == "1" ||
            System.getProperty("telos.alreadyUnlocked") == "1"

    /** Host (pre-vault) home. After P1, `$HOME` is the vault mount. */
    val hostHome: Path
        get() {
            val env = System.getenv("TELOS_HOST_HOME")
            if (!env.isNullOrBlank()) return Path(env)
            return Path(System.getProperty("user.home"))
        }

    val vaultMount: File?
        get() = System.getenv("TELOS_VAULT_MOUNT")?.takeIf { it.isNotBlank() }?.let { File(it) }
}
// === TELOS_PENDING_REVIEW_END: telos_labwc_session_p0p1 ===
