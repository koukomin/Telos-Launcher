package de.mm20.launcher2.downloads

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Explicit trampoline for download notification Open/Share actions. The PendingIntent must
 * target this activity so CodeQL does not treat ACTION_VIEW / ACTION_SEND as implicit
 * PendingIntents; the user-chosen viewer is started only after this process is already in
 * the foreground.
 */
class DownloadNotificationActionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent.data
        val mime = intent.type ?: "*/*"
        if (uri != null) {
            val next = when (intent.action) {
                ACTION_SHARE -> Intent.createChooser(
                    Intent(Intent.ACTION_SEND)
                        .setType(mime)
                        .putExtra(Intent.EXTRA_STREAM, uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                    null,
                )
                else -> Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, mime)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { startActivity(next) }
        }
        finish()
    }

    companion object {
        const val ACTION_OPEN = "de.mm20.launcher2.downloads.OPEN"
        const val ACTION_SHARE = "de.mm20.launcher2.downloads.SHARE"
    }
}
