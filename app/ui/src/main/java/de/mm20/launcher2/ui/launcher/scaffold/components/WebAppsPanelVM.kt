package de.mm20.launcher2.ui.launcher.scaffold.components

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.data.customattrs.CustomIcon
import de.mm20.launcher2.preferences.WebAppGroup
import de.mm20.launcher2.preferences.ui.WebAppBrowsingSettings
import de.mm20.launcher2.search.WebAppShortcut
import de.mm20.launcher2.ui.webappspanel.WebAppsPanelManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** One section of the panel grid: a named group (or null for shortcuts with no group) and its
 * member shortcuts, in panel order. */
data class WebAppPanelSection(
    val group: WebAppGroup?,
    val items: List<WebAppShortcut>,
)

class WebAppsPanelVM : ViewModel(), KoinComponent {
    private val manager: WebAppsPanelManager by inject()
    private val browsingSettings: WebAppBrowsingSettings by inject()

    val items = manager.items.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    /** [items] split into per-group sections, in the same order as the Web Apps settings screen's
     * group list, for display in the panel grid - mirrors the grouping WebAppActivity already
     * uses to restrict swipe-to-switch, just made visible here too. Null (still flat) unless
     * grouping is enabled, so the panel keeps its plain single-grid layout otherwise. */
    val sections = combine(
        items,
        browsingSettings.groupsEnabled,
        browsingSettings.groups,
    ) { allItems, groupsEnabled, groups ->
        if (!groupsEnabled || groups.isEmpty()) return@combine null
        val sections = mutableListOf<WebAppPanelSection>()
        for (group in groups) {
            val groupItems = allItems.filter { group.appKeys.contains(it.key) }
            if (groupItems.isNotEmpty()) sections.add(WebAppPanelSection(group, groupItems))
        }
        val grouped = groups.flatMap { it.appKeys }.toSet()
        val ungrouped = allItems.filterNot { grouped.contains(it.key) }
        if (ungrouped.isNotEmpty()) sections.add(WebAppPanelSection(null, ungrouped))
        sections
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

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
