package de.mm20.launcher2.comms.ermis

import android.net.Uri
import kotlinx.serialization.Serializable

@Serializable
data class ErmisAccount(val id: String, val name: String, val email: String, val imapHost: String,
    val imapPort: Int = 993, val smtpHost: String, val smtpPort: Int = 465,
    val smtpStartTls: Boolean = false, val username: String = email,
    val googleAccount: String? = null)
@Serializable
data class ErmisAttachment(val index: Int, val name: String, val mimeType: String, val size: Int)
@Serializable
data class ErmisMessage(val accountId: String, val uid: Long, val messageId: String = "",
    val from: String, val subject: String, val preview: String = "", val body: String = "",
    val date: Long, val read: Boolean = false, val starred: Boolean = false,
    val attachments: List<ErmisAttachment> = emptyList(), val note: String = "",
    val inReplyTo: String = "", val references: List<String> = emptyList())
data class ErmisDraft(val accountId: String, val to: String, val subject: String, val body: String,
    val attachments: List<Uri> = emptyList(), val inReplyTo: String? = null,
    val references: List<String> = emptyList(), val cc: String = "", val bcc: String = "")

@Serializable
data class ErmisSavedDraft(val id: String, val accountId: String, val to: String,
    val subject: String, val body: String, val attachmentUris: List<String> = emptyList(),
    val inReplyTo: String? = null, val references: List<String> = emptyList(),
    val cc: String = "", val bcc: String = "", val updatedAt: Long = 0L) {
    fun toDraft(): ErmisDraft = ErmisDraft(accountId, to, subject, body, attachmentUris.map(Uri::parse),
        inReplyTo, references, cc, bcc)
}

data class ErmisState(val accounts: List<ErmisAccount> = emptyList(), val messages: List<ErmisMessage> = emptyList(),
    val loading: Boolean = false, val error: String? = null, val drafts: List<ErmisSavedDraft> = emptyList())

object ErmisValidation {
    private val identifier = Regex("[A-Za-z0-9_-]{1,80}")
    private val hostname = Regex("[A-Za-z0-9.:-]+")
    fun validate(account: ErmisAccount) {
        require(account.id.matches(identifier)) { "Invalid account identifier" }
        require(account.email.contains('@') && !account.email.contains('\n') && !account.email.contains('\r')) { "Invalid email address" }
        for (host in listOf(account.imapHost, account.smtpHost)) {
            require(host.isNotBlank() && host.length <= 253 && host.matches(hostname)) { "Enter a server hostname" }
        }
        require(account.imapPort in 1..65535 && account.smtpPort in 1..65535) { "Invalid server port" }
        require(account.username.isNotBlank()) { "Username is required" }
        if (account.googleAccount != null) {
            require(account.googleAccount == account.email && account.username == account.email &&
                account.imapHost.equals("imap.gmail.com", true) && account.smtpHost.equals("smtp.gmail.com", true)) { "Invalid Google mail configuration" }
        }
    }
}
