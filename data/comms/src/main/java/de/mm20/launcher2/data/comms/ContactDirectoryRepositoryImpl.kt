package de.mm20.launcher2.data.comms

import android.content.ContentProviderOperation
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import de.mm20.launcher2.comms.PhoneNumbers
import de.mm20.launcher2.comms.model.DialerContact
import de.mm20.launcher2.comms.repository.ContactDirectoryRepository
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Queries `ContactsContract.CommonDataKinds.Phone` directly for a flat (id, name, number) list -
 * deliberately not routed through `:data:contacts`' `ContactRepository`, whose `search()` only
 * returns non-empty results for a 2+ character query and is built around `SavableSearchable`
 * rendering, not a dialer's "every contact with a phone number" list.
 */
internal class ContactDirectoryRepositoryImpl(
    private val context: Context,
    private val permissionsManager: PermissionsManager,
) : ContactDirectoryRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeContacts(): Flow<List<DialerContact>> {
        return permissionsManager.hasPermission(PermissionGroup.Contacts).flatMapLatest { granted ->
            if (!granted) flowOf(emptyList()) else {
                // reloads whenever the address book changes (added, edited or deleted contacts)
                callbackFlow {
                    val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                        override fun onChange(selfChange: Boolean) {
                            trySend(Unit)
                        }
                    }
                    context.contentResolver.registerContentObserver(
                        ContactsContract.Contacts.CONTENT_URI,
                        true,
                        observer,
                    )
                    trySend(Unit)
                    awaitClose { context.contentResolver.unregisterContentObserver(observer) }
                }.conflate().map { queryContacts() }
            }
        }
    }

    override suspend fun containsNumber(number: String): Boolean {
        if (number.isBlank()) return false
        // a single lookup instead of loading the whole address book for every incoming call
        val found = withContext(Dispatchers.IO) {
            runCatching {
                val uri = android.net.Uri.withAppendedPath(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    android.net.Uri.encode(number),
                )
                context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)
                    ?.use { it.count > 0 }
            }.getOrNull()
        }
        if (found != null) return found
        return queryContacts().any { contact ->
            contact.phoneNumbers.any { PhoneNumbers.match(it, number) }
        }
    }

    override suspend fun findDuplicateGroups(): List<List<DialerContact>> = withContext(Dispatchers.IO) {
        val contacts = queryContacts()
        val buckets = LinkedHashMap<String, MutableList<DialerContact>>()
        for (contact in contacts) {
            val keys = contact.phoneNumbers.map { PhoneNumbers.digits(it) }.filter { it.length >= 7 }.toSet()
            for (key in keys) {
                buckets.getOrPut(key) { mutableListOf() }.add(contact)
            }
        }
        buckets.values
            .map { group -> group.distinctBy { it.id } }
            .filter { it.size > 1 }
            .distinctBy { it.map { c -> c.id }.sorted() }
    }

    override suspend fun deleteContact(id: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val uri = ContactsContract.RawContacts.CONTENT_URI
            val deleted = context.contentResolver.delete(
                uri,
                "${ContactsContract.RawContacts.CONTACT_ID}=?",
                arrayOf(id.toString()),
            )
            deleted > 0
        } catch (_: SecurityException) {
            false
        }
    }

    override suspend fun importVcf(content: String): Int = withContext(Dispatchers.IO) {
        val unfolded = content.replace(Regex("\r?\n[ \t]"), "")
        val cards = unfolded.split(Regex("(?i)BEGIN:VCARD"))
        var imported = 0
        for (card in cards) {
            if (card.isBlank()) continue
            var name = ""
            val phones = mutableListOf<String>()
            val emails = mutableListOf<String>()
            for (raw in card.split(Regex("\\r?\\n"))) {
                val line = raw.trim()
                val idx = line.indexOf(':')
                if (idx < 0) continue
                val key = line.substring(0, idx).uppercase()
                val value = line.substring(idx + 1).trim()
                when {
                    key.startsWith("FN") -> name = value
                    key == "N" && name.isBlank() ->
                        name = value.split(";").filter { it.isNotBlank() }.reversed().joinToString(" ")
                    key.startsWith("TEL") && value.isNotBlank() -> phones += value
                    key.startsWith("EMAIL") && value.isNotBlank() -> emails += value
                }
            }
            if (name.isBlank() && phones.isEmpty()) continue
            if (insertContact(name.ifBlank { phones.first() }, phones, emails)) imported++
        }
        imported
    }

    private fun insertContact(name: String, phones: List<String>, emails: List<String>): Boolean {
        return try {
            val ops = ArrayList<ContentProviderOperation>()
            ops += ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                .build()
            ops += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                .build()
            phones.distinct().forEach { phone ->
                ops += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                    .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                    .build()
            }
            emails.distinct().forEach { email ->
                ops += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, email)
                    .build()
            }
            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            true
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun queryContacts(): List<DialerContact> = withContext(Dispatchers.IO) {
        data class Acc(
            var name: String,
            var photoUri: String?,
            val phones: MutableList<String> = mutableListOf(),
            val emails: MutableList<String> = mutableListOf(),
            var starred: Boolean = false,
            var company: String? = null,
            var jobTitle: String? = null,
            var birthdayMillis: Long? = null,
        )
        val byId = LinkedHashMap<Long, Acc>()

        val phoneProjection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
            ContactsContract.CommonDataKinds.Phone.STARRED,
        )
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            phoneProjection,
            null,
            null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} ASC",
        )?.use { cursor ->
            val idCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
            val numberCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val photoCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
            val starredCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.STARRED)
            if (idCol < 0) return@withContext emptyList()

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: continue
                val number = cursor.getString(numberCol) ?: continue
                val photoUri = if (photoCol >= 0) cursor.getString(photoCol) else null
                val starred = starredCol >= 0 && cursor.getInt(starredCol) != 0
                val entry = byId.getOrPut(id) { Acc(name, photoUri, starred = starred) }
                if (number !in entry.phones) entry.phones += number
                if (entry.photoUri == null) entry.photoUri = photoUri
            }
        }

        val emailProjection = arrayOf(
            ContactsContract.CommonDataKinds.Email.CONTACT_ID,
            ContactsContract.CommonDataKinds.Email.ADDRESS,
        )
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            emailProjection,
            null,
            null,
            null,
        )?.use { cursor ->
            val idCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.CONTACT_ID)
            val addrCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
            if (idCol < 0 || addrCol < 0) return@use
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val address = cursor.getString(addrCol) ?: continue
                val entry = byId[id] ?: continue
                if (address !in entry.emails) entry.emails += address
            }
        }

        context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(
                ContactsContract.Data.CONTACT_ID,
                ContactsContract.CommonDataKinds.Organization.COMPANY,
                ContactsContract.CommonDataKinds.Organization.TITLE,
            ),
            "${ContactsContract.Data.MIMETYPE}=?",
            arrayOf(ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE),
            null,
        )?.use { cursor ->
            val idCol = cursor.getColumnIndex(ContactsContract.Data.CONTACT_ID)
            val companyCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Organization.COMPANY)
            val titleCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Organization.TITLE)
            if (idCol >= 0) {
                while (cursor.moveToNext()) {
                    val entry = byId[cursor.getLong(idCol)] ?: continue
                    if (entry.company == null) entry.company = cursor.getString(companyCol)
                    if (entry.jobTitle == null) entry.jobTitle = cursor.getString(titleCol)
                }
            }
        }

        context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(
                ContactsContract.Data.CONTACT_ID,
                ContactsContract.CommonDataKinds.Event.START_DATE,
                ContactsContract.CommonDataKinds.Event.TYPE,
            ),
            "${ContactsContract.Data.MIMETYPE}=?",
            arrayOf(ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE),
            null,
        )?.use { cursor ->
            val idCol = cursor.getColumnIndex(ContactsContract.Data.CONTACT_ID)
            val dateCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Event.START_DATE)
            val typeCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Event.TYPE)
            if (idCol >= 0 && dateCol >= 0) {
                while (cursor.moveToNext()) {
                    val type = if (typeCol >= 0) cursor.getInt(typeCol) else 0
                    if (type != ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY) continue
                    val entry = byId[cursor.getLong(idCol)] ?: continue
                    val raw = cursor.getString(dateCol) ?: continue
                    entry.birthdayMillis = parseContactDate(raw)
                }
            }
        }

        byId.map { (id, data) ->
            DialerContact(
                id = id,
                displayName = data.name,
                phoneNumbers = data.phones,
                photoUri = data.photoUri,
                emails = data.emails,
                starred = data.starred,
                company = data.company,
                jobTitle = data.jobTitle,
                birthdayMillis = data.birthdayMillis,
            )
        }
    }

    private fun parseContactDate(raw: String): Long? {
        return try {
            val normalized = raw.removePrefix("--")
            val parts = normalized.split("-").mapNotNull { it.toIntOrNull() }
            if (parts.size < 2) return null
            val cal = java.util.Calendar.getInstance()
            if (parts.size >= 3) {
                cal.set(parts[0], parts[1] - 1, parts[2], 0, 0, 0)
            } else {
                cal.set(cal.get(java.util.Calendar.YEAR), parts[0] - 1, parts[1], 0, 0, 0)
            }
            cal.set(java.util.Calendar.MILLISECOND, 0)
            cal.timeInMillis
        } catch (_: Exception) {
            null
        }
    }
}
