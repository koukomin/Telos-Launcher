package de.mm20.launcher2.ui.settings.webappshortcuts

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Scale
import de.mm20.launcher2.search.WebAppShortcut
import de.mm20.launcher2.webappshortcuts.WebAppShortcutRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class WebAppShortcutsSettingsScreenVM : ViewModel(), KoinComponent {
    private val context: Context by inject()
    private val webAppShortcutRepository: WebAppShortcutRepository by inject()

    val shortcuts = webAppShortcutRepository.search("", false)
        .map { it.sortedBy { s -> s.label.lowercase() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    val showCreateDialog = mutableStateOf(false)
    val showEditDialogFor = mutableStateOf<WebAppShortcut?>(null)

    fun createShortcut() {
        showCreateDialog.value = true
    }

    fun editShortcut(shortcut: WebAppShortcut) {
        showEditDialogFor.value = shortcut
    }

    fun dismissDialogs() {
        showCreateDialog.value = false
        showEditDialogFor.value = null
    }

    fun save(
        existing: WebAppShortcut?,
        label: String,
        url: String,
        iconUri: String?,
        faviconUrl: String?,
    ) {
        val oldIconUri = existing?.iconUri
        if (existing != null) {
            webAppShortcutRepository.update(existing, label, url, iconUri, faviconUrl)
        } else {
            webAppShortcutRepository.create(label, url, iconUri, faviconUrl)
        }
        if (oldIconUri != null && oldIconUri != iconUri) {
            deleteIconFile(oldIconUri)
        }
        dismissDialogs()
    }

    fun delete(shortcut: WebAppShortcut) {
        webAppShortcutRepository.delete(shortcut)
        shortcut.iconUri?.let { deleteIconFile(it) }
    }

    private fun deleteIconFile(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            File(path).delete()
        }
    }

    suspend fun findFavicon(url: String): String? {
        return webAppShortcutRepository.findFavicon(url)
    }

    suspend fun importIcon(uri: Uri, sizePx: Int): String? = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, "webappshortcut_${UUID.randomUUID()}")
        val request = ImageRequest.Builder(context)
            .data(uri)
            .size(sizePx)
            .scale(Scale.FIT)
            .build()
        val drawable = context.imageLoader.execute(request).drawable ?: return@withContext null
        val bitmap = drawable.toBitmap()
        FileOutputStream(file).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        file.absolutePath
    }
}
