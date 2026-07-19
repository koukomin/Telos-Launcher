package de.mm20.launcher2.ui.launcher.search.common.grid

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import de.mm20.launcher2.preferences.DockItem
import de.mm20.launcher2.search.SavableSearchable

@Composable
fun DockGridItem(
    modifier: Modifier = Modifier,
    item: DockItem,
    showLabels: Boolean = false,
    enableShutterGesture: Boolean = false,
    enableFloatingLauncherDragSource: Boolean = false,
    // Add parameters to resolve searchables
) {
    // This will be implemented to handle both searchables and widgets
}
