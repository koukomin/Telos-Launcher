package de.mm20.launcher2.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration_34_35 : Migration(34, 35) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `StoreItem` (
                `id` TEXT NOT NULL,
                `packageName` TEXT NOT NULL,
                `displayName` TEXT NOT NULL,
                `sourceType` TEXT NOT NULL,
                `sourceJson` TEXT NOT NULL,
                `installedVersionCode` INTEGER,
                `latestVersionCode` INTEGER,
                `latestVersion` TEXT,
                `latestDownloadUrl` TEXT,
                `latestChangelog` TEXT,
                `latestSize` INTEGER,
                `latestPublishedAt` INTEGER,
                `lastCheckedAt` INTEGER,
                PRIMARY KEY(`id`)
            );
            """.trimIndent(),
        )
    }
}
