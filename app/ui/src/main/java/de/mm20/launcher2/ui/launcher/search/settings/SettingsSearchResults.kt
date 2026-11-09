package de.mm20.launcher2.ui.launcher.search.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.Modifier
import de.mm20.launcher2.search.SettingsSearchItem
import de.mm20.launcher2.ui.launcher.search.common.ShowAllButton
import de.mm20.launcher2.ui.launcher.search.common.list.ListItem
import de.mm20.launcher2.ui.launcher.search.common.list.ListResults
import kotlin.math.min

fun LazyListScope.SettingsSearchResults(
    items: List<SettingsSearchItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    reverse: Boolean,
    truncate: Boolean,
    onShowAll: () -> Unit,
) {
    if (items.isEmpty()) return
    ListResults(
        items = items.subList(0, if (truncate) min(6, items.size) else items.size),
        key = "settings-search",
        reverse = reverse,
        selectedIndex = selectedIndex,
        itemContent = { item, showDetails, index ->
            ListItem(
                modifier = Modifier.fillMaxWidth(),
                item = item,
                showDetails = showDetails,
                onShowDetails = { onSelect(if (it) index else -1) },
            )
        },
        after = if (truncate && items.size > 6) {
            {
                ShowAllButton(onShowAll = onShowAll)
            }
        } else null,
    )
}
