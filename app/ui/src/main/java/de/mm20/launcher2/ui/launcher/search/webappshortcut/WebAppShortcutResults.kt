package de.mm20.launcher2.ui.launcher.search.webappshortcut

import androidx.compose.foundation.lazy.LazyListScope
import de.mm20.launcher2.search.WebAppShortcut
import de.mm20.launcher2.ui.launcher.search.common.grid.GridItem
import de.mm20.launcher2.ui.launcher.search.common.grid.GridResults
import de.mm20.launcher2.ui.locals.LocalGridSettings

fun LazyListScope.WebAppShortcutResults(
    shortcuts: List<WebAppShortcut>,
    highlightedItem: WebAppShortcut?,
    columns: Int,
    reverse: Boolean,
) {
    GridResults(
        key = "webappshortcuts",
        items = shortcuts,
        itemContent = {
            GridItem(
                item = it,
                showLabels = LocalGridSettings.current.showLabels,
                highlight = it.key == highlightedItem?.key,
                enableFloatingLauncherDragSource = true,
            )
        },
        reverse = reverse,
        columns = columns,
    )
}
