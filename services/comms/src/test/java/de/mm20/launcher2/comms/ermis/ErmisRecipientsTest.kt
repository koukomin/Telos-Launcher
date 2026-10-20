package de.mm20.launcher2.comms.ermis

import javax.mail.Message
import javax.mail.Session
import javax.mail.internet.MimeMessage
import java.util.Properties
import org.junit.Assert.*
import org.junit.Test

class ErmisRecipientsTest {
    @Test fun ccAndBccEnterEnvelopeWhileBccNeverEntersMimeHeaders() {
        val recipients = ErmisRecipients.parse("Alice <alice@example.org>", "copy@example.org", "secret@example.org")
        val message = MimeMessage(Session.getInstance(Properties()))
        recipients.apply(message)
        message.setText("Test"); message.saveChanges()
        assertEquals(listOf("alice@example.org", "copy@example.org", "secret@example.org"), recipients.envelope.map { it.address })
        assertEquals("copy@example.org", message.getRecipients(Message.RecipientType.CC).single().toString())
        assertNull(message.getHeader("Bcc"))
        val bytes = java.io.ByteArrayOutputStream().also { message.writeTo(it) }.toString("UTF-8")
        assertFalse(bytes.contains("secret@example.org"))
    }
    @Test fun bccOnlyMailHasARecipientAndEmptyMailIsRejected() {
        assertEquals("hidden@example.org", ErmisRecipients.parse("", "", "hidden@example.org").envelope.single().address)
        assertThrows(IllegalArgumentException::class.java) { ErmisRecipients.parse("", "", "") }
    }
    @Test fun rejectsAddressAndReplyHeaderInjectionAndGroups() {
        for (target in 0..2) {
            val fields = MutableList(3) { "" }; fields[target] = "safe@example.org\r\nBcc: injected@example.org"
            assertThrows(IllegalArgumentException::class.java) { ErmisRecipients.parse(fields[0], fields[1], fields[2]) }
        }
        assertThrows(IllegalArgumentException::class.java) { ErmisRecipients.parse("list: one@example.org;", "", "") }
        assertThrows(IllegalArgumentException::class.java) { ErmisRecipients.validateHeaders("Subject\nBcc: leaked", null, emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { ErmisRecipients.validateHeaders("Subject", "<ok>\r\nInjected: value", emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { ErmisRecipients.validateHeaders("Subject", null, listOf("<ok>\nInjected: value")) }
    }
    @Test fun recipientLimitsApplyAcrossAllThreeFields() {
        val many = (1..100).joinToString(",") { "person$it@example.org" }
        assertEquals(100, ErmisRecipients.parse(many, "", "").envelope.size)
        assertThrows(IllegalArgumentException::class.java) { ErmisRecipients.parse(many, "extra@example.org", "") }
    }
}
