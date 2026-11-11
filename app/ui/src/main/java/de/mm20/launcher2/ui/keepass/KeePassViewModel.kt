package de.mm20.launcher2.ui.keepass

import android.content.Context
import android.net.Uri
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

    val isUnlocked = mutableStateOf(false)
    val unlockError = mutableStateOf(false)

    fun loadFromUri(context: Context, uri: Uri, password: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val stream = context.contentResolver.openInputStream(uri)
                if (stream != null) {
                    val db = KeePassDatabaseManager.openDatabase(stream, password)
                    withContext(Dispatchers.Main) {
                        if (db != null) {
                            database.value = db
                            currentGroup.value = db.content.group
                            isUnlocked.value = true
                            unlockError.value = false
                        } else {
                            unlockError.value = true
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) { unlockError.value = true }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { unlockError.value = true }
            }
        }
    }

    fun loadFromSteganography(context: Context, uri: Uri, password: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Copy to temp file
                val tempImg = File.createTempFile("steg_extract", ".png", context.cacheDir)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempImg.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                val payload = SteganographyEngine.extractFromImage(tempImg)
                tempImg.delete()

                if (payload != null) {
                    val db = KeePassDatabaseManager.openDatabase(payload.inputStream(), password)
                    withContext(Dispatchers.Main) {
                        if (db != null) {
                            database.value = db
                            currentGroup.value = db.content.group
                            isUnlocked.value = true
                            unlockError.value = false
                        } else {
                            unlockError.value = true
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) { unlockError.value = true }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { unlockError.value = true }
            }
        }
    }

    fun lock() {
        database.value = null
        currentGroup.value = null
        isUnlocked.value = false
        unlockError.value = false
    }
}
