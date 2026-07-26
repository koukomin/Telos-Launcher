package de.mm20.launcher2.icons.providers

import android.content.Context
import de.mm20.launcher2.icons.ColorLayer
import de.mm20.launcher2.icons.GridLayer
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.icons.StaticLauncherIcon
import de.mm20.launcher2.icons.TransparentLayer
import de.mm20.launcher2.search.Folder
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.searchable.SavableSearchableRepository
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent

class FolderIconProvider(
    private val context: Context,
    private val repository: SavableSearchableRepository,
    private val iconService: de.mm20.launcher2.icons.IconService,
    private val themed: Boolean,
) : IconProvider, KoinComponent {

    override suspend fun getIcon(searchable: SavableSearchable, size: Int): LauncherIcon? {
        if (searchable !is Folder) return null

        val childrenList: List<SavableSearchable> = repository.getByKeys(searchable.itemKeys).first()
        if (childrenList.isEmpty()) return searchable.getPlaceholderIcon(context)

        if (searchable.isCover) {
            return iconService.getIcon(childrenList.first(), size).first()
        }

        val iconLayers = mutableListOf<de.mm20.launcher2.icons.LauncherIconLayer>()
        for (child in childrenList) {
            val icon = iconService.getIcon(child, size / 2).first()
            if (icon is StaticLauncherIcon) {
                iconLayers.add(icon.foregroundLayer)
            }
            if (iconLayers.size >= 4) break
        }
        
        if (iconLayers.isEmpty()) return searchable.getPlaceholderIcon(context)

        return StaticLauncherIcon(
            foregroundLayer = GridLayer(iconLayers),
            backgroundLayer = TransparentLayer
        )
    }
}
