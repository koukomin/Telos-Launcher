package de.mm20.launcher2.comms.ermis

import java.util.Properties
import javax.mail.Session
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import org.junit.Assert.*
import org.junit.Test

class ErmisMimeTest {
    @Test fun alternativeSelectionUsesSameAttachmentIndexForDisplayAndDownload() {
        val alternative = MimeMultipart("alternative").apply {
            addBodyPart(MimeBodyPart().apply { setContent("<b>HTML</b>", "text/html") })
            addBodyPart(MimeBodyPart().apply { setText("plain body") })
        }
        val mixed = MimeMultipart().apply {
            addBodyPart(MimeBodyPart().apply { setContent(alternative) })
            addBodyPart(MimeBodyPart().apply { setText("first bytes"); fileName = "first.txt" })
            addBodyPart(MimeBodyPart().apply { setText("second bytes"); fileName = "second.txt" })
        }
        val message = MimeMessage(Session.getInstance(Properties())).apply { setContent(mixed); saveChanges() }
        val result = ErmisMime.read(message)
        assertEquals("plain body", result.body)
        assertEquals(listOf("first.txt", "second.txt"), result.attachments.map { it.fileName })
        assertEquals("second bytes", result.attachments[1].inputStream.bufferedReader().use { it.readText() })
    }
    @Test fun emptyAlternativeDoesNotCrash() {
        val part = MimeBodyPart().apply { setContent(MimeMultipart("alternative")) }
        assertEquals("", ErmisMime.read(part).body)
    }
    @Test fun rejectsExcessiveMimeParts() {
        val multi = MimeMultipart().apply { repeat(101) { addBodyPart(MimeBodyPart().apply { setText("text") }) } }
        val part = MimeBodyPart().apply { setContent(multi) }
        assertThrows(IllegalArgumentException::class.java) { ErmisMime.read(part) }
    }
}
