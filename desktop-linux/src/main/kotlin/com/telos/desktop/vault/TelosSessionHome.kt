package com.telos.desktop.vault

// === TELOS_PENDING_REVIEW_START: telos_labwc_session_p0p1 ===
object TelosSessionHome {
    val session: Boolean
        get() = System.getenv("TELOS_SESSION") == "1" ||
            System.getProperty("telos.session") == "1"

    val full: Boolean
        get() = session || System.getenv("TELOS_SESSION_HOME") == "full"

    val alreadyUnlocked: Boolean
        get() = System.getenv("TELOS_VAULT_UNLOCKED") == "1"
}
// === TELOS_PENDING_REVIEW_END: telos_labwc_session_p0p1 ===
