package de.mm20.launcher2.comms.ermis

import javax.mail.AuthenticationFailedException

/** Refresh a rejected OAuth token once; never repeat a send or retry password credentials. */
internal object ErmisAuthRetry {
    fun <T> connect(
        initialSecret: String,
        oauth: Boolean,
        invalidate: (String) -> Unit,
        refresh: () -> String,
        connect: (String) -> T,
    ): T {
        try {
            return connect(initialSecret)
        } catch (failure: AuthenticationFailedException) {
            if (!oauth) throw failure
            invalidate(initialSecret)
        }
        val fresh = refresh()
        try {
            return connect(fresh)
        } catch (failure: AuthenticationFailedException) {
            invalidate(fresh)
            throw failure
        }
    }
}
