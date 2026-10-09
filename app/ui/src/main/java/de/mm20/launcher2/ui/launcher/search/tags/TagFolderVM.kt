package de.mm20.launcher2.ui.launcher.search.tags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.icons.IconService
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.services.favorites.FavoritesService
import de.mm20.launcher2.services.tags.TagsService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Backs the "folder" popup shown when tapping a pinned [de.mm20.launcher2.search.Tag] icon:
 * every tag doubles as a folder of the items tagged with it.
 */
class TagFolderVM : ViewModel(), KoinComponent {
    private val tagsService: TagsService by inject()
    private val iconService: IconService by inject()
    private val favoritesService: FavoritesService by inject()

    private var tag: String? = null

    val items = kotlinx.coroutines.flow.MutableStateFlow<List<SavableSearchable>>(emptyList())

    fun init(tag: String) {
        if (this.tag == tag) return
        this.tag = tag
        viewModelScope.launch {
            tagsService.getTaggedItems(tag).collect {
                items.value = it.distinctBy { item -> item.key }
            }
        }
    }

    fun getIcon(item: SavableSearchable, size: Int): Flow<LauncherIcon?> {
        return iconService.getIcon(item, size)
    }

    fun launch(context: android.content.Context, item: SavableSearchable) {
        if (item.launch(context, null)) {
            favoritesService.reportLaunch(item)
        }
    }
}
