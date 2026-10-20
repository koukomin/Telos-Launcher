package de.mm20.launcher2.comms.ermis

import java.util.Locale

/** IMAP UIDs and UIDVALIDITY are scoped to one account and mailbox, never to an account alone. */
object ErmisMailboxPolicy {
    const val SERVER_SEARCH_WINDOW = 5000
    const val SERVER_SEARCH_RESULTS = 100
    fun folder(value: String): String {
        require(value.isNotEmpty() && value.length <= 1024 && value.none { it == '\r' || it == '\n' || it == '\u0000' })
        return if (value.equals("INBOX", true)) "INBOX" else value
    }
    fun customFolder(value: String): String {
        val canonical = folder(value.trim())
        require(canonical != "INBOX" && canonical.none { it.isISOControl() || it == '*' || it == '%' })
        return canonical
    }
    fun validityKey(accountId: String, mailbox: String): String {
        require(accountId.isNotEmpty() && !accountId.contains('|'))
        val canonical = folder(mailbox)
        // Keep existing INBOX UIDVALIDITY entries readable after the schema upgrade.
        return if (canonical == "INBOX") accountId else "$accountId|$canonical"
    }
    fun matches(message: ErmisMessage, accountId: String, mailbox: String, uid: Long? = null): Boolean =
        message.accountId == accountId && folder(message.folder) == folder(mailbox) && (uid == null || message.uid == uid)
    fun role(fullName: String, name: String, attributes: List<String>): ErmisFolderRole {
        if (fullName.equals("INBOX", true)) return ErmisFolderRole.INBOX
        val roles = mapOf("\\trash" to ErmisFolderRole.TRASH, "\\junk" to ErmisFolderRole.SPAM,
            "\\sent" to ErmisFolderRole.SENT, "\\drafts" to ErmisFolderRole.DRAFTS,
            "\\archive" to ErmisFolderRole.ARCHIVE, "\\all" to ErmisFolderRole.ALL)
        val flags = attributes.map { it.lowercase(Locale.ROOT) }.toSet()
        roles.entries.firstOrNull { it.key in flags }?.let { return it.value }
        return when (name.lowercase(Locale.ROOT)) {
            "sent", "sent mail", "sent items" -> ErmisFolderRole.SENT
            "drafts" -> ErmisFolderRole.DRAFTS
            "trash", "deleted items", "deleted messages" -> ErmisFolderRole.TRASH
            "spam", "junk", "junk email", "junk e-mail" -> ErmisFolderRole.SPAM
            "archive", "archives" -> ErmisFolderRole.ARCHIVE
            "all mail" -> ErmisFolderRole.ALL
            else -> ErmisFolderRole.OTHER
        }
    }
}

/** The destructive half of fallback MOVE cannot run before the server confirms COPY. */
internal object ErmisMovePolicy {
    fun execute(supportsMove: Boolean, supportsUidExpunge: Boolean,
        move: () -> Unit, copy: () -> Unit, markDeleted: () -> Unit, expungeSelected: () -> Unit) {
        if (supportsMove) { move(); return }
        copy() // A failed/uncertain COPY leaves source flags untouched.
        markDeleted()
        if (supportsUidExpunge) expungeSelected()
    }
}
