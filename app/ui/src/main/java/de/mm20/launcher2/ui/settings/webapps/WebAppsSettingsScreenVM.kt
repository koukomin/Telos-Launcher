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
import de.mm20.launcher2.data.customattrs.CustomIcon
import de.mm20.launcher2.preferences.GestureAction
import de.mm20.launcher2.preferences.ui.GestureSettings
import de.mm20.launcher2.preferences.ui.WebAppBrowsingSettings
import de.mm20.launcher2.search.WebAppShortcut
import de.mm20.launcher2.ui.webappspanel.WebAppsPanelManager
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
    private val panelManager: WebAppsPanelManager by inject()

    val adBlockEnabled = browsingSettings.adBlockEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), true)

    fun setAdBlockEnabled(enabled: Boolean) = browsingSettings.setAdBlockEnabled(enabled)

    val zoomControlsEnabled = browsingSettings.zoomControlsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), true)

    fun setZoomControlsEnabled(enabled: Boolean) = browsingSettings.setZoomControlsEnabled(enabled)

    val trackingParamStrippingEnabled = browsingSettings.trackingParamStrippingEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), true)

    fun setTrackingParamStrippingEnabled(enabled: Boolean) =
        browsingSettings.setTrackingParamStrippingEnabled(enabled)

    val topBarAtBottom = browsingSettings.topBarAtBottom
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    fun setTopBarAtBottom(atBottom: Boolean) = browsingSettings.setTopBarAtBottom(atBottom)

    val swipeToSwitchEnabled = browsingSettings.swipeToSwitchEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), true)

    fun setSwipeToSwitchEnabled(enabled: Boolean) = browsingSettings.setSwipeToSwitchEnabled(enabled)

    val userAgentMode = browsingSettings.userAgentMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "default")

    fun setUserAgentMode(mode: String) = browsingSettings.setUserAgentMode(mode)

    val customUserAgent = browsingSettings.customUserAgent
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "")

    fun setCustomUserAgent(userAgent: String) =
        browsingSettings.setCustomUserAgent(WebAppUserAgents.sanitize(userAgent))

    val cookiesEnabled = browsingSettings.cookiesEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), true)

    fun setCookiesEnabled(enabled: Boolean) = browsingSettings.setCookiesEnabled(enabled)

    val thirdPartyCookiesEnabled = browsingSettings.thirdPartyCookiesEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    fun setThirdPartyCookiesEnabled(enabled: Boolean) =
        browsingSettings.setThirdPartyCookiesEnabled(enabled)

    /** Removes all cookies and web storage of every web app (all profiles). */
    fun clearCookiesAndSiteData(onDone: () -> Unit) {
        de.mm20.launcher2.ui.webapp.WebAppProfiles.clearAll(onDone)
    }

    val groupsEnabled = browsingSettings.groupsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    fun setGroupsEnabled(enabled: Boolean) = browsingSettings.setGroupsEnabled(enabled)

    val groups = browsingSettings.groups
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    fun createGroup(name: String) {
        viewModelScope.launch {
            val current = groups.value
            browsingSettings.setGroups(current + de.mm20.launcher2.preferences.WebAppGroup(id = UUID.randomUUID().toString(), name = name))
        }
    }

    fun deleteGroup(groupId: String) {
        viewModelScope.launch {
            browsingSettings.setGroups(groups.value.filter { it.id != groupId })
        }
    }

    fun updateGroup(group: de.mm20.launcher2.preferences.WebAppGroup) {
        viewModelScope.launch {
            browsingSettings.setGroups(groups.value.map { if (it.id == group.id) group else it })
        }
    }

    fun toggleGroupNotifications(groupId: String, enabled: Boolean) {
        viewModelScope.launch {
            val group = groups.value.find { it.id == groupId } ?: return@launch
            updateGroup(group.copy(notificationsEnabled = enabled))
        }
    }

    /**
     * Adds the [selected] apps of a predefined [category] as ordinary web app shortcuts (reusing
     * existing ones with the same URL) and puts them into a folder for that category (reusing the
     * folder if one was already created from it). [onDone] is called with the number of newly
     * created web apps.
     */
    fun addPreset(
        category: WebAppPresetCategory,
        selected: List<WebAppPreset>,
        onDone: (created: Int) -> Unit = {},
    ) {
        viewModelScope.launch {
            val existing = webAppShortcutRepository.search("", false).first()
            var created = 0
            val keys = mutableListOf<String>()
            for (preset in selected) {
                val wanted = WebAppPresets.comparableUrl(preset.url)
                val match = existing.find { WebAppPresets.comparableUrl(it.url) == wanted }
                if (match != null) {
                    keys.add(match.key)
                    continue
                }
                val favicon = withContext(Dispatchers.IO) { webAppShortcutRepository.findFavicon(preset.url) }
                val shortcut = webAppShortcutRepository.create(
                    label = preset.name,
                    url = preset.url,
                    iconUri = null,
                    faviconUrl = favicon,
                )
                keys.add(shortcut.key)
                created++
            }
            val current = browsingSettings.groups.first()
            val target = current.find { it.category == category.id }
            val updated = if (target != null) {
                current.map { g ->
                    if (g.id == target.id) g.copy(appKeys = (g.appKeys + keys).distinct())
                    else g.copy(appKeys = g.appKeys - keys.toSet())
                }
            } else {
                current.map { g -> g.copy(appKeys = g.appKeys - keys.toSet()) } +
                        de.mm20.launcher2.preferences.WebAppGroup(
                            id = UUID.randomUUID().toString(),
                            name = context.getString(category.nameRes),
                            appKeys = keys.distinct(),
                            category = category.id,
                        )
            }
            browsingSettings.setGroups(updated)
            browsingSettings.setGroupsEnabled(true)
            onDone(created)
        }
    }

    fun assignToGroup(shortcutKey: String, groupId: String?) {
        viewModelScope.launch {
            val currentGroups = groups.value.map { g ->
                if (g.id == groupId) {
                    if (!g.appKeys.contains(shortcutKey)) g.copy(appKeys = g.appKeys + shortcutKey) else g
                } else {
                    g.copy(appKeys = g.appKeys - shortcutKey)
                }
            }
            browsingSettings.setGroups(currentGroups)
        }
    }

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
        customCss: String?,
        notificationsEnabled: Boolean,
        groupId: String?,
        adBlockMode: WebAppShortcut.AdBlockMode,
        cookieOptions: WebAppShortcut.CookieOptions,
    ) {
        val oldIconUri = existing?.iconUri
        val shortcut = if (existing != null) {
            webAppShortcutRepository.update(
                existing, label, url, iconUri, faviconUrl, rendererPackage,
                showInGrid, showInPanel, existing.order, iconSource, customCss,
                notificationsEnabled, adBlockMode, cookieOptions,
            )
        } else {
            webAppShortcutRepository.create(
                label, url, iconUri, faviconUrl, rendererPackage,
                showInGrid, showInPanel, 0, iconSource, customCss,
                notificationsEnabled, adBlockMode, cookieOptions,
            )
        }
        assignToGroup(shortcut.key, groupId)
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
            shortcut.iconSource,
            shortcut.customCss,
            shortcut.notificationsEnabled,
        )
    }

    /** Deletes the web app everywhere: storage, search, panel and folders. */
    fun delete(shortcut: WebAppShortcut) {
        panelManager.delete(shortcut)
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

    suspend fun exportIconPackIcon(customIcon: CustomIcon?, sizePx: Int): String? =
        panelManager.exportIconPackIcon(customIcon, sizePx)
}
