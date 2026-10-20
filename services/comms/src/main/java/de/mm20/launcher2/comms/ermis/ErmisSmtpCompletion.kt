package de.mm20.launcher2.comms.ermis

/** Disconnect failure must not turn SMTP acceptance into an invitation to send the same mail again. */
internal object ErmisSmtpCompletion {
    fun send(send: () -> Unit, disconnect: () -> Unit) {
        try { send() } finally { try { disconnect() } catch (_: Exception) { } }
    }
}
