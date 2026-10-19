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
        // Use parsed wire headers: setContent alone leaves a standalone MimeBodyPart's
        // Content-Type at its default until updateHeaders/saveChanges runs.
        val wire = buildString {
            append("MIME-Version: 1.0\r\nContent-Type: multipart/mixed; boundary=ermis-boundary\r\n\r\n")
            repeat(101) { append("--ermis-boundary\r\nContent-Type: text/plain; charset=UTF-8\r\n\r\ntext\r\n") }
            append("--ermis-boundary--\r\n")
        }
        val message = MimeMessage(Session.getInstance(Properties()), wire.byteInputStream(Charsets.US_ASCII))
        assertThrows(IllegalArgumentException::class.java) { ErmisMime.read(message) }
    }
    @Test fun boundsRawMultipartSourceBeforeLibraryAllocation() {
        var supplied = 0
        val source = object : java.io.InputStream() {
            private val prefix = "--budget-boundary\r\nContent-Type: text/plain\r\n\r\n".byteInputStream()
            override fun read(): Int {
                supplied++
                val first = prefix.read()
                return if (first >= 0) first else 'x'.code // Unbounded remote stream; no large fixture allocation.
            }
        }
        val part = object : MimeBodyPart() {
            override fun getContentType() = "multipart/mixed; boundary=budget-boundary"
            override fun getInputStream(): java.io.InputStream = source
            override fun getContent(): Any = throw AssertionError("Unbounded Part.content must not be used")
        }
        val error = assertThrows(Exception::class.java) { ErmisMime.read(part, rawParseLimit = 1024) }
        assertTrue(generateSequence(error as Throwable) { it.cause }.any { it.message?.contains("MIME raw parsing limit") == true })
        assertTrue("Parser must stop reading at the budget", supplied <= 1025)
    }

}
