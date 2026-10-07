package de.mm20.launcher2.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.backup.BackupGroup
import de.mm20.launcher2.ui.R

private fun title(group: BackupGroup) = when (group) {
    BackupGroup.Launcher -> R.string.backup_group_launcher
    BackupGroup.Notes -> R.string.backup_group_notes
    BackupGroup.Calendar -> R.string.backup_group_calendar
}

private fun summary(group: BackupGroup) = when (group) {
    BackupGroup.Launcher -> R.string.backup_group_launcher_summary
    BackupGroup.Notes -> R.string.backup_group_notes_summary
    BackupGroup.Calendar -> R.string.backup_group_calendar_summary
}

/**
 * The parts of a backup with a checkbox each and an **All** button, used to choose what is put
 * into a backup and what is restored from one.
 */
@Composable
fun BackupGroupList(
    available: List<BackupGroup>,
    selected: Set<BackupGroup>,
    onToggle: (BackupGroup) -> Unit,
    onSelectAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        for (group in available) {
            Row(
                Modifier.fillMaxWidth().clickable { onToggle(group) }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = group in selected, onCheckedChange = { onToggle(group) })
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(stringResource(title(group)), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(summary(group)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (available.size > 1) {
            OutlinedButton(onClick = onSelectAll, modifier = Modifier.padding(top = 8.dp), enabled = !selected.containsAll(available)) {
                Text(stringResource(R.string.backup_all))
            }
        }
    }
}
