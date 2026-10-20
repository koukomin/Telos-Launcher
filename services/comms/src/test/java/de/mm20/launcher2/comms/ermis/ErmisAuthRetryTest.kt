package de.mm20.launcher2.comms.ermis

import javax.mail.AuthenticationFailedException
import javax.mail.MessagingException
import org.junit.Assert.*
import org.junit.Test

class ErmisAuthRetryTest {
    @Test fun rejectedGoogleTokenIsInvalidatedAndRefreshedOnce() {
        val attempts = mutableListOf<String>()
        val invalidated = mutableListOf<String>()
        var refreshes = 0
        val result = ErmisAuthRetry.connect("expired", true, { invalidated += it }, { refreshes++; "fresh" }) { token ->
            attempts += token
            if (token == "expired") throw AuthenticationFailedException()
            "connected"
        }
        assertEquals("connected", result)
        assertEquals(listOf("expired", "fresh"), attempts)
        assertEquals(listOf("expired"), invalidated)
        assertEquals(1, refreshes)
    }
    @Test fun revokedAccessDoesNotCauseAnInfiniteRefreshLoop() {
        var attempts = 0
        var refreshes = 0
        val invalidated = mutableListOf<String>()
        assertThrows(AuthenticationFailedException::class.java) {
            ErmisAuthRetry.connect("old", true, { invalidated += it }, { refreshes++; "new" }) {
                attempts++; throw AuthenticationFailedException()
            }
        }
        assertEquals(2, attempts)
        assertEquals(1, refreshes)
        assertEquals(listOf("old", "new"), invalidated)
    }
    @Test fun neverRetriesPasswordOrNetworkFailures() {
        for (oauth in listOf(false, true)) {
            var attempts = 0
            val failure = if (oauth) MessagingException("Network unavailable") else AuthenticationFailedException()
            val caught = assertThrows(MessagingException::class.java) {
                ErmisAuthRetry.connect("secret", oauth, { fail("Must not invalidate") }, { fail("Must not refresh"); "" }) {
                    attempts++; throw failure
                }
            }
            assertSame(failure, caught)
            assertEquals(1, attempts)
        }
    }
}
