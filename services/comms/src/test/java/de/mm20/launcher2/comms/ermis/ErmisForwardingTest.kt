package de.mm20.launcher2.comms.ermis

import java.util.Properties
import javax.mail.Session
import javax.mail.internet.MimeMessage
import org.junit.Assert.*
import org.junit.Test

class ErmisForwardingTest {
    @Test fun sourceIdentityRejectsReusedUidAfterMailboxReset() {
        val source = ErmisForwardSource("account-1", "Archive", 42, 123)
        ErmisForwardPolicy.requireSameMailbox(source,123)
        assertThrows(IllegalArgumentException::class.java) { ErmisForwardPolicy.requireSameMailbox(source,124) }
        assertThrows(IllegalArgumentException::class.java) { ErmisForwardPolicy.validate(source.copy(uid=0)) }
        assertThrows(IllegalArgumentException::class.java) { ErmisForwardPolicy.validate(source.copy(uidValidity=-1)) }
        assertThrows(IllegalArgumentException::class.java) { ErmisForwardPolicy.validate(source.copy(accountId="../secret")) }
    }
    @Test fun writesRespectExactBoundaryForBothSingleAndBulkBytes() {
        val out = ErmisBoundedOutputStream(5)
        out.write(byteArrayOf(1,2,3),0,3)
        out.write(4); out.write(5)
        assertArrayEquals(byteArrayOf(1,2,3,4,5),out.toByteArray())
        assertThrows(IllegalArgumentException::class.java) { out.write(6) }
        assertThrows(IllegalArgumentException::class.java) { out.write(byteArrayOf(6),0,1) }
        assertArrayEquals(byteArrayOf(1,2,3,4,5),out.toByteArray())
    }
    @Test fun oversizedOriginalStopsWhileBeingWrittenBeforeMaterializingAttachment() {
        val out = ErmisBoundedOutputStream(100)
        assertThrows(IllegalArgumentException::class.java) { out.write(ByteArray(101),0,101) }
        assertEquals(0,out.toByteArray().size)
        assertThrows(IllegalArgumentException::class.java) { out.write(byteArrayOf(1),-1,1) }
    }
    @Test fun forwardedOriginalKeepsContentButStripsHiddenRecipientHeaders() {
        val original = MimeMessage(Session.getInstance(Properties())).apply {
            setHeader("From","sender@example.org")
            setHeader("To","recipient@example.org")
            setHeader("Bcc","secret@example.org")
            setHeader("Resent-Bcc","other-secret@example.org")
            setSubject("Original"); setText("Original content"); saveChanges()
        }
        val out = ErmisBoundedOutputStream(10_000)
        original.writeTo(out,arrayOf("Bcc","Resent-Bcc"))
        val serialized = out.toByteArray().toString(Charsets.UTF_8)
        assertTrue(serialized.contains("Original content"))
        assertTrue(serialized.contains("recipient@example.org"))
        assertFalse(serialized.contains("secret@example.org"))
        assertFalse(serialized.contains("Resent-Bcc:"))
    }
    @Test fun draftLimitCountsForwardedMessageAsAnAttachment() {
        val source=ErmisForwardSource("account-1","INBOX",1,1)
        val draft=ErmisSavedDraft("draft-1","account-1","","","",attachmentUris=(1..20).map { "content://documents/$it" },forwardSource=source)
        assertThrows(IllegalArgumentException::class.java) { ErmisDraftPolicy.validate(draft) }
        ErmisDraftPolicy.validate(draft.copy(attachmentUris=draft.attachmentUris.take(19)))
    }
}
