package de.mm20.launcher2.ui.launcher.search.common.grid

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.ui.layout.BottomReversed
import de.mm20.launcher2.ui.locals.LocalGridSettings
import kotlin.math.ceil

@Composable
fun SearchResultGrid(
    items: List<SavableSearchable>,
    modifier: Modifier = Modifier,
    showLabels: Boolean = LocalGridSettings.current.showLabels,
    columns: Int = LocalGridSettings.current.columnCount,
    reverse: Boolean = false,
    highlightedItem: SavableSearchable? = null,
    transitionKey: Any? = items,
    enableShutterGesture: Boolean = false,
    enableFloatingLauncherDragSource: Boolean = false,
    /** When true, rows with fewer items than [columns] center the group of icons instead of
     * left-aligning them with trailing blank cells. Used by the dock, where a partially-filled
     * row should read as centered, not stuck to the start. */
    centerRows: Boolean = false,
) {
    AnimatedContent(
        items to transitionKey,
        modifier = modifier
            .fillMaxWidth()
            .padding(4.dp),
        transitionSpec = {
            fadeIn() togetherWith fadeOut()
        },
        contentKey = { it.second }
    ) { (items, _) ->
        Column(
            verticalArrangement = if (reverse) Arrangement.BottomReversed else Arrangement.Top
        ) {
            for (i in 0 until ceil(items.size / columns.toFloat()).toInt()) {
                if (centerRows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        for (j in 0 until columns) {
                            val item = items.getOrNull(i * columns + j) ?: continue
                            key(item.key) {
                                GridItem(
                                    item = item,
                                    showLabels = showLabels,
                                    highlight = item.key == highlightedItem?.key,
                                    enableShutterGesture = enableShutterGesture,
                                    enableFloatingLauncherDragSource = enableFloatingLauncherDragSource,
                                )
                            }
                        }
                    }
                } else {
                    Row {
                        for (j in 0 until columns) {
                            val item = items.getOrNull(i * columns + j)
                            if (item != null) {
                                key(item.key) {
                                    GridItem(
                                        modifier = Modifier
                                            .weight(1f),
                                        item = item,
                                        showLabels = showLabels,
                                        highlight = item.key == highlightedItem?.key,
                                        enableShutterGesture = enableShutterGesture,
                                        enableFloatingLauncherDragSource = enableFloatingLauncherDragSource,
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}
