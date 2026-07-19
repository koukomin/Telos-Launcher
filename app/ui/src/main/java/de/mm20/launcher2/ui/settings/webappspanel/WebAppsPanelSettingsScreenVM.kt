package de.mm20.launcher2.ui.settings.webappspanel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.preferences.GestureAction
import de.mm20.launcher2.preferences.ui.GestureSettings
import de.mm20.launcher2.search.WebAppShortcut
import de.mm20.launcher2.ui.webappspanel.WebAppsPanelManager
import de.mm20.launcher2.webappshortcuts.WebAppShortcutRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

enum class PanelDirection { Left, Right }

class WebAppsPanelSettingsScreenVM : ViewModel(), KoinComponent {
    private val manager: WebAppsPanelManager by inject()
    private val gestureSettings: GestureSettings by inject()
    private val webAppShortcutRepository: WebAppShortcutRepository by inject()

    val items = manager.items.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    val availableToAdd = combine(
        items,
        webAppShortcutRepository.search("", false),
    ) { panelItems, all ->
        val panelKeys = panelItems.map { it.key }.toSet()
        all.filterNot { it.key in panelKeys }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

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

    fun moveItem(from: Int, to: Int) {
        val current = items.value.map { it.key }.toMutableList()
        if (from !in current.indices || to !in current.indices) return
        val item = current.removeAt(from)
        current.add(to, item)
        manager.setOrder(current)
    }

    fun remove(shortcut: WebAppShortcut) = manager.remove(shortcut)

    fun addExisting(shortcut: WebAppShortcut) = manager.addExisting(shortcut)

    fun createAndAdd(label: String, url: String, iconUri: String?, faviconUrl: String?, rendererPackage: String?) {
        manager.createAndAdd(label, url, iconUri, faviconUrl, rendererPackage)
    }

    fun update(existing: WebAppShortcut, label: String, url: String, iconUri: String?, faviconUrl: String?, rendererPackage: String?) {
        manager.update(existing, label, url, iconUri, faviconUrl, rendererPackage)
    }

    suspend fun findFavicon(url: String): String? = manager.findFavicon(url)

    suspend fun importIcon(uri: Uri, sizePx: Int): String? = manager.importIcon(uri, sizePx)
}
