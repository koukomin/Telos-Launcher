package de.mm20.launcher2.comms.ermis

import com.sun.mail.imap.IMAPSSLStore
import com.sun.mail.smtp.SMTPSSLTransport
import com.sun.mail.smtp.SMTPTransport
import org.junit.Assert.*
import org.junit.Test

class ErmisMailSessionTest {
    @Test fun googleTokensNeverUsePasswordAuthenticationMechanisms() {
        val session = ErmisMailSession.create(oauth = true, tls13 = true)
        for (protocol in listOf("imaps", "smtp", "smtps")) {
            assertEquals("XOAUTH2", session.getProperty("mail.$protocol.auth.mechanisms"))
            assertEquals("true", session.getProperty("mail.$protocol.auth.plain.disable"))
            assertEquals("true", session.getProperty("mail.$protocol.auth.login.disable"))
            assertEquals("true", session.getProperty("mail.$protocol.ssl.checkserveridentity"))
        }
    }
    @Test fun passwordAccountsAndOldDevicesRetainCompatibleSecureDefaults() {
        val session = ErmisMailSession.create(oauth = false, tls13 = false)
        assertNull(session.getProperty("mail.imaps.auth.mechanisms"))
        assertEquals("TLSv1.2", session.getProperty("mail.imaps.ssl.protocols"))
        assertEquals("true", session.getProperty("mail.smtp.starttls.required"))
        assertEquals("true", session.getProperty("mail.imaps.throwsearchexception"))
        assertEquals("20000", session.getProperty("mail.smtps.timeout"))
    }
    @Test fun supportedProvidersAreConstructedDirectlyInsteadOfDiscoveredByClassName() {
        val session = ErmisMailSession.create(oauth = false, tls13 = true)
        assertTrue(ErmisMailSession.store(session) is IMAPSSLStore)
        assertTrue(ErmisMailSession.transport(session, true) is SMTPTransport)
        assertTrue(ErmisMailSession.transport(session, false) is SMTPSSLTransport)
    }
}
