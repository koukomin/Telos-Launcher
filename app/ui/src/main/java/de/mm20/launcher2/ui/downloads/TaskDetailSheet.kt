package de.mm20.launcher2.ui.downloads

import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.downloads.DownloadManager
import de.mm20.launcher2.downloads.DownloadState
import de.mm20.launcher2.downloads.DownloadTask
import de.mm20.launcher2.downloads.logic.ArchiveLogic
import de.mm20.launcher2.downloads.logic.Formatting
import de.mm20.launcher2.ui.R
import java.util.Date

@Composable
internal fun TaskDetailSheet(task: DownloadTask, manager: DownloadManager, onDismiss: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(task.displayName, style = MaterialTheme.typography.titleLarge)
            Text(stateLabel(task) + " · " + progressText(task), color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (task.segments.size > 1) {
                Text(stringResource(R.string.dl_detail_segments, task.segments.size), style = MaterialTheme.typography.labelLarge)
                SegmentBars(task)
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (task.state) {
                    DownloadState.Completed -> {
                        Button(onClick = { if (!openDownload(context, task)) Toast.makeText(context, R.string.dl_no_app, Toast.LENGTH_SHORT).show() }) { Text(stringResource(R.string.dl_open)) }
                        OutlinedButton(onClick = { if (!shareDownload(context, task)) Toast.makeText(context, R.string.dl_no_app, Toast.LENGTH_SHORT).show() }) { Text(stringResource(R.string.dl_share)) }
                    }
                    DownloadState.Paused -> Button(onClick = { manager.resume(task.id) }) { Text(stringResource(R.string.dl_resume)) }
                    DownloadState.Failed -> Button(onClick = { manager.resume(task.id) }) { Text(stringResource(R.string.dl_retry)) }
                    else -> Button(onClick = { manager.pause(task.id) }) { Text(stringResource(R.string.dl_pause)) }
                }
                if (task.state == DownloadState.Queued || task.state == DownloadState.Paused) {
                    OutlinedButton(onClick = { manager.moveToTop(task.id) }) { Text(stringResource(R.string.dl_p3_move_top)) }
                    OutlinedButton(onClick = { manager.moveToBottom(task.id) }) { Text(stringResource(R.string.dl_p3_move_bottom)) }
                }
                if (task.state == DownloadState.Completed && ArchiveLogic.isZip(task.name) && !task.fileMissing && task.extractState != "running") {
                    OutlinedButton(onClick = { manager.extract(task.id) }) { Text(stringResource(R.string.dl_p3_extract)) }
                }
                OutlinedButton(onClick = { copyLink(context, task.url) }) { Text(stringResource(R.string.dl_copy_link)) }
                OutlinedButton(onClick = onDelete) { Text(stringResource(R.string.dl_delete)) }
            }

            val fmt = { t: Long -> DateFormat.getMediumDateFormat(context).format(Date(t)) + " " + DateFormat.getTimeFormat(context).format(Date(t)) }
            when (task.extractState) {
                "running" -> Info(R.string.dl_p3_extract, stringResource(R.string.dl_p3_extract_running))
                "done" -> Info(R.string.dl_p3_extract, stringResource(R.string.dl_p3_extract_done))
                "failed" -> Info(R.string.dl_p3_extract, stringResource(R.string.dl_p3_extract_failed))
            }
            task.media?.let { m ->
                if (m.uploader.isNotBlank()) Info(R.string.dl_m_tab, m.uploader)
            }
            Info(R.string.dl_detail_url, task.url)
            task.resolvedUrl?.takeIf { it != task.url }?.let { Info(R.string.dl_detail_final_url, it) }
            if (task.mirrors.isNotEmpty()) Info(R.string.dl_detail_mirrors, task.mirrors.joinToString("\n"))
            Info(R.string.dl_detail_size, if (task.totalBytes >= 0) Formatting.size(task.totalBytes) else "?")
            task.category?.let { Info(R.string.dl_category, categoryLabel(it)) }
            task.mimeType?.let { Info(R.string.dl_detail_mime, it) }
            task.fileUri?.let { Info(R.string.dl_detail_saved_to, android.net.Uri.decode(it)) }
            Info(R.string.dl_detail_resume_support, stringResource(if (task.acceptRanges) R.string.dl_yes else R.string.dl_no))
            if (task.createdAt > 0) Info(R.string.dl_detail_created, fmt(task.createdAt))
            if (task.completedAt > 0) Info(R.string.dl_detail_completed, fmt(task.completedAt))
            if (task.retryCount > 0) Info(R.string.dl_detail_retries, task.retryCount.toString())
            task.checksum?.let { Info(R.string.dl_detail_checksum, it) }
            task.etag?.let { Info("ETag", it) }
            if (!task.error.isNullOrBlank()) Info(R.string.dl_detail_error, task.error!!)
            val headerText = buildString {
                task.userAgent?.let { appendLine("User-Agent: $it") }
                task.referer?.let { appendLine("Referer: $it") }
                if (!task.cookies.isNullOrBlank()) appendLine("Cookie: ***")
                task.headers.forEach { (k, v) -> appendLine("$k: ${if (k.equals("Authorization", true) || k.equals("Cookie", true)) "***" else v}") }
            }.trim()
            if (headerText.isNotEmpty()) Info(R.string.dl_detail_headers, headerText, mono = true)
            Spacer(Modifier.size(16.dp))
        }
    }
}

@Composable
private fun Info(label: Int, value: String, mono: Boolean = false) = Info(stringResource(label), value, mono)

@Composable
private fun Info(label: String, value: String, mono: Boolean = false) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = if (mono) FontFamily.Monospace else null)
    }
}

/** One bar per connection (range), filled by how much of it is done */
@Composable
private fun SegmentBars(task: DownloadTask) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        for (s in task.segments.sortedBy { it.start }) {
            val f = if (s.length > 0) (s.downloaded.toFloat() / s.length).coerceIn(0f, 1f) else 0f
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                Box(Modifier.fillMaxWidth(f).height(6.dp).background(MaterialTheme.colorScheme.primary))
            }
        }
    }
}
