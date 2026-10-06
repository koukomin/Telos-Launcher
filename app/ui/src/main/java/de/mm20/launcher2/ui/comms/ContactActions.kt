package de.mm20.launcher2.ui.comms

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract

/** One entry in the contact's data that messenger apps add so that calls can start from the contact */
internal data class DataAction(val uri: Uri, val mimeType: String) {
    fun intent(): Intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mimeType)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

internal object ContactActions {

    /** Mime types the messenger apps use for their actions in the contacts database */
    val whatsAppCall = listOf("vnd.android.cursor.item/vnd.com.whatsapp.voip.call")
    val whatsAppVideo = listOf("vnd.android.cursor.item/vnd.com.whatsapp.video.call")
    val telegramCall = listOf("vnd.android.cursor.item/vnd.org.telegram.messenger.android.call")
    val signalCall = listOf("vnd.android.cursor.item/vnd.org.thoughtcrime.securesms.call")
    val viberCall = listOf("vnd.android.cursor.item/vnd.com.viber.voip.viber_number_call")

    /** The first data row of the contact with one of [mimeTypes], or null when the app has none */
    fun find(context: Context, contactId: Long, mimeTypes: List<String>): DataAction? {
        if (mimeTypes.isEmpty()) return null
        val placeholders = mimeTypes.joinToString(",") { "?" }
        return runCatching {
            context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.Data._ID, ContactsContract.Data.MIMETYPE),
                "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} IN ($placeholders)",
                arrayOf(contactId.toString()) + mimeTypes.toTypedArray(),
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    DataAction(
                        ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, cursor.getLong(0)),
                        cursor.getString(1),
                    )
                } else null
            }
        }.getOrNull()
    }

    fun setRingtone(context: Context, contactId: Long, ringtone: Uri?) {
        runCatching {
            val values = android.content.ContentValues().apply {
                put(ContactsContract.Contacts.CUSTOM_RINGTONE, ringtone?.toString())
            }
            context.contentResolver.update(
                ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId),
                values,
                null,
                null,
            )
        }
    }
}
