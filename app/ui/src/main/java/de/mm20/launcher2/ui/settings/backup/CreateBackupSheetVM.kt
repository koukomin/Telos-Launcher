package de.mm20.launcher2.ui.settings.backup

import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.backup.BackupGroup
import de.mm20.launcher2.backup.BackupManager
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class CreateBackupSheetVM : ViewModel(), KoinComponent {

    private val backupManager: BackupManager by inject()

    val state = mutableStateOf(CreateBackupState.Ready)

    /** The parts that go into the backup, all of them to start with */
    val selected = mutableStateOf(BackupGroup.entries.toSet())

    fun toggle(group: BackupGroup) {
        selected.value = if (group in selected.value) selected.value - group else selected.value + group
    }

    fun selectAll() {
        selected.value = BackupGroup.entries.toSet()
    }

    /** Back to the choice of parts after a failed backup */
    fun retry() {
        state.value = CreateBackupState.Ready
    }

    fun reset() {
        state.value = CreateBackupState.Ready
        selectAll()
    }

    fun createBackup(uri: Uri) {
        viewModelScope.launch {
            state.value = CreateBackupState.BackingUp
            try {
                backupManager.backup(uri, selected.value)
                state.value = CreateBackupState.BackedUp
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.e("MM20", "Backup failed", e)
                state.value = CreateBackupState.Failed
            }
        }
    }
}

enum class CreateBackupState {
    Ready,
    BackingUp,
    BackedUp,
    Failed,
}