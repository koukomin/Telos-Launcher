package de.mm20.launcher2.keepasstl

import app.keemobile.kotpass.cryptography.EncryptedValue
import app.keemobile.kotpass.database.KeePassDatabase
import app.keemobile.kotpass.database.decode
import app.keemobile.kotpass.models.Entry
import app.keemobile.kotpass.models.EntryValue
import java.io.InputStream
import okio.buffer
import okio.source

object KeePassDatabaseManager {

    /**
     * Decodes a KDBX v3 or v4 database.
     */
    fun openDatabase(stream: InputStream, password: String): KeePassDatabase? {
        return runCatching {
            KeePassDatabase.decode(stream, app.keemobile.kotpass.database.Credentials.from(EncryptedValue.fromString(password)))
        }.getOrNull()
    }

    fun extractCredentials(entry: Entry): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for ((key, value) in entry.fields) {
            when (value) {
                is EntryValue.Plain -> result[key] = value.content
                is EntryValue.Encrypted -> result[key] = value.content
            }
        }
        return result
    }
}
