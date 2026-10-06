package de.mm20.launcher2.data.comms

import android.content.Context
import de.mm20.launcher2.comms.repository.SpamRepository

class SpamRepositoryImpl(private val context: Context) : SpamRepository {
    override suspend fun isNumberBlocked(number: String): Boolean {
        val db = SpamDatabase.getDatabase(context)
        return db.blockedNumberDao().getBlockedNumber(number) != null
    }

    override suspend fun setBlocked(number: String, blocked: Boolean) {
        val db = SpamDatabase.getDatabase(context)
        if (blocked) {
            db.blockedNumberDao().insert(BlockedNumberEntity(number))
        } else {
            db.blockedNumberDao().delete(BlockedNumberEntity(number))
        }
    }

    override suspend fun getAllBlocked(): List<String> {
        return SpamDatabase.getDatabase(context).blockedNumberDao().getAllNumbers()
    }
}
