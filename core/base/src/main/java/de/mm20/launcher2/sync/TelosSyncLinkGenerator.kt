package de.mm20.launcher2.sync

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast

/**
 * Universal Share & Link Generator for Telos Sync.
 * Generates syncthing/telos-sync links for files and folders
 * in Telos Files, Docs, and Media, copying them directly to clipboard.
 */
object TelosSyncLinkGenerator {

    fun generateFolderLink(folderId: String, path: String, deviceId: String? = null): String {
        val builder = Uri.Builder()
            .scheme("telos-sync")
            .authority("share")
            .appendQueryParameter("folder", folderId)
            .appendQueryParameter("path", path)
        if (!deviceId.isNullOrBlank()) {
            builder.appendQueryParameter("device", deviceId)
        }
        return builder.build().toString()
    }

    fun shareFileOrFolderLink(context: Context, path: String, folderId: String = "default"): String {
        val link = generateFolderLink(folderId, path)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard != null) {
            val clip = ClipData.newPlainText("Telos Sync Link", link)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Telos Sync Link copied: $link", Toast.LENGTH_SHORT).show()
        }
        return link
    }
}
