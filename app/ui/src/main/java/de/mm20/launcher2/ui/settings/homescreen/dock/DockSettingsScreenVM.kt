package de.mm20.launcher2.ui.settings.homescreen.dock

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.applications.AppRepository
import de.mm20.launcher2.icons.IconService
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.preferences.DockItem
import de.mm20.launcher2.preferences.ui.UiSettings
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.searchable.SavableSearchableRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class DockSettingsScreenVM : ViewModel(), KoinComponent {
    private val context: Context by inject()
    private val uiSettings: UiSettings by inject()
    private val searchableRepository: SavableSearchableRepository by inject()
    private val appRepository: AppRepository by inject()
    private val iconService: IconService by inject()

    val dockPages = uiSettings.dockPages.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())
    val dockRows = uiSettings.dockRows.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), 1)
    val dockColumns = uiSettings.dockColumns.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), 5)
    val defaultPage = uiSettings.dockDefaultPage.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), 0)
    val gridSettings = uiSettings.gridSettings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val dockBackgroundEnabled = uiSettings.dockBackgroundEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    fun setDockBackgroundEnabled(enabled: Boolean) = uiSettings.setDockBackgroundEnabled(enabled)

    val dockBackgroundColor = uiSettings.dockBackgroundColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setDockBackgroundColor(color: Int?) = uiSettings.setDockBackgroundColor(color)

    val dockBackgroundOpacity = uiSettings.dockBackgroundOpacity
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), 0.3f)

    fun setDockBackgroundOpacity(opacity: Float) = uiSettings.setDockBackgroundOpacity(opacity)

    val dockBackgroundShadow = uiSettings.dockBackgroundShadow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), 0)

    fun setDockBackgroundShadow(elevation: Int) = uiSettings.setDockBackgroundShadow(elevation)

    val dockPageIndicatorEnabled = uiSettings.dockPageIndicatorEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), true)

    fun setDockPageIndicatorEnabled(enabled: Boolean) = uiSettings.setDockPageIndicatorEnabled(enabled)

    val dockPageIndicatorColor = uiSettings.dockPageIndicatorColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setDockPageIndicatorColor(color: Int?) = uiSettings.setDockPageIndicatorColor(color)

    var pendingItemPos by mutableStateOf<Triple<Int, Int, Int>?>(null) // page, row, col

    fun setDockPages(pages: List<List<DockItem>>) {
        uiSettings.setDockPages(pages)
    }

    fun setRows(rows: Int) {
        uiSettings.setDockRows(rows)
    }

    fun setColumns(columns: Int) {
        uiSettings.setDockColumns(columns)
    }

    fun setDefaultPage(page: Int) {
        uiSettings.setDockDefaultPage(page)
    }

    /** Switches from auto-favorites to the custom dock. ONLY this first dock is seeded with
     * default apps; any further docks (via [setDockCount]) always start empty. */
    fun enableCustomDock() {
        viewModelScope.launch {
            if (dockPages.value.isNotEmpty()) return@launch
            uiSettings.setDockPages(listOf(resolveDefaultApps()))
        }
    }

    private suspend fun resolveDefaultApps(): List<DockItem> {
        val intents = listOf(
            Intent(Intent.ACTION_DIAL), // Dialer
            Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:")), // SMS
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER), // Browser
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY), // Image Viewer
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC) // Media Player
        )
        
        val pm = context.packageManager
        val items = mutableListOf<DockItem>()
        val columns = dockColumns.value
        
        for (intent in intents) {
            val resolveInfo = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            if (resolveInfo != null) {
                val pkg = resolveInfo.activityInfo.packageName
                val activity = resolveInfo.activityInfo.name
                val key = "app://$pkg:$activity"
                
                // Try to find the searchable object and upsert it to ensure it's in DB
                appRepository.findOne(pkg, Process.myUserHandle()).first()?.let {
                    searchableRepository.upsert(it)
                }
                
                items.add(DockItem.Searchable(key))
            }
            if (items.size >= columns) break
        }
        
        return items
    }

    /** Grows or shrinks the number of docks. New docks are always added EMPTY - default apps
     * are only ever auto-resolved for the very first dock; extra docks are filled manually. */
    fun setDockCount(count: Int) {
        val current = dockPages.value.toMutableList()
        if (current.isEmpty()) return
        val target = count.coerceIn(1, MAX_DOCKS)
        while (current.size < target) current.add(emptyList())
        while (current.size > target) current.removeAt(current.size - 1)
        uiSettings.setDockPages(current)
        if (defaultPage.value >= target) {
            setDefaultPage(target - 1)
        }
    }

    companion object {
        const val MAX_DOCKS = 5
    }

    fun setItem(page: Int, row: Int, col: Int, item: DockItem?, searchable: SavableSearchable? = null) {
        val current = dockPages.value.toMutableList()
        while (current.size <= page) current.add(emptyList())
        
        val pageItems = current[page].toMutableList()
        val cols = dockColumns.value
        val rows = dockRows.value
        val requiredSize = rows * cols
        while (pageItems.size < requiredSize) pageItems.add(DockItem.Searchable(""))
        
        val index = row * cols + col
        
        if (item != null) {
            pageItems[index] = item
            if (searchable != null) {
                viewModelScope.launch {
                    searchableRepository.upsert(searchable)
                }
            }
        } else {
            pageItems[index] = DockItem.Searchable("") 
        }
        
        current[page] = pageItems
        uiSettings.setDockPages(current)
    }

    fun moveItem(page: Int, from: Int, to: Int) {
        val current = dockPages.value.toMutableList()
        if (page !in current.indices) return
        val pageItems = current[page].toMutableList()
        if (from !in pageItems.indices || to !in pageItems.indices) return

        val item = pageItems.removeAt(from)
        pageItems.add(to, item)
        current[page] = pageItems
        uiSettings.setDockPages(current)
    }

    fun getSearchable(key: String): Flow<SavableSearchable?> {
        return searchableRepository.getByKeys(listOf(key)).map { it.firstOrNull() }
    }

    fun getIcon(searchable: SavableSearchable, size: Int): Flow<LauncherIcon?> {
        return iconService.getIcon(searchable, size)
    }
}
