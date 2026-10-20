package de.mm20.launcher2.comms.ermis

import java.util.Locale
import javax.mail.internet.InternetAddress

data class ErmisReplyRecipients(val to: String, val cc: String)

/** Reply headers are untrusted input; never include Bcc or any of the user's sending identities. */
object ErmisReplies {
    private fun parse(values: List<String>): List<InternetAddress> = values.flatMap { value ->
        require(value.length <= 16_000 && !value.contains('\r') && !value.contains('\n'))
        InternetAddress.parse(value, true).toList().also { addresses ->
            require(addresses.size <= 100)
            addresses.forEach { require(!it.isGroup); it.validate() }
        }
    }.also { require(it.size <= 100) }
    fun recipients(message: ErmisMessage, ownEmails: List<String>, all: Boolean): ErmisReplyRecipients {
        val own = ownEmails.map { it.trim().lowercase(Locale.ROOT) }.toSet()
        val seen = own.toMutableSet()
        fun unique(values: List<InternetAddress>) = values.filter { seen.add(it.address.lowercase(Locale.ROOT)) }
        val sender = parse(listOf(message.replyTo.ifBlank { message.from }))
        val to = unique(sender + if (all) parse(message.to) else emptyList()).let {
            if (it.isEmpty() && !all) unique(parse(message.to)) else it
        }
        val cc = if (all) unique(parse(message.cc)) else emptyList()
        return ErmisReplyRecipients(to.joinToString(", ") { it.toUnicodeString() }, cc.joinToString(", ") { it.toUnicodeString() })
    }
}
