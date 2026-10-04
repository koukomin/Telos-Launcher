package de.mm20.launcher2.data.comms

import android.content.Context
import de.mm20.launcher2.comms.repository.SpamRepository

class SpamRepositoryImpl(private val context: Context) : SpamRepository {
    override suspend fun isNumberBlocked(number: String): Boolean {
        val db = SpamDatabase.getDatabase(context)
        return db.blockedNumberDao().getBlockedNumber(number) != null
    }
}
