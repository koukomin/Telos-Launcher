package de.mm20.launcher2.ui.launcher.widgets.favorites
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.search.Tag
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.common.FavoritesTagSelector
import de.mm20.launcher2.ui.component.Banner
import de.mm20.launcher2.ui.launcher.search.common.grid.SearchResultGrid
import de.mm20.launcher2.widgets.AppsWidget

@Composable
fun AppsWidget(widget: AppsWidget) {
    val viewModel: AppsWidgetVM = viewModel(key = "favorites-widget-${widget.id}")
    val favorites by remember { viewModel.favorites }.collectAsState(emptyList())
    val pinnedTags by viewModel.pinnedTags.collectAsState(emptyList())
    val selectedTag by viewModel.selectedTag.collectAsState(null)

    val customTags = widget.config.customTags && widget.config.tagList.isNotEmpty()
    val favoritesEditButton = !customTags && widget.config.editButton

    val compactTags = widget.config.compactTags
    val tagsExpanded = widget.config.tagsMultiline

    LaunchedEffect(widget) {
        viewModel.updateWidget(widget)
    }

    Column(
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        if (favorites.isNotEmpty()) {
            SearchResultGrid(
                favorites,
                transitionKey = selectedTag,
                enableShutterGesture = true,
                enableFloatingLauncherDragSource = true,
            )
        } else {
            Banner(
                modifier = Modifier.padding(16.dp),
                text = stringResource(
                    if (selectedTag == null) I18nR.string.favorites_empty else I18nR.string.favorites_empty_tag
                ),
                icon = if (selectedTag == null) BaseR.drawable.star_24px else BaseR.drawable.tag_24px,
            )
        }
        if (favoritesEditButton || (!customTags && pinnedTags.isNotEmpty()) || (customTags && widget.config.tagList.size > 1)) {
            FavoritesTagSelector(
                tags = if (customTags) widget.config.tagList.map { Tag(it) } else pinnedTags,
                selectedTag = selectedTag,
                editButton = favoritesEditButton,
                reverse = false,
                onSelectTag = { viewModel.selectTag(it) },
                scrollState = rememberScrollState(),
                expanded = tagsExpanded,
                compact = compactTags,
                onExpand = { viewModel.setTagsExpanded(it) },
                showFavorites = !customTags
            )
        }
    }
}