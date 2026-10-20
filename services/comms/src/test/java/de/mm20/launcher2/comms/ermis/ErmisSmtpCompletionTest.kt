package de.mm20.launcher2.comms.ermis

import org.junit.Assert.*
import org.junit.Test

class ErmisSmtpCompletionTest {
    @Test fun acceptedMessageRemainsSuccessfulWhenQuitFails() {
        var sends = 0
        ErmisSmtpCompletion.send({ sends++ }, { error("QUIT connection lost") })
        assertEquals(1, sends)
    }
    @Test fun originalSendFailureSurvivesDisconnectFailureWithoutRetry() {
        val rejected = IllegalStateException("SMTP failed")
        var sends = 0
        val failure = assertThrows(IllegalStateException::class.java) {
            ErmisSmtpCompletion.send({ sends++; throw rejected }, { error("QUIT failed") })
        }
        assertSame(rejected, failure)
        assertEquals(1, sends)
    }
}
