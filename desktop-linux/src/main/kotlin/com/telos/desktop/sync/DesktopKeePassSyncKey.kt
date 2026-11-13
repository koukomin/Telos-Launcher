package com.telos.desktop.sync

import app.keemobile.kotpass.cryptography.EncryptedValue
import app.keemobile.kotpass.database.Credentials
import app.keemobile.kotpass.database.KeePassDatabase
import app.keemobile.kotpass.database.decode
import app.keemobile.kotpass.models.Entry
import app.keemobile.kotpass.models.EntryValue
import app.keemobile.kotpass.models.Group
import java.io.File

object DesktopKeePassSyncKey {
    fun readSyncSecret(
        kdbx: File,
        masterPassword: CharArray,
        entryTitle: String,
    ): CharArray? {
        if (!kdbx.isFile) return null
        return runCatching {
            kdbx.inputStream().use { stream ->
                val db = KeePassDatabase.decode(
                    stream,
                    Credentials.from(EncryptedValue.fromString(String(masterPassword))),
                )
                val entry = findEntry(db.content.group, entryTitle) ?: return null
                fieldText(entry.fields.password)?.toCharArray()
                    ?: fieldText(entry.fields.notes)?.toCharArray()
            }
        }.getOrNull()
    }

    private fun findEntry(group: Group, title: String): Entry? {
        group.entries.firstOrNull { fieldText(it.fields.title).equals(title, ignoreCase = true) }
            ?.let { return it }
        for (child in group.groups) {
            findEntry(child, title)?.let { return it }
        }
        return null
    }

    private fun fieldText(value: EntryValue?): String? = when (value) {
        is EntryValue.Plain -> value.content.takeIf { it.isNotBlank() }
        is EntryValue.Encrypted -> value.content.takeIf { it.isNotBlank() }
        null -> null
    }
}
