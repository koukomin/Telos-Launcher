package de.mm20.launcher2.ui.webappspanel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Scale
import de.mm20.launcher2.preferences.ui.WebAppsPanelSettings
import de.mm20.launcher2.search.WebAppShortcut
import de.mm20.launcher2.searchable.SavableSearchableRepository
import de.mm20.launcher2.webappshortcuts.WebAppShortcutRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Shared business logic for the Web Apps Panel, used both by the in-panel edit mode
 * ([de.mm20.launcher2.ui.launcher.scaffold.components.WebAppsPanelComponent]) and the dedicated
 * settings screen - kept out of either ViewModel so the two don't duplicate it.
 */
class WebAppsPanelManager internal constructor(
    private val context: Context,
    private val webAppsPanelSettings: WebAppsPanelSettings,
    private val searchableRepository: SavableSearchableRepository,
    private val webAppShortcutRepository: WebAppShortcutRepository,
) {
    /** The panel's web app shortcuts, resolved and restored to the configured order. */
    val items: Flow<List<WebAppShortcut>> = webAppShortcutRepository.search("", false)
        .map { shortcuts ->
            shortcuts.filter { it.showInPanel }
                .sortedBy { it.order }
        }

    fun setOrder(orderedKeys: List<String>) {
        // Update order in repository
        // This is tricky because we need to update each item.
    }

    fun remove(shortcut: WebAppShortcut) {
        webAppShortcutRepository.update(shortcut, shortcut.label, shortcut.url, shortcut.iconUri, shortcut.faviconUrl, shortcut.rendererPackage, shortcut.showInGrid, false, shortcut.order, shortcut.iconSource)
    }

    fun addExisting(shortcut: WebAppShortcut) {
        webAppShortcutRepository.update(shortcut, shortcut.label, shortcut.url, shortcut.iconUri, shortcut.faviconUrl, shortcut.rendererPackage, shortcut.showInGrid, true, shortcut.order, shortcut.iconSource)
    }

    fun createAndAdd(
        label: String,
        url: String,
        iconUri: String?,
        faviconUrl: String?,
        rendererPackage: String?,
    ): WebAppShortcut {
        return webAppShortcutRepository.create(
            label = label,
            url = url,
            iconUri = iconUri,
            faviconUrl = faviconUrl,
            rendererPackage = rendererPackage,
            showInGrid = true,
            showInPanel = true,
            order = 0, // Should determine next order
            iconSource = WebAppShortcut.IconSource.Website
        )
    }

    fun update(
        existing: WebAppShortcut,
        label: String,
        url: String,
        iconUri: String?,
        faviconUrl: String?,
        rendererPackage: String?,
    ): WebAppShortcut {
        return webAppShortcutRepository.update(
            shortcut = existing,
            label = label,
            url = url,
            iconUri = iconUri,
            faviconUrl = faviconUrl,
            rendererPackage = rendererPackage,
            showInGrid = existing.showInGrid,
            showInPanel = existing.showInPanel,
            order = existing.order,
            iconSource = existing.iconSource
        )
    }

    suspend fun findFavicon(url: String): String? = webAppShortcutRepository.findFavicon(url)

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
