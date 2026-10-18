package de.mm20.launcher2.data.comms

import android.content.Context
import de.mm20.launcher2.comms.PhoneNumbers
import de.mm20.launcher2.comms.repository.SpamRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SpamRepositoryImpl(private val context: Context) : SpamRepository {
    // opening the encrypted database (and migrating the old one) is blocking work
    private suspend fun dao() = withContext(Dispatchers.IO) { SpamDatabase.getDatabase(context).blockedNumberDao() }

    override suspend fun isNumberBlocked(number: String): Boolean {
        val dao = dao()
        if (dao.getBlockedNumber(number) != null) return true
        val digits = number.filter { it.isDigit() || it == '+' }
        val all = dao.getAllNumbers()
        if (all.any { PhoneNumbers.match(it, number) }) return true
        for (rule in all) {
            val trimmed = rule.trim()
            if (trimmed.startsWith("re:") && trimmed.length > 3) {
                val ok = runCatching { Regex(trimmed.removePrefix("re:")).containsMatchIn(number) }.getOrDefault(false)
                if (ok) return true
            } else if (trimmed.endsWith("*") && trimmed.length > 1) {
                val prefix = trimmed.dropLast(1).filter { it.isDigit() || it == '+' }
                if (prefix.isNotEmpty() && digits.startsWith(prefix)) return true
            }
        }
        return false
    }

    override suspend fun setBlocked(number: String, blocked: Boolean) {
        val dao = dao()
        if (blocked) {
            dao.insert(BlockedNumberEntity(number))
        } else {
            // isNumberBlocked() also matches other notations, so unblocking removes those entries too
            dao.delete(BlockedNumberEntity(number))
            dao.getAllNumbers()
                .filter { it != number && PhoneNumbers.match(it, number) }
                .forEach { dao.delete(BlockedNumberEntity(it)) }
        }
    }

    override suspend fun getAllBlocked(): List<String> {
        return dao().getAllNumbers()
    }
}
