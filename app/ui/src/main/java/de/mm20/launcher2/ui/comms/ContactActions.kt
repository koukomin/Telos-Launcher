package de.mm20.launcher2.ui.comms

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import de.mm20.launcher2.ktx.tryStartActivity
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One entry in the contact's data that messenger apps add so that calls can start from the contact */
internal data class DataAction(val uri: Uri, val mimeType: String) {
    fun intent(): Intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mimeType)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

internal object ContactActions {

    fun edit(context: Context, id: Long) {
        if (!context.tryStartActivity(Intent(Intent.ACTION_EDIT,
            ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id)))) {
            android.widget.Toast.makeText(context, de.mm20.launcher2.ui.R.string.au_phonea_action_failed, android.widget.Toast.LENGTH_LONG).show()
        }
    }

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

    /** Stores the contact's own ringtone (null = silent). Returns false when the contact could not be changed. */
    suspend fun setRingtone(context: Context, contactId: Long, ringtone: Uri?): Boolean {
        if (contactId < 0) return false
        return withContext(Dispatchers.IO) {
            runCatching {
                val values = android.content.ContentValues().apply {
                    put(ContactsContract.Contacts.CUSTOM_RINGTONE, ringtone?.toString())
                }
                context.contentResolver.update(
                    ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId),
                    values,
                    null,
                    null,
                ) > 0
            }.getOrDefault(false)
        }
    }
}

@androidx.compose.runtime.Composable
internal fun DeleteContactDialog(contact: de.mm20.launcher2.comms.model.DialerContact, onDismiss: () -> Unit) {
    val repo: de.mm20.launcher2.comms.repository.ContactDirectoryRepository = org.koin.compose.koinInject()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val deleting = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { if (!deleting.value) onDismiss() },
        title = { androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(de.mm20.launcher2.ui.R.string.hc_delete)) },
        text = { androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(de.mm20.launcher2.ui.R.string.contacts_delete_confirm, contact.displayName)) },
        confirmButton = {
            androidx.compose.material3.TextButton(enabled = !deleting.value, onClick = {
                deleting.value = true
                scope.launch {
                    val deleted = try {
                        repo.deleteContact(contact.id)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        false
                    }
                    deleting.value = false
                    if (!deleted) {
                        android.widget.Toast.makeText(context, de.mm20.launcher2.ui.R.string.au_phonea_action_failed, android.widget.Toast.LENGTH_LONG).show()
                    }
                    onDismiss()
                }
            }) { androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(de.mm20.launcher2.ui.R.string.hc_delete)) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(enabled = !deleting.value, onClick = onDismiss) {
                androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(android.R.string.cancel))
            }
        },
    )
}
