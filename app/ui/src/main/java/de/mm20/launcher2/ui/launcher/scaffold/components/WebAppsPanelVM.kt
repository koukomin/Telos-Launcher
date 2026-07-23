package de.mm20.launcher2.ui.launcher.scaffold.components

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.data.customattrs.CustomIcon
import de.mm20.launcher2.search.WebAppShortcut
import de.mm20.launcher2.ui.webappspanel.WebAppsPanelManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class WebAppsPanelVM : ViewModel(), KoinComponent {
    private val manager: WebAppsPanelManager by inject()

    val items = manager.items.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    /** Registered web apps not on the panel yet, offered by the edit mode for adding. */
    val availableToAdd =
        manager.availableToAdd.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    fun moveItem(from: Int, to: Int) {
        val current = items.value.toMutableList()
        if (from !in current.indices || to !in current.indices) return
        val item = current.removeAt(from)
        current.add(to, item)
        manager.setOrder(current)
    }

    fun remove(shortcut: WebAppShortcut) {
        manager.remove(shortcut)
    }

    fun addExisting(shortcut: WebAppShortcut) {
        manager.addExisting(shortcut)
    }

    fun createAndAdd(label: String, url: String, iconUri: String?, faviconUrl: String?, rendererPackage: String?) {
        manager.createAndAdd(label, url, iconUri, faviconUrl, rendererPackage)
    }

    suspend fun findFavicon(url: String): String? = manager.findFavicon(url)

    suspend fun importIcon(uri: Uri, sizePx: Int): String? = manager.importIcon(uri, sizePx)

    suspend fun exportIconPackIcon(customIcon: CustomIcon?, sizePx: Int): String? =
        manager.exportIconPackIcon(customIcon, sizePx)
}
