package de.mm20.launcher2.comms.sms

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

/**
 * Sends and fetches multimedia messages through the phone's own messaging service: Telos writes
 * or reads a file, the system does the network part with the mobile data of the carrier.
 */
object MmsTransport {
    private const val TAG = "MmsTransport"
    private const val PHONE_PACKAGE = "com.android.phone"

    @Suppress("DEPRECATION")
    private fun manager(context: Context, subId: Int): SmsManager {
        val default = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(SmsManager::class.java) else SmsManager.getDefault()
        if (subId < 0) return default
        return runCatching { SmsManager.getSmsManagerForSubscriptionId(subId) }.getOrDefault(default)
    }

    private fun shareWithSystem(context: Context, file: File): Uri {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        // the system service that does the transfer has to read and write this file
        context.grantUriPermission(PHONE_PACKAGE, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        return uri
    }

    private fun resultIntent(context: Context, receiver: Class<*>, code: Int, extras: Intent.() -> Unit): PendingIntent {
        val intent = Intent(context, receiver).apply(extras)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getBroadcast(context, code, intent, flags)
    }

    private fun dir(context: Context) = File(context.cacheDir, "mms").apply { mkdirs() }

    /** Starts fetching a message announced by [notification]; [MmsDownloadedReceiver] gets the result. */
    fun download(context: Context, notification: MmsNotification, subId: Int) {
        runCatching {
            val file = File(dir(context), "in_${System.currentTimeMillis()}.dat")
            file.createNewFile()
            val uri = shareWithSystem(context, file)
            val done = resultIntent(context, MmsDownloadedReceiver::class.java, file.name.hashCode()) {
                putExtra("file", file.absolutePath)
                putExtra("tid", notification.transactionId)
                putExtra("from", notification.from)
            }
            manager(context, subId).downloadMultimediaMessage(context, notification.location, uri, null, done)
        }.onFailure { Log.w(TAG, "Could not start the download", it) }
    }

    /** Hands a finished message to the system for sending. [rowUri] is the entry in the message store, if any. */
    fun send(context: Context, recipients: List<String>, parts: List<MmsPart>, rowUri: Uri?): Boolean = runCatching {
        val pdu = MmsPdu.buildSendRequest(recipients, parts, null)
        val file = File(dir(context), "out_${System.currentTimeMillis()}.dat")
        file.writeBytes(pdu)
        val uri = shareWithSystem(context, file)
        val sent = resultIntent(context, MmsSentReceiver::class.java, file.name.hashCode()) {
            putExtra("file", file.absolutePath)
            if (rowUri != null) putExtra("row", rowUri.toString())
        }
        manager(context, -1).sendMultimediaMessage(context, uri, null, null, sent)
        true
    }.onFailure { Log.w(TAG, "Could not send the message", it) }.isSuccess
}
