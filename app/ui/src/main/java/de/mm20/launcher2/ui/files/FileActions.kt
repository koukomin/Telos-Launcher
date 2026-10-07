package de.mm20.launcher2.ui.files

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import de.mm20.launcher2.ui.media.photos.PhotoViewerActivity
import de.mm20.launcher2.ui.media.video.PlayerChoice
import de.mm20.launcher2.ui.media.video.VideoPlayerActivity
import java.io.File

/** Opening, sharing and handing out files. */
internal object FileActions {

    fun mimeOf(name: String): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase()) ?: "*/*"

    /**
     * An address another app can read for [path]. A file the provider does not cover (or that only
     * the superuser can read) is copied into the cache first.
     */
    fun uriFor(context: Context, path: String, rootMode: Boolean): Uri? = runCatching {
        var file = File(path)
        val covered = path.startsWith("/storage/") || path.startsWith(context.cacheDir.path) ||
            path.startsWith(context.filesDir.path) || path.startsWith(android.os.Environment.getExternalStorageDirectory().path)
        if (!covered || !file.canRead()) {
            val dir = File(context.cacheDir, "files_open").apply { mkdirs() }
            val copy = File(dir, file.name)
            if (rootMode) {
                // chmod so that the app can read what the superuser copied
                val r = RootShell.run("cp -f ${RootShell.q(path)} ${RootShell.q(copy.path)} && chmod 644 ${RootShell.q(copy.path)}")
                if (!r.ok) return null
            } else {
                file.copyTo(copy, overwrite = true)
            }
            file = copy
        }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrNull()

    fun open(context: Context, entry: FsEntry, siblings: List<FsEntry>, rootMode: Boolean) {
        val toast = { text: String -> Toast.makeText(context, text, Toast.LENGTH_SHORT).show() }
        when (entry.kind) {
            FileKind.Image -> {
                val images = siblings.filter { it.kind == FileKind.Image && !it.isDir }
                val uris = images.mapNotNull { uriFor(context, it.path, rootMode)?.toString() }
                val index = images.indexOfFirst { it.path == entry.path }
                if (uris.isEmpty() || index < 0) { toast("Cannot open this picture"); return }
                context.startActivity(
                    Intent(context, PhotoViewerActivity::class.java)
                        .putStringArrayListExtra(PhotoViewerActivity.EXTRA_URIS, ArrayList(uris))
                        .putExtra(PhotoViewerActivity.EXTRA_INDEX, index)
                )
            }
            FileKind.Video -> {
                val videos = siblings.filter { it.kind == FileKind.Video && !it.isDir }
                val uris = videos.mapNotNull { uriFor(context, it.path, rootMode)?.toString() }
                val index = videos.indexOfFirst { it.path == entry.path }
                if (uris.isEmpty() || index < 0) { toast("Cannot open this video"); return }
                context.startActivity(
                    Intent(context, PlayerChoice.playerClass(context))
                        .putStringArrayListExtra(VideoPlayerActivity.EXTRA_URIS, ArrayList(uris))
                        .putStringArrayListExtra(VideoPlayerActivity.EXTRA_TITLES, ArrayList(videos.map { it.name.substringBeforeLast('.') }))
                        .putExtra(VideoPlayerActivity.EXTRA_INDEX, index)
                )
            }
            else -> {
                val uri = uriFor(context, entry.path, rootMode) ?: run { toast("Cannot open this file"); return }
                // documents open in Telos Photos
                if (de.mm20.launcher2.ui.media.docs.DocumentTypes.supports(entry.name)) {
                    context.startActivity(
                        Intent(context, de.mm20.launcher2.ui.media.docs.DocumentViewerActivity::class.java)
                            .setDataAndType(uri, mimeOf(entry.name)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    )
                    return
                }
                val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mimeOf(entry.name))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                runCatching { context.startActivity(Intent.createChooser(view, entry.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure { toast("No app can open this file") }
            }
        }
    }

    fun openWith(context: Context, entry: FsEntry, rootMode: Boolean) {
        val uri = uriFor(context, entry.path, rootMode) ?: return
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mimeOf(entry.name)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { context.startActivity(Intent.createChooser(view, entry.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun share(context: Context, entries: List<FsEntry>, rootMode: Boolean) {
        val files = entries.filter { !it.isDir }
        val uris = files.mapNotNull { uriFor(context, it.path, rootMode) }
        if (uris.isEmpty()) {
            Toast.makeText(context, "Folders cannot be shared. Compress them first.", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).setType(mimeOf(files.first().name)).putExtra(Intent.EXTRA_STREAM, uris.first())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*").putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { context.startActivity(Intent.createChooser(intent, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** Folders where a careless change can break the phone, shown only in root mode */
    fun isSystemPath(path: String, rootMode: Boolean): Boolean =
        rootMode && !path.startsWith("rem://") && !path.startsWith("arc://") && !path.startsWith("/storage/") && !path.startsWith("/sdcard") && !path.startsWith("/mnt/sdcard")
}
