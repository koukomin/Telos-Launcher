package de.mm20.launcher2.ui.common

import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.backup.BackupCompatibility
import de.mm20.launcher2.backup.BackupGroup
import de.mm20.launcher2.backup.BackupManager
import de.mm20.launcher2.backup.BackupMetadata
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class RestoreBackupSheetVM : ViewModel(), KoinComponent {

    private val backupManager: BackupManager by inject()

    private var restoreUri: Uri? = null

    val state = mutableStateOf(RestoreBackupState.Parsing)
    val metadata = mutableStateOf<BackupMetadata?>(null)
    val compatibility = mutableStateOf<BackupCompatibility?>(null)

    /** The parts that are restored, all that the backup contains to start with */
    val selected = mutableStateOf<Set<BackupGroup>>(emptySet())

    fun toggle(group: BackupGroup) {
        selected.value = if (group in selected.value) selected.value - group else selected.value + group
    }

    fun selectAll() {
        selected.value = metadata.value?.groups ?: emptySet()
    }

    fun setInputUri(uri: Uri) {
        restoreUri = uri
        state.value = RestoreBackupState.Parsing
        viewModelScope.launch {
            val metadata = backupManager.readBackupMeta(uri)
            if (metadata == null) {
                state.value = RestoreBackupState.InvalidFile
            } else {
                state.value = RestoreBackupState.Ready
                compatibility.value = backupManager.checkCompatibility(metadata)
            }
            this@RestoreBackupSheetVM.metadata.value = metadata
            selected.value = metadata?.groups ?: emptySet()
        }
    }

    fun restore() {
        val uri = restoreUri ?: return

        viewModelScope.launch {
            state.value = RestoreBackupState.Restoring
            try {
                backupManager.restore(uri, selected.value)
                state.value = RestoreBackupState.Restored
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.e("MM20", "Restore failed", e)
                state.value = RestoreBackupState.InvalidFile
            }
        }
    }
}

enum class RestoreBackupState {
    Parsing,
    InvalidFile,
    Ready,
    Restoring,
    Restored,
}