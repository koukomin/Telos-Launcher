package de.mm20.launcher2.data.comms

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.sqlcipher.database.SupportFactory

@Database(
    entities = [SecretContact::class, SecretCallLog::class, SecretSms::class],
    version = 1,
    exportSchema = false
)
abstract class TelosVaultDatabase : RoomDatabase() {

    companion object {
        @Volatile
        private var INSTANCE: TelosVaultDatabase? = null

        fun getDatabase(context: Context, passphrase: ByteArray): TelosVaultDatabase {
            return INSTANCE ?: synchronized(this) {
                val factory = SupportFactory(passphrase)
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TelosVaultDatabase::class.java,
                    "telos_vault.db"
                )
                .openHelperFactory(factory)
                .build()
                INSTANCE = instance
                instance
            }
        }
        
        fun closeDatabase() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}
