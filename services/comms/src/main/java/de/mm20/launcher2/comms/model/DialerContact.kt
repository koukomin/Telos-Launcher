package de.mm20.launcher2.comms.model

data class DialerContact(
    val id: Long,
    val displayName: String,
    val phoneNumbers: List<String>,
    val photoUri: String? = null,
    val emails: List<String> = emptyList(),
    val starred: Boolean = false,
    val company: String? = null,
    val jobTitle: String? = null,
    val birthdayMillis: Long? = null,
    val accounts: List<ContactAccount> = emptyList(),
)

/** A raw-contact account; null fields identify contacts saved only on the device. */
data class ContactAccount(val name: String?, val type: String?) {
    val key: String get() = "${type.orEmpty().length}:${type.orEmpty()}${name.orEmpty()}"
}

/** null means all accounts; an empty selection deliberately means no contacts. */
fun DialerContact.isInAccounts(selected: Set<String>?): Boolean =
    selected == null || accounts.any { it.key in selected }
