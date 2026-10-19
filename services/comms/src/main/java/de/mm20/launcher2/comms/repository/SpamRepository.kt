package de.mm20.launcher2.comms.repository

interface SpamRepository {
    suspend fun isNumberBlocked(number: String): Boolean
    suspend fun isPersonallyBlocked(number: String): Boolean = isNumberBlocked(number)
    suspend fun isSystemBlocked(number: String): Boolean = false
    /** Updates both stores; throws on partial success so UI can report missing Android access. */
    suspend fun setBlocked(number: String, blocked: Boolean)
    suspend fun getAllBlocked(): List<String>
}

class SystemBlockUpdateException(cause: Throwable) : Exception("Personal list updated; Android block list was not updated", cause)
