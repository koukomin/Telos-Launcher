package de.mm20.launcher2.comms.remote

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract

object DeviceContactsResolver {
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun lookup(context: Context?, number: String): Pair<String?, String?> {
        val ctx = context ?: appContext ?: return null to null
        if (number.isBlank()) return null to null
        try {
            val key = de.mm20.launcher2.ktx.PhoneNumberKey.of(number)
            val candidates = linkedSetOf(number, key).apply {
                if (key.startsWith("+30") && key.length == 13) add(key.drop(3))
            }.filter(String::isNotBlank)
            for (candidate in candidates) {
                val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(candidate))
                val projection = arrayOf(
                    ContactsContract.PhoneLookup.DISPLAY_NAME,
                    ContactsContract.PhoneLookup.PHOTO_URI
                )
                ctx.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                        val photoIdx = cursor.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_URI)
                        val name = if (nameIdx >= 0) cursor.getString(nameIdx) else null
                        val photo = if (photoIdx >= 0) cursor.getString(photoIdx) else null
                        return name to photo
                    }
                }
            }
        } catch (_: Exception) {}
        return null to null
    }
}
