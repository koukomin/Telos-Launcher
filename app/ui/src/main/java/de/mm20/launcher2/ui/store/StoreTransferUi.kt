package de.mm20.launcher2.ui.store
import de.mm20.launcher2.i18n.R as I18nR

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.mm20.launcher2.store.installer.Downloader
import de.mm20.launcher2.store.installer.StoreDownloadStatus
import de.mm20.launcher2.ui.R

@Composable
internal fun StoreTransferProgress(id: String) {
    val downloader: Downloader = org.koin.compose.koinInject()
    val transfers by downloader.progress.collectAsStateWithLifecycle()
    val transfer = transfers[id] ?: return
    if (transfer.status == StoreDownloadStatus.Complete || transfer.status == StoreDownloadStatus.Failed) return
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val total = transfer.total
        if (total != null && total > 0) LinearProgressIndicator(progress = { (transfer.bytes.toFloat() / total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Text(
            when (transfer.status) {
                StoreDownloadStatus.Paused -> stringResource(I18nR.string.telos_store_paused)
                StoreDownloadStatus.WaitingForNetwork -> stringResource(I18nR.string.telos_store_waiting_network)
                else -> stringResource(I18nR.string.telos_store_transfer, transfer.bytesPerSecond / 1024, transfer.bytes / (1024 * 1024))
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun StoreUpdateConsent(viewModel: StoreViewModel) {
    val pending by viewModel.pendingUpdates.collectAsStateWithLifecycle()
    if (pending.isEmpty()) return
    AlertDialog(
        onDismissRequest = viewModel::dismissUpdates,
        title = { Text(stringResource(I18nR.string.telos_store_auto_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(I18nR.string.telos_store_auto_explanation))
                Text(pending.joinToString { it.displayName }, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { viewModel.confirmUpdates(true) }) { Text(stringResource(I18nR.string.telos_store_auto_enable)) } },
        dismissButton = { TextButton(onClick = { viewModel.confirmUpdates(false) }) { Text(stringResource(I18nR.string.telos_store_update_once)) } },
    )
}
