package de.mm20.launcher2.comms.ermis

import javax.mail.AuthenticationFailedException
import javax.mail.MessagingException
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.*
import org.junit.Test

class ErmisMailFailureTest {
    @Test fun wrappedTlsFailureKeepsItsMeaningAndEndpoint() {
        val failure = ErmisConnectionException("IMAP imap.example.org:993", MessagingException("untrusted server text", SSLHandshakeException("certificate")))
        val causes = ErmisMailFailure.causes(failure)
        assertEquals(ErmisFailureKind.TLS, ErmisMailFailure.kind(causes))
        assertEquals("IMAP imap.example.org:993", causes.filterIsInstance<ErmisConnectionException>().single().endpoint)
    }
    @Test fun googleConsentIsDistinguishedFromRejectedPassword() {
        assertEquals(ErmisFailureKind.GOOGLE_ACCESS, ErmisMailFailure.kind(ErmisMailFailure.causes(ErmisConnectionException("SMTP", ErmisGoogleAccessNotGrantedException()))))
        assertEquals(ErmisFailureKind.AUTHENTICATION, ErmisMailFailure.kind(ErmisMailFailure.causes(AuthenticationFailedException())))
    }
    @Test fun maliciousOrCyclicExceptionChainsStayBounded() {
        val first = RuntimeException("first")
        val second = RuntimeException("second")
        first.initCause(second)
        second.initCause(first)
        assertEquals(2, ErmisMailFailure.causes(first).size)
        var chain: Throwable = RuntimeException()
        repeat(40) { chain = RuntimeException(chain) }
        assertEquals(16, ErmisMailFailure.causes(chain).size)
    }
}
