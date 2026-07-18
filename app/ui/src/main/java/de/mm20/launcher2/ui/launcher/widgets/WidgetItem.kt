package de.mm20.launcher2.ui.launcher.widgets

import android.appwidget.AppWidgetManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.zIndex
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.DragResizeHandle
import de.mm20.launcher2.ui.component.LauncherCard
import de.mm20.launcher2.ui.component.ResizeAxis
import de.mm20.launcher2.ui.launcher.sheets.ConfigureWidgetSheet
import de.mm20.launcher2.ui.launcher.widgets.ataglance.AtAGlanceWidget
import de.mm20.launcher2.ui.launcher.widgets.battery.BatteryWidget
import de.mm20.launcher2.ui.launcher.widgets.calendar.CalendarWidget
import de.mm20.launcher2.ui.launcher.widgets.external.AppWidget
import de.mm20.launcher2.ui.launcher.widgets.favorites.AppsWidget
import de.mm20.launcher2.ui.launcher.widgets.freeze.FreezeWidget
import de.mm20.launcher2.ui.launcher.widgets.music.MusicWidget
import de.mm20.launcher2.ui.launcher.widgets.network.NetworkWidget
import de.mm20.launcher2.ui.launcher.widgets.notes.NotesWidget
import de.mm20.launcher2.ui.launcher.widgets.plugin.PluginWidget as PluginWidgetContent
import de.mm20.launcher2.ui.launcher.widgets.reminders.RemindersWidget
import de.mm20.launcher2.ui.launcher.widgets.system.SystemWidget
import de.mm20.launcher2.ui.launcher.widgets.weather.WeatherWidget
import de.mm20.launcher2.ui.theme.transparency.transparency
import de.mm20.launcher2.widgets.AppWidget
import de.mm20.launcher2.widgets.AtAGlanceWidget
import de.mm20.launcher2.widgets.BatteryWidget
import de.mm20.launcher2.widgets.CalendarWidget
import de.mm20.launcher2.widgets.AppsWidget
import de.mm20.launcher2.widgets.FreezeWidget
import de.mm20.launcher2.widgets.MusicWidget
import de.mm20.launcher2.widgets.NetworkWidget
import de.mm20.launcher2.widgets.NotesWidget
import de.mm20.launcher2.widgets.RemindersWidget
import de.mm20.launcher2.widgets.SystemWidget
import de.mm20.launcher2.widgets.WeatherWidget
import de.mm20.launcher2.widgets.Widget
import de.mm20.launcher2.widgets.height
import de.mm20.launcher2.widgets.withHeight
import kotlin.math.roundToInt

@Composable
fun WidgetItem(
    widget: Widget,
    modifier: Modifier = Modifier,
    editMode: Boolean = false,
    onWidgetAdd: (widget: Widget, offset: Int) -> Unit = { _, _ -> },
    onWidgetUpdate: (widget: Widget) -> Unit = {},
    onWidgetRemove: () -> Unit = {},
    onAddToStack: (() -> Unit)? = null,
    draggableState: DraggableState = rememberDraggableState {},
    onDragStopped: () -> Unit = {}
) {
    val context = LocalContext.current

    var configure by rememberSaveable { mutableStateOf(false) }

    var isDragged by remember { mutableStateOf(false) }
    val elevation by animateDpAsState(if (isDragged) 8.dp else 0.dp)

    val backgroundOpacity by animateFloatAsState(
        if (widget is AppWidget && !widget.config.background && !editMode) 0f else MaterialTheme.transparency.surface,
        label = "widgetCardBackgroundOpacity",
    )

    LauncherCard(
        modifier = modifier.zIndex(if (isDragged) 1f else 0f),
        elevation = elevation,
        backgroundOpacity = backgroundOpacity,
    ) {
        Column {
            AnimatedVisibility(editMode) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painterResource(R.drawable.drag_indicator_24px),
                        contentDescription = null,
                        modifier = Modifier.draggable(
                            state = draggableState,
                            orientation = Orientation.Vertical,
                            startDragImmediately = true,
                            onDragStarted = {
                                isDragged = true
                            },
                            onDragStopped = {
                                isDragged = false
                                onDragStopped()
                            }
                        )
                    )
                    Text(
                        text = widget.getLabel(LocalContext.current),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1
                    )
                    IconButton(onClick = {
                        configure = true
                    }) {
                        Icon(
                            painterResource(R.drawable.tune_24px),
                            contentDescription = stringResource(R.string.settings)
                        )
                    }
                    if (onAddToStack != null) {
                        IconButton(onClick = onAddToStack) {
                            Icon(
                                painterResource(R.drawable.add_24px),
                                contentDescription = stringResource(R.string.widget_stack_action_add)
                            )
                        }
                    }
                    IconButton(onClick = { onWidgetRemove() }) {
                        Icon(
                            painterResource(R.drawable.delete_24px),
                            contentDescription = stringResource(R.string.widget_action_remove)
                        )
                    }
                }
            }
            var resizeHeight by remember(widget.id) {
                mutableStateOf(widget.height?.dp ?: Dp.Unspecified)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (editMode) Modifier.padding(bottom = 32.dp) else Modifier),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (resizeHeight.isUnspecified) Modifier
                            else Modifier.height(resizeHeight).clipToBounds()
                        ),
                ) {
                    when (widget) {
                        is WeatherWidget -> {
                            WeatherWidget(widget)
                        }

                        is MusicWidget -> {
                            MusicWidget(widget)
                        }

                        is CalendarWidget -> {
                            CalendarWidget(widget)
                        }

                        is AppsWidget -> {
                            AppsWidget(widget)
                        }

                        is NotesWidget -> {
                            NotesWidget(
                                widget,
                                onWidgetAdd = onWidgetAdd,
                            )
                        }

                        is BatteryWidget -> {
                            BatteryWidget(widget)
                        }

                        is NetworkWidget -> {
                            NetworkWidget(widget)
                        }

                        is SystemWidget -> {
                            SystemWidget(widget)
                        }

                        is RemindersWidget -> {
                            RemindersWidget(widget)
                        }

                        is FreezeWidget -> {
                            FreezeWidget(widget)
                        }

                        is AppWidget -> {
                            AppWidget(
                                widget,
                                onWidgetUpdate = onWidgetUpdate,
                                onWidgetRemove = onWidgetRemove,
                            )
                        }

                        is de.mm20.launcher2.widgets.PluginWidget -> {
                            PluginWidgetContent(widget)
                        }

                        is AtAGlanceWidget -> {
                            AtAGlanceWidget(widget)
                        }
                    }
                }
                if (editMode) {
                    DragResizeHandle(
                        resizeAxis = ResizeAxis.Vertical,
                        alignment = Alignment.BottomCenter,
                        height = resizeHeight,
                        minHeight = 56.dp,
                        maxHeight = 1200.dp,
                        snapToMeasuredHeight = true,
                        onResize = { _, h -> resizeHeight = h },
                        onResizeStopped = {
                            onWidgetUpdate(
                                widget.withHeight(
                                    resizeHeight.takeIf { !it.isUnspecified }?.value?.roundToInt()
                                )
                            )
                        },
                    )
                }
            }
        }
    }
    ConfigureWidgetSheet(
        expanded = configure,
        widget = widget,
        onWidgetUpdated = onWidgetUpdate,
        onDismiss = { configure = false },
    )
}
