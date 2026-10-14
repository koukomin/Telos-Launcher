package de.mm20.launcher2.ui.files

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.R

@Composable
fun FilesToolbarSettingsDialog(initial: FilesToolbarConfig, onDismiss: () -> Unit, onSave: (FilesToolbarConfig) -> Unit) {
    var config by remember(initial) { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.telos_files_toolbar_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.telos_files_toolbar_info, FilesToolbarConfig.MAX_ACTIONS))
                config.order.forEachIndexed { index, action ->
                    val label = stringResource(when (action) {
                        FilesToolbarAction.View -> R.string.au22_files_view_mode
                        FilesToolbarAction.Sort -> R.string.hc_sort_by_2
                        FilesToolbarAction.Hidden -> R.string.hf_files_show_hidden
                        FilesToolbarAction.Refresh -> R.string.hc_refresh
                    })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = action in config.visible,
                            enabled = action in config.visible || config.visible.size < FilesToolbarConfig.MAX_ACTIONS,
                            onCheckedChange = { checked -> config = config.copy(visible = if (checked) config.visible + action else config.visible - action) })
                        Text(label, Modifier.weight(1f))
                        fun move(destination: Int) {
                            val order = config.order.toMutableList()
                            order.removeAt(index); order.add(destination, action)
                            config = config.copy(order = order)
                        }
                        IconButton(onClick = { move(index - 1) }, enabled = index > 0) {
                            Icon(painterResource(R.drawable.keyboard_arrow_up_24px), stringResource(R.string.telos_files_toolbar_up, label))
                        }
                        IconButton(onClick = { move(index + 1) }, enabled = index < config.order.lastIndex) {
                            Icon(painterResource(R.drawable.keyboard_arrow_down_24px), stringResource(R.string.telos_files_toolbar_down, label))
                        }
                    }
                }
                TextButton(onClick = { config = FilesToolbarConfig() }) { Text(stringResource(R.string.telos_files_toolbar_reset)) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(config) }) { Text(stringResource(R.string.telos_files_toolbar_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.hc_cancel)) } })
}
