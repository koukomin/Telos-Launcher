package de.mm20.launcher2.comms.repository

interface SpamRepository {
    suspend fun isNumberBlocked(number: String): Boolean
    suspend fun setBlocked(number: String, blocked: Boolean)
    suspend fun getAllBlocked(): List<String>
}
