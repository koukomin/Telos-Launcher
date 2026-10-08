package de.mm20.launcher2.ui.downloads

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import de.mm20.launcher2.downloads.DownloadCategory
import de.mm20.launcher2.downloads.DownloadState
import de.mm20.launcher2.downloads.DownloadTask
import de.mm20.launcher2.downloads.logic.Formatting
import de.mm20.launcher2.downloads.logic.LinkParser
import de.mm20.launcher2.ui.R
import java.io.File

internal fun categoryIcon(c: DownloadCategory?): Int = when (c) {
    DownloadCategory.Video -> R.drawable.movie_24px
    DownloadCategory.Audio -> R.drawable.music_note_24px
    DownloadCategory.Documents -> R.drawable.description_24px
    DownloadCategory.Archives -> R.drawable.folder_zip_24px
    DownloadCategory.Programs -> R.drawable.apk_document_24px
    else -> R.drawable.download_24px
}

@Composable
internal fun categoryLabel(c: DownloadCategory): String = stringResource(
    when (c) {
        DownloadCategory.Video -> R.string.dl_category_video
        DownloadCategory.Audio -> R.string.dl_category_audio
        DownloadCategory.Documents -> R.string.dl_category_documents
        DownloadCategory.Archives -> R.string.dl_category_archives
        DownloadCategory.Programs -> R.string.dl_category_programs
        DownloadCategory.Other -> R.string.dl_category_other
    }
)

@Composable
internal fun stateLabel(t: DownloadTask): String = when (t.state) {
    DownloadState.Queued -> if (t.retryCount > 0 && t.nextRetryAt > System.currentTimeMillis()) {
        stringResource(R.string.dl_state_waiting_retry, t.retryCount)
    } else stringResource(R.string.dl_state_queued)
    DownloadState.Connecting -> stringResource(R.string.dl_state_connecting)
    DownloadState.Downloading -> stringResource(R.string.dl_state_downloading)
    DownloadState.Verifying -> stringResource(R.string.dl_state_verifying)
    DownloadState.Seeding -> stringResource(R.string.dl_t_state_seeding)
    DownloadState.Paused -> stringResource(R.string.dl_state_paused)
    DownloadState.Completed -> stringResource(R.string.dl_state_completed)
    DownloadState.Failed -> stringResource(R.string.dl_state_failed)
}

/** "12.3 MB / 40 MB · 2.1 MB/s · 0:12" */
internal fun progressText(t: DownloadTask): String = buildList {
    if (t.state == DownloadState.Completed) {
        add(Formatting.size(t.totalBytes))
    } else {
        add(if (t.totalBytes > 0) "${Formatting.size(t.downloadedBytes)} / ${Formatting.size(t.totalBytes)}" else Formatting.size(t.downloadedBytes))
        if (t.state == DownloadState.Downloading && t.speedBps > 0) add(Formatting.speed(t.speedBps))
        t.etaSeconds?.let { add(Formatting.duration(it)) }
    }
}.joinToString(" · ")

/** The first http(s) link in the clipboard, null if there is none or it can not be read */
internal fun clipboardLink(context: Context): String? = try {
    val cm = context.getSystemService(ClipboardManager::class.java)
    val text = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
    LinkParser.extractHttp(text).firstOrNull()
} catch (e: Exception) {
    null
}

private fun contentUri(context: Context, task: DownloadTask): Uri? {
    val uri = task.fileUri?.let(Uri::parse) ?: return null
    if (uri.scheme != "file") return uri
    // below Android 10 the file is in the app's own folder
    return try {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(uri.path!!))
    } catch (e: Exception) {
        null
    }
}

internal fun openDownload(context: Context, task: DownloadTask): Boolean {
    val uri = contentUri(context, task) ?: return false
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, task.mimeType ?: "*/*")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        false
    }
}

internal fun shareDownload(context: Context, task: DownloadTask): Boolean {
    val uri = contentUri(context, task) ?: return false
    val send = Intent(Intent.ACTION_SEND).setType(task.mimeType ?: "*/*").putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    return try {
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        false
    }
}

internal fun copyLink(context: Context, link: String) {
    val cm = context.getSystemService(ClipboardManager::class.java)
    cm.setPrimaryClip(android.content.ClipData.newPlainText("link", link))
    Toast.makeText(context, R.string.dl_link_copied, Toast.LENGTH_SHORT).show()
}
