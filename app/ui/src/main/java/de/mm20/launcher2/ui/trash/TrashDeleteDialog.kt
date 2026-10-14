package de.mm20.launcher2.ui.trash

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.comms.files.TelosTrash
import de.mm20.launcher2.ui.R
import org.koin.compose.koinInject

/** Shared delete choice for files, photos, documents and local media. */
@Composable
fun TrashDeleteDialog(count: Int, canTrash: Boolean, onTrash: () -> Unit, onPermanent: () -> Unit, onDismiss: () -> Unit, warning: String? = null, fixedRetentionDays: Int? = null) {
    val trash: TelosTrash = koinInject()
    var permanent by remember { mutableStateOf(!canTrash) }
    var days by remember { mutableStateOf((fixedRetentionDays ?: trash.retentionDays).toString()) }
    val validDays = days.toIntOrNull()?.takeIf { it in 1..365 }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.telos_trash_delete_title, count)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (canTrash) {
                Row(Modifier.fillMaxWidth().clickable { permanent = false }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = !permanent, onClick = { permanent = false })
                    Text(stringResource(R.string.telos_trash_move))
                }
                if (!permanent) {
                    OutlinedTextField(days, { days = it.filter(Char::isDigit).take(3) }, label = { Text(stringResource(R.string.telos_trash_retention)) },
                        singleLine = true, isError = validDays == null, readOnly = fixedRetentionDays != null,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                    Text(stringResource(if (fixedRetentionDays != null) R.string.telos_trash_android_retention else R.string.telos_trash_retention_hint), style = MaterialTheme.typography.bodySmall)
                }
            } else Text(stringResource(R.string.telos_trash_unavailable), style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth().clickable { permanent = true }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = permanent, onClick = { permanent = true })
                Text(stringResource(R.string.telos_trash_permanent))
            }
            if (permanent) Text(stringResource(R.string.telos_trash_permanent_warning), color = MaterialTheme.colorScheme.error)
            warning?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(enabled = permanent || validDays != null, onClick = {
            if (permanent) onPermanent() else { if (fixedRetentionDays == null) trash.retentionDays = validDays!!; onTrash() }
            onDismiss()
        }) { Text(stringResource(if (permanent) R.string.telos_trash_permanent else R.string.telos_trash_move), color = if (permanent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.hc_cancel)) } })
}
