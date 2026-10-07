package de.mm20.launcher2.ui.comms

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.content.FileProvider
import de.mm20.launcher2.applock.SettingsDeepLinkContract
import java.io.File

/**
 * Opens Telos Messages for a text link (sms:, smsto:, mms:, mmsto:) or when something is shared to
 * Telos Messages: a text, pictures, videos. It is the entry the system and other apps know the
 * messages app by (also required of a default SMS app); the screen itself lives in the Comms dashboard.
 */
class ComposeSmsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val number = intent.data?.schemeSpecificPart?.substringBefore('?')?.takeIf { it.isNotBlank() }.orEmpty()
        val query = intent.data?.let { runCatching { Uri.parse("x://x?" + it.schemeSpecificPart.substringAfter('?', "")) }.getOrNull() }
        val body = intent.getStringExtra("sms_body")
            ?: intent.getStringExtra(Intent.EXTRA_TEXT)
            ?: query?.getQueryParameter("body")
            ?: ""
        val shared = copyShared()

        val forward = Intent().setClassName(packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
            .putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_COMMS)
            .putExtra(SettingsDeepLinkContract.EXTRA_COMMS_TAB, "messages")
            .putExtra(SettingsDeepLinkContract.EXTRA_DIAL_NUMBER, number)
            .putExtra(SettingsDeepLinkContract.EXTRA_SMS_BODY, body)
            .putStringArrayListExtra(SettingsDeepLinkContract.EXTRA_SMS_ATTACHMENTS, ArrayList(shared.map { it.toString() }))
        // a shared picture has to stay readable for the messages screen
        shared.firstOrNull()?.let { first ->
            forward.clipData = ClipData.newRawUri("shared", first).also { clip -> shared.drop(1).forEach { clip.addItem(ClipData.Item(it)) } }
            forward.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(forward)
        finish()
    }

    /** The shared files are copied into Telos' own cache: what was shared may be gone or unreadable later */
    private fun copyShared(): List<Uri> {
        val uris = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(streamExtra())
            Intent.ACTION_SEND_MULTIPLE -> streamsExtra()
            else -> emptyList()
        }
        val dir = File(cacheDir, "shared_attachments").apply { mkdirs() }
        // earlier copies are not needed any more
        dir.listFiles()?.filter { it.lastModified() < System.currentTimeMillis() - 60 * 60_000 }?.forEach { it.delete() }
        return uris.take(10).mapIndexedNotNull { i, uri ->
            runCatching {
                val file = File(dir, "${System.currentTimeMillis()}_$i")
                contentResolver.openInputStream(uri)!!.use { input -> file.outputStream().use { input.copyTo(it) } }
                if (file.length() > 25L * 1024 * 1024) { file.delete(); return@runCatching null }
                // keep the type: it is derived from the original
                val type = contentResolver.getType(uri)
                val named = File(dir, file.name + (type?.substringAfter('/')?.let { ".$it" } ?: ""))
                file.renameTo(named)
                FileProvider.getUriForFile(this, "$packageName.fileprovider", named)
            }.getOrNull()
        }
    }

    @Suppress("DEPRECATION")
    private fun streamExtra(): Uri? = intent.getParcelableExtra(Intent.EXTRA_STREAM)

    @Suppress("DEPRECATION")
    private fun streamsExtra(): List<Uri> = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
}
