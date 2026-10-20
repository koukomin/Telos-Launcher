package de.mm20.launcher2.comms.ermis

import javax.mail.Message
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

internal data class ErmisRecipients(val to: Array<InternetAddress>, val cc: Array<InternetAddress>, val bcc: Array<InternetAddress>) {
    val envelope: Array<InternetAddress> get() = to + cc + bcc
    fun apply(message: MimeMessage) {
        message.setRecipients(Message.RecipientType.TO, to)
        message.setRecipients(Message.RecipientType.CC, cc)
        // Bcc belongs in the SMTP envelope, never in the transmitted MIME headers.
        message.removeHeader("Bcc")
    }
    companion object {
        fun parse(to: String, cc: String, bcc: String): ErmisRecipients {
            fun addresses(value: String): Array<InternetAddress> {
                require(value.length <= 16_000 && !value.contains('\r') && !value.contains('\n'))
                if (value.isBlank()) return emptyArray()
                return InternetAddress.parse(value, true).also { list ->
                    require(list.size <= 100)
                    list.forEach { require(!it.isGroup); it.validate() }
                }
            }
            return ErmisRecipients(addresses(to), addresses(cc), addresses(bcc)).also {
                require(it.envelope.size in 1..100)
            }
        }
        fun validateHeaders(subject: String, inReplyTo: String?, references: List<String>) {
            require(subject.length <= 16_000 && !subject.contains('\r') && !subject.contains('\n'))
            require(references.size <= 100)
            (references + listOfNotNull(inReplyTo)).forEach {
                require(it.length <= 998 && !it.contains('\r') && !it.contains('\n'))
            }
        }
    }
}
