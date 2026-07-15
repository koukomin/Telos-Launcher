package de.mm20.launcher2.ui.launcher.widgets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.widgets.Widget
import kotlinx.coroutines.launch

/**
 * Renders a "widget stack": several widgets sharing one home-screen slot,
 * swipeable (via [HorizontalPager]) with a chevron switcher as an
 * alternative to swiping. Delegates each page's content to the existing
 * [WidgetItem], so every widget type is rendered exactly like it would be
 * if it weren't stacked.
 */
@Composable
fun WidgetStackItem(
    widgets: List<Widget>,
    modifier: Modifier = Modifier,
    editMode: Boolean = false,
    onWidgetAdd: (widget: Widget, offset: Int) -> Unit = { _, _ -> },
    onWidgetUpdate: (widget: Widget) -> Unit = {},
    onWidgetRemove: (widget: Widget) -> Unit = {},
    onAddToStack: (target: Widget) -> Unit = {},
    onRemoveFromStack: (widget: Widget) -> Unit = {},
    draggableState: DraggableState = rememberDraggableState {},
    onDragStopped: () -> Unit = {},
) {
    // Reset pager state (including current page) whenever stack membership
    // changes, so a removed/added widget doesn't leave the pager pointing
    // at a stale index.
    key(widgets.map { it.id }) {
        val pagerState = rememberPagerState(initialPage = 0) { widgets.size }
        val scope = rememberCoroutineScope()
        val page = pagerState.currentPage.coerceIn(0, widgets.lastIndex)
        val widget = widgets[page]

        Column(modifier = modifier) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    enabled = page > 0,
                    onClick = { scope.launch { pagerState.animateScrollToPage(page - 1) } },
                ) {
                    Icon(painterResource(R.drawable.chevron_backward_24px), null)
                }
                TextButton(
                    modifier = Modifier.weight(1f),
                    onClick = {},
                    enabled = false,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = LocalContentColor.current,
                        disabledContentColor = LocalContentColor.current,
                    ),
                ) {
                    Text(
                        text = "${page + 1} / ${widgets.size}",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                IconButton(
                    enabled = page < widgets.lastIndex,
                    onClick = { scope.launch { pagerState.animateScrollToPage(page + 1) } },
                ) {
                    Icon(painterResource(R.drawable.chevron_forward_24px), null)
                }
                AnimatedVisibility(editMode) {
                    Row {
                        IconButton(onClick = { onAddToStack(widget) }) {
                            Icon(
                                painterResource(R.drawable.add_24px),
                                contentDescription = stringResource(R.string.widget_stack_action_add),
                            )
                        }
                        IconButton(onClick = { onRemoveFromStack(widget) }) {
                            Icon(
                                painterResource(R.drawable.link_off_24px),
                                contentDescription = stringResource(R.string.widget_stack_action_remove),
                            )
                        }
                    }
                }
            }
            HorizontalPager(
                modifier = Modifier.animateContentSize(),
                state = pagerState,
                verticalAlignment = Alignment.Top,
                // Stacks are usually small; don't waste memory/composition
                // pre-rendering pages the user may never swipe to.
                beyondViewportPageCount = 0,
            ) { pageIndex ->
                val pageWidget = widgets[pageIndex]
                WidgetItem(
                    widget = pageWidget,
                    editMode = editMode,
                    onWidgetAdd = onWidgetAdd,
                    onWidgetUpdate = onWidgetUpdate,
                    onWidgetRemove = { onWidgetRemove(pageWidget) },
                    draggableState = draggableState,
                    onDragStopped = onDragStopped,
                )
            }
        }
    }
}
