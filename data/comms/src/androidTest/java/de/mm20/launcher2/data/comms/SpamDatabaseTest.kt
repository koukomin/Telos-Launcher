package de.mm20.launcher2.data.comms

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpamDatabaseTest {
    @Test fun encryptedBlockListOpensAndReopensWithThePersistedKey() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val number = "+306900000001"
        val db = SpamDatabase.getDatabase(context)
        db.blockedNumberDao().insert(BlockedNumberEntity(number))
        assertEquals(number, db.blockedNumberDao().getBlockedNumber(number)?.number)
        db.close()
        val file = context.getDatabasePath("spam_secure.db")
        assertTrue(file.exists())
        assertFalse(file.inputStream().use { String(ByteArray(16).also { bytes -> it.read(bytes) }, Charsets.US_ASCII) }.startsWith("SQLite format 3"))
        val reopened = Room.databaseBuilder(context, SpamDatabase::class.java, "spam_secure.db")
            .openHelperFactory(net.zetetic.database.sqlcipher.SupportOpenHelperFactory(SpamDbKey.get(context))).build()
        try { assertEquals(number, reopened.blockedNumberDao().getBlockedNumber(number)?.number) }
        finally { reopened.close() }
    }
}
