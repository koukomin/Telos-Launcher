package de.mm20.launcher2.data.comms

import android.content.Context
import android.provider.ContactsContract
import de.mm20.launcher2.comms.model.DialerContact
import de.mm20.launcher2.comms.repository.ContactDirectoryRepository
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
            if (!granted) flowOf(emptyList()) else flowOf(queryContacts())
        }
    }

    private suspend fun queryContacts(): List<DialerContact> = withContext(Dispatchers.IO) {
        val numbersById = LinkedHashMap<Long, Triple<String, String?, MutableList<String>>>()

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI
        )
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} ASC",
        )?.use { cursor ->
            val idCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
            val numberCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val photoCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI)
            if (idCol < 0) return@withContext emptyList()

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: continue
                val number = cursor.getString(numberCol) ?: continue

                val photoUri = if (photoCol >= 0) cursor.getString(photoCol) else null
                val entry = numbersById.getOrPut(id) { Triple(name, photoUri, mutableListOf()) }
                if (number !in entry.third) entry.third += number
            }
        }

        numbersById.map { (id, data) ->
            DialerContact(id = id, displayName = data.first, phoneNumbers = data.third, photoUri = data.second)
        }
    }
}
