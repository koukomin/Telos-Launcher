package de.mm20.launcher2.data.comms

import android.content.Context
import de.mm20.launcher2.comms.PhoneNumbers
import de.mm20.launcher2.comms.repository.SpamRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SpamRepositoryImpl(private val context: Context) : SpamRepository {
    // opening the encrypted database (and migrating the old one) is blocking work
    private suspend fun dao() = withContext(Dispatchers.IO) { SpamDatabase.getDatabase(context).blockedNumberDao() }

    override suspend fun isNumberBlocked(number: String): Boolean =
        isPersonallyBlocked(number) || isSystemBlocked(number)

    override suspend fun isSystemBlocked(number: String): Boolean = withContext(Dispatchers.IO) {
        if (!android.provider.BlockedNumberContract.canCurrentUserBlockNumbers(context)) false
        else try { android.provider.BlockedNumberContract.isBlocked(context, number) }
        catch (_: SecurityException) { false }
    }

    override suspend fun isPersonallyBlocked(number: String): Boolean {
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
        // A personal-list update can succeed even if Android refuses its system block-list write.
        // Propagate that partial failure; callers must not advertise that both stores were updated.
        try {
        withContext(Dispatchers.IO) {
            check(android.provider.BlockedNumberContract.canCurrentUserBlockNumbers(context)) {
                "Personal list saved; Android system block-list access requires the default phone or SMS role"
            }
            if (blocked) {
                val values = android.content.ContentValues().apply {
                    put(android.provider.BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number)
                }
                checkNotNull(context.contentResolver.insert(android.provider.BlockedNumberContract.BlockedNumbers.CONTENT_URI, values))
            } else android.provider.BlockedNumberContract.unblock(context, number)
            check(android.provider.BlockedNumberContract.isBlocked(context, number) == blocked) {
                "Personal list saved; Android system block-list update could not be verified"
            }
        }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (error: Exception) { throw de.mm20.launcher2.comms.repository.SystemBlockUpdateException(error) }

    }

    override suspend fun getAllBlocked(): List<String> {
        return dao().getAllNumbers()
    }
}
