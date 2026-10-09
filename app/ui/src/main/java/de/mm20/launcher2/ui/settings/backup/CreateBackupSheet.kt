package de.mm20.launcher2.ui.settings.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import de.mm20.launcher2.backup.BackupGroup
import de.mm20.launcher2.ui.common.BackupGroupList
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.DismissableBottomSheet
import de.mm20.launcher2.ui.component.LargeMessage
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Composable
fun CreateBackupSheet(
    expanded: Boolean,
    onDismissRequest: () -> Unit
) {
    DismissableBottomSheet(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) {
        val viewModel: CreateBackupSheetVM = viewModel()

        val backupLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/vnd.de.mm20.launcher2.backup"),
            onResult = {
                if (it != null) viewModel.createBackup(it)
                else onDismissRequest()
            }
        )
        LaunchedEffect(null) {
            viewModel.reset()
        }
        fun chooseFile() {
            val fileName = "${
                ZonedDateTime.now().format(
                    DateTimeFormatter.ISO_INSTANT
                ).replace(":", "_")
            }.kvaesitso"
            backupLauncher.launch(fileName)
        }

        val state by viewModel.state
        AnimatedContent(
            state,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            if (it == CreateBackupState.Ready) {
                Column(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.backup_choose_parts), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
                    BackupGroupList(
                        available = BackupGroup.entries.toList(),
                        selected = viewModel.selected.value,
                        onToggle = viewModel::toggle,
                        onSelectAll = viewModel::selectAll,
                    )
                    Button(
                        onClick = ::chooseFile,
                        enabled = viewModel.selected.value.isNotEmpty(),
                        modifier = Modifier.padding(top = 16.dp).navigationBarsPadding(),
                    ) { Text(stringResource(if (viewModel.selected.value.isEmpty()) R.string.backup_nothing_selected else R.string.backup_create)) }
                }
            } else if (it == CreateBackupState.BackingUp) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp)
                    )
                }
            } else if (it == CreateBackupState.Failed) {
                Column(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    LargeMessage(
                        icon = R.drawable.error_48px,
                        text = stringResource(R.string.au3_sysa_backup_failed)
                    )
                    Button(
                        onClick = viewModel::retry,
                        modifier = Modifier.padding(top = 16.dp),
                    ) { Text(stringResource(R.string.au3_sysa_try_again)) }
                }
            } else if (it == CreateBackupState.BackedUp) {
                LargeMessage(
                    modifier = Modifier.aspectRatio(1f),
                    icon = R.drawable.check_circle_48px,
                    text = stringResource(
                        id = R.string.backup_complete
                    )
                )
            }
        }
    }
}

