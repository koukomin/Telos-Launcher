package de.mm20.launcher2.ui.keepass

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.keemobile.kotpass.database.KeePassDatabase
import app.keemobile.kotpass.models.Entry
import app.keemobile.kotpass.models.Group
import de.mm20.launcher2.keepasstl.KeePassDatabaseManager
import de.mm20.launcher2.keepasstl.SteganographyEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import java.io.File

class KeePassViewModel : ViewModel(), KoinComponent {
    val database = mutableStateOf<KeePassDatabase?>(null)
    val currentGroup = mutableStateOf<Group?>(null)
    val selectedEntry = mutableStateOf<Entry?>(null)
    val isUnlocked = mutableStateOf(false)
    val unlockError = mutableStateOf(false)
    private val groupStack = mutableStateListOf<Group>()

    val canNavigateUp: Boolean get() = groupStack.isNotEmpty()

    fun loadFromUri(context: Context, uri: Uri, password: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val db = context.contentResolver.openInputStream(uri)?.use { stream ->
                    KeePassDatabaseManager.openDatabase(stream, password)
                }
                withContext(Dispatchers.Main) {
                    if (db != null) {
                        openRoot(db)
                    } else {
                        unlockError.value = true
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { unlockError.value = true }
            }
        }
    }

    fun loadFromSteganography(context: Context, uri: Uri, password: String) {
        viewModelScope.launch(Dispatchers.IO) {
            var tempImg: File? = null
            var payload: ByteArray? = null
            try {
                tempImg = File.createTempFile("steg_extract", ".png", context.cacheDir)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempImg.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                payload = SteganographyEngine.extractFromImage(tempImg)
                if (payload != null) {
                    val db = payload.inputStream().use { stream ->
                        KeePassDatabaseManager.openDatabase(stream, password)
                    }
                    withContext(Dispatchers.Main) {
                        if (db != null) {
                            openRoot(db)
                        } else {
                            unlockError.value = true
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) { unlockError.value = true }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { unlockError.value = true }
            } finally {
                tempImg?.delete()
                payload?.fill(0)
            }
        }
    }

    fun openGroup(group: Group) {
        currentGroup.value?.let { groupStack.add(it) }
        currentGroup.value = group
        selectedEntry.value = null
    }

    fun navigateUp(): Boolean {
        if (selectedEntry.value != null) {
            selectedEntry.value = null
            return true
        }
        if (groupStack.isEmpty()) return false
        currentGroup.value = groupStack.removeAt(groupStack.lastIndex)
        return true
    }

    fun openEntry(entry: Entry) {
        selectedEntry.value = entry
    }

    fun closeEntry() {
        selectedEntry.value = null
    }

    fun lock() {
        database.value = null
        currentGroup.value = null
        selectedEntry.value = null
        groupStack.clear()
        isUnlocked.value = false
        unlockError.value = false
    }

    private fun openRoot(db: KeePassDatabase) {
        database.value = db
        groupStack.clear()
        currentGroup.value = db.content.group
        selectedEntry.value = null
        isUnlocked.value = true
        unlockError.value = false
    }
}
