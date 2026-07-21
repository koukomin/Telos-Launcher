package de.mm20.launcher2.ui.settings.webapps

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
import de.mm20.launcher2.preferences.GestureAction
import de.mm20.launcher2.preferences.ui.GestureSettings
import de.mm20.launcher2.preferences.ui.WebAppBrowsingSettings
import de.mm20.launcher2.search.WebAppShortcut
import de.mm20.launcher2.webappshortcuts.WebAppShortcutRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

enum class PanelDirection { Left, Right }

class WebAppsSettingsScreenVM : ViewModel(), KoinComponent {
    private val context: Context by inject()
    private val webAppShortcutRepository: WebAppShortcutRepository by inject()
    private val gestureSettings: GestureSettings by inject()
    private val browsingSettings: WebAppBrowsingSettings by inject()

    val adBlockEnabled = browsingSettings.adBlockEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), true)

    fun setAdBlockEnabled(enabled: Boolean) = browsingSettings.setAdBlockEnabled(enabled)

    val zoomControlsEnabled = browsingSettings.zoomControlsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), true)

    fun setZoomControlsEnabled(enabled: Boolean) = browsingSettings.setZoomControlsEnabled(enabled)

    val shortcuts = webAppShortcutRepository.search("", false)
        .map { it.sortedBy { s -> s.label.lowercase() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    val direction = combine(
        gestureSettings.swipeLeft,
        gestureSettings.swipeRight,
    ) { left, right ->
        when {
            left is GestureAction.WebAppsPanel -> PanelDirection.Left
            right is GestureAction.WebAppsPanel -> PanelDirection.Right
            else -> null
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setEnabled(enabled: Boolean) {
        if (enabled) {
            setDirection(PanelDirection.Right)
        } else {
            viewModelScope.launch {
                if (gestureSettings.swipeLeft.first() is GestureAction.WebAppsPanel) {
                    gestureSettings.setSwipeLeft(GestureAction.NoAction)
                }
                if (gestureSettings.swipeRight.first() is GestureAction.WebAppsPanel) {
                    gestureSettings.setSwipeRight(GestureAction.NoAction)
                }
            }
        }
    }

    fun setDirection(direction: PanelDirection) {
        viewModelScope.launch {
            val left = gestureSettings.swipeLeft.first()
            val right = gestureSettings.swipeRight.first()
            if (direction == PanelDirection.Left) {
                if (right is GestureAction.WebAppsPanel) gestureSettings.setSwipeRight(GestureAction.NoAction)
                gestureSettings.setSwipeLeft(GestureAction.WebAppsPanel)
            } else {
                if (left is GestureAction.WebAppsPanel) gestureSettings.setSwipeLeft(GestureAction.NoAction)
                gestureSettings.setSwipeRight(GestureAction.WebAppsPanel)
            }
        }
    }

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
        rendererPackage: String?,
        showInGrid: Boolean,
        showInPanel: Boolean,
        iconSource: WebAppShortcut.IconSource,
    ) {
        val oldIconUri = existing?.iconUri
        if (existing != null) {
            webAppShortcutRepository.update(existing, label, url, iconUri, faviconUrl, rendererPackage, showInGrid, showInPanel, existing.order, iconSource)
        } else {
            webAppShortcutRepository.create(label, url, iconUri, faviconUrl, rendererPackage, showInGrid, showInPanel, 0, iconSource)
        }
        if (oldIconUri != null && oldIconUri != iconUri) {
            deleteIconFile(oldIconUri)
        }
        dismissDialogs()
    }

    fun setPlacement(shortcut: WebAppShortcut, showInGrid: Boolean, showInPanel: Boolean) {
        webAppShortcutRepository.update(
            shortcut,
            shortcut.label,
            shortcut.url,
            shortcut.iconUri,
            shortcut.faviconUrl,
            shortcut.rendererPackage,
            showInGrid,
            showInPanel,
            shortcut.order,
            shortcut.iconSource
        )
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
