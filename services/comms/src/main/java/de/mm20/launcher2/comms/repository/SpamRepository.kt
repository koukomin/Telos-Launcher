package de.mm20.launcher2.comms.repository

interface SpamRepository {
    suspend fun isNumberBlocked(number: String): Boolean
}
