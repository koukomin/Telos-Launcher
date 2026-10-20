package de.mm20.launcher2.comms.ermis

import org.junit.Assert.*
import org.junit.Test

class ErmisRepliesTest {
    private val mail = ErmisMessage("account", 1, from = "Sender <sender@example.org>", subject = "Test", date = 0,
        to = listOf("Me <me@example.org>", "colleague@example.org"),
        cc = listOf("COLLEAGUE@example.org", "copy@example.org", "other-me@example.org"), replyTo = "reply@example.org")
    @Test fun replyAllUsesReplyToAndRemovesOwnIdentitiesAndCrossFieldDuplicates() {
        val result = ErmisReplies.recipients(mail, listOf("ME@example.org", "other-me@example.org"), true)
        assertEquals("reply@example.org, colleague@example.org", result.to)
        assertEquals("copy@example.org", result.cc)
    }
    @Test fun normalReplyDoesNotCopyOtherRecipients() {
        val result = ErmisReplies.recipients(mail, listOf("me@example.org"), false)
        assertEquals("reply@example.org", result.to)
        assertTrue(result.cc.isEmpty())
    }
    @Test fun replyingToSentMailTargetsOriginalRecipientRatherThanSelf() {
        val result = ErmisReplies.recipients(mail.copy(from = "me@example.org", replyTo = "", cc = emptyList()), listOf("me@example.org"), false)
        assertEquals("colleague@example.org", result.to)
    }
    @Test fun rejectsInjectedReplyHeaders() {
        assertThrows(IllegalArgumentException::class.java) {
            ErmisReplies.recipients(mail.copy(replyTo = "safe@example.org\r\nBcc: hidden@example.org"), emptyList(), true)
        }
    }
}
