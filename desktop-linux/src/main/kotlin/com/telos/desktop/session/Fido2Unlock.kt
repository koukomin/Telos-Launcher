package com.telos.desktop.session

import com.telos.desktop.linux.Sys
import java.io.File

// === TELOS_PENDING_REVIEW_START: telos_labwc_session_p0p1 ===
object Fido2Unlock {
    fun available(): Boolean =
        Sys.run("which", "fido2-token").ok && Sys.run("fido2-token", "-L").stdout.isNotBlank()

    /**
     * Mixes a FIDO2 hmac-secret (if the host has libfido2 tools) into [password].
     * Missing hardware or tools returns [password] unchanged.
     */
    fun mix(password: String): String {
        if (!available()) return password
        val dev = Sys.run("fido2-token", "-L").stdout.lineSequence()
            .firstOrNull { it.contains("/dev/") }?.substringBefore(":")?.trim() ?: return password
        val cred = File(System.getProperty("user.home"), ".local/share/telos/fido2-cred")
        if (!cred.isFile) return password
        val assert = Sys.run(
            "fido2-assert", "-G", "-i", cred.absolutePath, dev,
            timeoutSec = 30,
            input = password,
        )
        if (!assert.ok || assert.stdout.isBlank()) return password
        return password + ":" + assert.stdout.trim().take(64)
    }
}
// === TELOS_PENDING_REVIEW_END: telos_labwc_session_p0p1 ===
