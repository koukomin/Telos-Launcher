package de.mm20.launcher2.comms.ermis

import javax.mail.Session
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import java.util.Properties
import org.junit.Assert.*
import org.junit.Test

class ErmisHtmlTest {
    @Test fun preservesPassiveTablesAndLinksButRemovesScriptsFormsAndTracking() {
        val result = ErmisHtml.sanitize("""<table><tr><td colspan="2"><b>Invoice</b></td></tr></table><script>alert(1)</script><img src="https://tracker.example/pixel"><form action="https://steal.example"><input name="secret"></form><a href="javascript:alert(1)" onclick="alert(1)">bad</a><a href="https://example.org">safe</a><style>@import url(https://tracker.example);</style>""")
        assertTrue(result.contains("<table>")); assertTrue(result.contains("<b>Invoice</b>"))
        assertTrue(result.contains("https://example.org"))
        for (forbidden in listOf("<script", "<img", "<form", "<input", "javascript:", "onclick", "<style", "tracker.example", "steal.example")) assertFalse(forbidden, result.contains(forbidden))
    }
    @Test fun alternativeKeepsOnePlainBodyAndSafeHtmlSeparately() {
        val part = MimeMultipart("alternative")
        part.addBodyPart(MimeBodyPart().apply { setText("Plain version", "UTF-8") })
        part.addBodyPart(MimeBodyPart().apply { setContent("<p><b>Rich version</b></p>", "text/html; charset=UTF-8") })
        val original = MimeMessage(Session.getInstance(Properties())).apply { setContent(part); saveChanges() }
        val wire = java.io.ByteArrayOutputStream().also { original.writeTo(it) }.toByteArray()
        val result = ErmisMime.read(MimeMessage(Session.getInstance(Properties()), wire.inputStream()))
        assertEquals("Plain version", result.body)
        assertTrue(result.html.contains("<b>Rich version</b>"))
        assertTrue(result.attachments.isEmpty())
    }
    @Test fun oversizedHtmlFallsBackRatherThanGrowingCache() {
        assertEquals("", ErmisHtml.sanitize("x".repeat(ErmisHtml.MAX_HTML + 1)))
    }
}
