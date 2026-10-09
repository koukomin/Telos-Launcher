package de.mm20.launcher2.ui.downloads

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import de.mm20.launcher2.applock.SettingsDeepLinkContract
import de.mm20.launcher2.downloads.logic.LinkParser
import de.mm20.launcher2.downloads.logic.TorrentSources
import de.mm20.launcher2.ui.R
import java.io.File

/**
 * "Telos Downloads" in the share menu (text) and for opening download links (http or https links
 * of files), magnet links and .torrent files from other apps. It only collects the links and opens
 * the add sheet; nothing is downloaded before the user confirms. Telos Video is offered for the
 * same magnet links and .torrent files (it streams them while they download), the user chooses.
 */
class DownloadShareActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
            Intent.ACTION_VIEW -> intent.dataString.orEmpty()
            else -> ""
        }
        val links = ArrayList<String>()
        links.addAll(TorrentSources.extract(text))
        links.addAll(LinkParser.extractHttp(text).filter { it !in links })
        // a .torrent file: read it now, the permission to read it ends with this activity
        val streamUri: Uri? = when (intent?.action) {
            Intent.ACTION_SEND -> @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
            Intent.ACTION_VIEW -> intent.data?.takeIf { it.scheme == "content" || it.scheme == "file" }
            else -> null
        }
        if (streamUri != null) copyTorrentFile(streamUri)?.let { links.add(0, it) }
        if (links.isEmpty()) {
            Toast.makeText(this, R.string.dl_invalid_links, Toast.LENGTH_SHORT).show()
        } else {
            startActivity(
                Intent().setClassName(packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
                    .putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_DOWNLOADS)
                    .putStringArrayListExtra(SettingsDeepLinkContract.EXTRA_DOWNLOAD_URLS, links)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        finish()
    }

    /** Copies a shared .torrent file into the cache and returns its file: address, null if it is not one */
    private fun copyTorrentFile(uri: Uri): String? = try {
        val dir = File(cacheDir, "torrent-inbox").apply { mkdirs() }
        // old copies are not needed any more
        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 24 * 3600 * 1000L }?.forEach { it.delete() }
        val target = File(dir, "${System.nanoTime()}.torrent")
        val input = if (uri.scheme == "file") File(uri.path!!).inputStream() else contentResolver.openInputStream(uri)
        var total = 0L
        input?.use { i -> target.outputStream().use { o ->
            val buf = ByteArray(16 * 1024)
            while (true) {
                val n = i.read(buf)
                if (n < 0) break
                total += n
                if (total > 8L * 1024 * 1024) throw java.io.IOException("too large")
                o.write(buf, 0, n)
            }
        } } ?: throw java.io.IOException("not readable")
        // a torrent file is a bencoded dictionary
        if (target.length() < 10 || target.inputStream().use { it.read() } != 'd'.code) { target.delete(); null }
        else Uri.fromFile(target).toString()
    } catch (e: Exception) {
        // an unreadable, oversized or half written copy must not stay in the cache
        runCatching { File(cacheDir, "torrent-inbox").listFiles()?.filter { it.length() == 0L || it.length() > 8L * 1024 * 1024 }?.forEach { it.delete() } }
        null
    }
}
