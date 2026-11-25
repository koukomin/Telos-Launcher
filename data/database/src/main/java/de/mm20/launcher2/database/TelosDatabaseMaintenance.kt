package de.mm20.launcher2.database

import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Maintenance and optimization helper for Telos Launcher SQLite databases.
 * Executes SQLite PRAGMA optimize and WAL checkpointing to keep queries at 100% peak performance.
 */
object TelosDatabaseMaintenance {

    /**
     * Executes SQLite query planner optimization and WAL checkpointing.
     */
    suspend fun optimizeDatabase(db: SupportSQLiteDatabase) = withContext(Dispatchers.IO) {
        try {
            db.query("PRAGMA optimize;").close()
            db.query("PRAGMA wal_checkpoint(TRUNCATE);").close()
        } catch (e: Exception) {
            // Ignore non-fatal maintenance exceptions
        }
    }
}
