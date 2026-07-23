package de.mm20.launcher2.ui.webappspanel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Scale
import de.mm20.launcher2.data.customattrs.CustomIcon
import de.mm20.launcher2.icons.DynamicLauncherIcon
import de.mm20.launcher2.icons.IconService
import de.mm20.launcher2.icons.LauncherIconRenderSettings
import de.mm20.launcher2.icons.StaticLauncherIcon
import de.mm20.launcher2.preferences.ui.WebAppsPanelSettings
import de.mm20.launcher2.search.WebAppShortcut
import de.mm20.launcher2.searchable.SavableSearchableRepository
import de.mm20.launcher2.ui.settings.webapps.WebAppIconPickerTarget
import de.mm20.launcher2.webappshortcuts.WebAppShortcutRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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
    private val iconService: IconService,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        // Panel membership used to live in a DataStore key list (webAppsPanelItems); the unified
        // Web Apps model moved it onto each shortcut's own showInPanel flag, but shortcuts saved
        // before that change deserialize with showInPanel = false - without this reconciliation,
        // everything the user had put on the panel silently vanished from it. One-shot: applies
        // the legacy list onto the flags, then clears it so it never runs again.
        scope.launch {
            val legacyKeys = webAppsPanelSettings.items.first()
            if (legacyKeys.isEmpty()) return@launch
            val shortcuts = webAppShortcutRepository.search("", false).first()
            for ((index, key) in legacyKeys.withIndex()) {
                val shortcut = shortcuts.firstOrNull { it.key == key } ?: continue
                if (!shortcut.showInPanel) {
                    webAppShortcutRepository.update(
                        shortcut = shortcut,
                        label = shortcut.label,
                        url = shortcut.url,
                        iconUri = shortcut.iconUri,
                        faviconUrl = shortcut.faviconUrl,
                        rendererPackage = shortcut.rendererPackage,
                        showInGrid = shortcut.showInGrid,
                        showInPanel = true,
                        order = index,
                        iconSource = shortcut.iconSource,
                        customCss = shortcut.customCss,
                    )
                }
            }
            webAppsPanelSettings.setItems(emptyList())
        }
    }

    /** The panel's web app shortcuts, resolved and restored to the configured order. */
    val items: Flow<List<WebAppShortcut>> = webAppShortcutRepository.search("", false)
        .map { shortcuts ->
            shortcuts.filter { it.showInPanel }
                .sortedBy { it.order }
        }

    /** Registered web apps that are not on the panel yet - what the panel's edit mode offers to add. */
    val availableToAdd: Flow<List<WebAppShortcut>> = webAppShortcutRepository.search("", false)
        .map { shortcuts ->
            shortcuts.filterNot { it.showInPanel }
                .sortedBy { it.label.lowercase() }
        }

    /** Persists [orderedShortcuts]' positions as each shortcut's panel order. */
    fun setOrder(orderedShortcuts: List<WebAppShortcut>) {
        for ((index, shortcut) in orderedShortcuts.withIndex()) {
            if (shortcut.order == index) continue
            webAppShortcutRepository.update(
                shortcut = shortcut,
                label = shortcut.label,
                url = shortcut.url,
                iconUri = shortcut.iconUri,
                faviconUrl = shortcut.faviconUrl,
                rendererPackage = shortcut.rendererPackage,
                showInGrid = shortcut.showInGrid,
                showInPanel = shortcut.showInPanel,
                order = index,
                iconSource = shortcut.iconSource,
                customCss = shortcut.customCss,
            )
        }
    }

    fun remove(shortcut: WebAppShortcut) {
        webAppShortcutRepository.update(shortcut, shortcut.label, shortcut.url, shortcut.iconUri, shortcut.faviconUrl, shortcut.rendererPackage, shortcut.showInGrid, false, shortcut.order, shortcut.iconSource, shortcut.customCss)
    }

    fun addExisting(shortcut: WebAppShortcut) {
        scope.launch {
            webAppShortcutRepository.update(
                shortcut, shortcut.label, shortcut.url, shortcut.iconUri, shortcut.faviconUrl,
                shortcut.rendererPackage, shortcut.showInGrid, true, nextOrder(), shortcut.iconSource,
                shortcut.customCss,
            )
        }
    }

    fun createAndAdd(
        label: String,
        url: String,
        iconUri: String?,
        faviconUrl: String?,
        rendererPackage: String?,
    ) {
        scope.launch {
            webAppShortcutRepository.create(
                label = label,
                url = url,
                iconUri = iconUri,
                faviconUrl = faviconUrl,
                rendererPackage = rendererPackage,
                showInGrid = true,
                showInPanel = true,
                order = nextOrder(),
                iconSource = WebAppShortcut.IconSource.Website,
            )
        }
    }

    /** Order value that places a newly added shortcut at the end of the panel. */
    private suspend fun nextOrder(): Int = (items.first().maxOfOrNull { it.order } ?: -1) + 1

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
            iconSource = existing.iconSource,
            customCss = existing.customCss,
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

    /** Rasterizes an icon-pack/system-icon pick from [de.mm20.launcher2.ui.common.IconPicker] into
     * a permanent PNG file, the same way [importIcon] does for an imported photo - so a web app
     * shortcut's `iconUri` can point at it just like any other custom icon. Baked with fixed
     * neutral colors rather than the current theme's, since this file is a one-time static
     * snapshot: baking today's theme colors into it would freeze them in place, defeating the
     * point of a themed icon for any icon-pack entry that relies on live theming. */
    suspend fun exportIconPackIcon(customIcon: CustomIcon?, sizePx: Int): String? = withContext(Dispatchers.IO) {
        val resolved = iconService.resolveCustomIcon(WebAppIconPickerTarget, sizePx, customIcon).first()
            ?: return@withContext null
        val staticIcon = when (resolved) {
            is StaticLauncherIcon -> resolved
            is DynamicLauncherIcon -> resolved.getIcon(System.currentTimeMillis())
            else -> return@withContext null
        }
        val bitmap = staticIcon.render(
            LauncherIconRenderSettings(
                size = sizePx,
                fgThemeColor = android.graphics.Color.BLACK,
                bgThemeColor = android.graphics.Color.WHITE,
                fgTone = 10,
                bgTone = 90,
            )
        )
        val file = File(context.filesDir, "webappshortcut_${UUID.randomUUID()}")
        FileOutputStream(file).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        file.absolutePath
    }
}
