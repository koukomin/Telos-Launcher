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
        // the number may be stored in another notation (+49 / 0049 / national)
        return dao.getAllNumbers().any { PhoneNumbers.match(it, number) }
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
