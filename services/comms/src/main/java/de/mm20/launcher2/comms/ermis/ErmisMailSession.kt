package de.mm20.launcher2.comms.ermis

import com.sun.mail.imap.IMAPSSLStore
import com.sun.mail.smtp.SMTPSSLTransport
import com.sun.mail.smtp.SMTPTransport
import java.util.Properties
import javax.mail.Session
import javax.mail.Store
import javax.mail.Transport

/** Explicit providers survive shrinking without relying on META-INF provider class names. */
internal object ErmisMailSession {
    fun create(oauth: Boolean, tls13: Boolean): Session = Session.getInstance(Properties().apply {
        for (protocol in listOf("imaps", "smtp", "smtps")) {
            if (oauth) {
                setProperty("mail.$protocol.auth.mechanisms", "XOAUTH2")
                setProperty("mail.$protocol.auth.login.disable", "true")
                setProperty("mail.$protocol.auth.plain.disable", "true")
            }
            setProperty("mail.$protocol.connectiontimeout", "15000")
            setProperty("mail.$protocol.timeout", "20000")
            setProperty("mail.$protocol.writetimeout", "20000")
            setProperty("mail.$protocol.ssl.checkserveridentity", "true")
            setProperty("mail.$protocol.ssl.protocols", if (tls13) "TLSv1.2 TLSv1.3" else "TLSv1.2")
        }
        setProperty("mail.smtp.starttls.enable", "true")
        setProperty("mail.smtp.starttls.required", "true")
        setProperty("mail.smtp.auth", "true")
        setProperty("mail.smtps.auth", "true")
        setProperty("mail.imaps.peek", "true")
    })

    fun store(session: Session): Store = IMAPSSLStore(session, null)
    fun transport(session: Session, startTls: Boolean): Transport =
        if (startTls) SMTPTransport(session, null) else SMTPSSLTransport(session, null)
}
