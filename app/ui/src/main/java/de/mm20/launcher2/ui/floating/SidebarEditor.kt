package de.mm20.launcher2.ui.floating

import de.mm20.launcher2.search.GreekFold
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.applications.AppRepository
import de.mm20.launcher2.icons.IconService
import de.mm20.launcher2.preferences.ui.FloatingLauncherSettings
import de.mm20.launcher2.preferences.ui.ShutterSettings
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.dragndrop.DraggableItem
import de.mm20.launcher2.ui.component.dragndrop.LazyVerticalDragAndDropGrid
import de.mm20.launcher2.ui.component.dragndrop.rememberLazyDragAndDropGridState
import de.mm20.launcher2.widgets.AppsWidget
import de.mm20.launcher2.widgets.AtAGlanceWidget
import de.mm20.launcher2.widgets.BatteryWidget
import de.mm20.launcher2.widgets.CalendarWidget
import de.mm20.launcher2.widgets.FreezeWidget
import de.mm20.launcher2.widgets.MusicWidget
import de.mm20.launcher2.widgets.NetworkWidget
import de.mm20.launcher2.widgets.NotesWidget
import de.mm20.launcher2.widgets.RemindersWidget
import de.mm20.launcher2.widgets.SystemWidget
import de.mm20.launcher2.widgets.WeatherWidget
import de.mm20.launcher2.widgets.Widget
import de.mm20.launcher2.widgets.WidgetRepository
import org.koin.compose.koinInject
import java.util.UUID

/** The widgets of the launcher that can be put into the sidebar, with the icon the widget picker uses */
private val SIDEBAR_WIDGET_TYPES = listOf(
    WeatherWidget.Type to R.drawable.light_mode_24px,
    CalendarWidget.Type to R.drawable.today_24px,
    MusicWidget.Type to R.drawable.music_note_24px,
    NotesWidget.Type to R.drawable.sticky_note_2_24px,
    BatteryWidget.Type to R.drawable.battery_full_24px,
    AtAGlanceWidget.Type to R.drawable.visibility_24px,
    RemindersWidget.Type to R.drawable.task_alt_24px,
    NetworkWidget.Type to R.drawable.wifi_24px,
    SystemWidget.Type to R.drawable.memory_24px,
    FreezeWidget.Type to R.drawable.ac_unit_24px,
    AppsWidget.Type to R.drawable.apps_24px,
)

private fun newSidebarWidget(type: String): Widget? {
    val id = UUID.randomUUID()
    return when (type) {
        WeatherWidget.Type -> WeatherWidget(id)
        CalendarWidget.Type -> CalendarWidget(id)
        MusicWidget.Type -> MusicWidget(id)
        NotesWidget.Type -> NotesWidget(id)
        BatteryWidget.Type -> BatteryWidget(id)
        AtAGlanceWidget.Type -> AtAGlanceWidget(id)
        RemindersWidget.Type -> RemindersWidget(id)
        NetworkWidget.Type -> NetworkWidget(id)
        SystemWidget.Type -> SystemWidget(id)
        FreezeWidget.Type -> FreezeWidget(id)
        AppsWidget.Type -> AppsWidget(id)
        else -> null
    }
}

/**
 * The editor of the sidebar, like "Edit" in the Smart Sidebar: on the left what can be added
 * (tools, widgets, apps) with a search field, on the right the sidebar as it is, where an item is
 * removed with its minus and moved by dragging.
 */
@Composable
internal fun SidebarEditor(
    entries: List<SidebarEntry>,
    settings: FloatingLauncherSettings,
    iconService: IconService,
    appRepository: AppRepository,
    widgetRepository: WidgetRepository,
    onDone: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val style = LocalSidebarStyle.current
    val shutterSettings: ShutterSettings = koinInject()
    var query by remember { mutableStateOf("") }
    val apps by remember { appRepository.findMany() }.collectAsState(emptyList())
    val sortedApps = remember(apps) { apps.sortedBy { (it.labelOverride ?: it.label).lowercase() } }
    val addedKeys = remember(entries) { entries.map { it.key }.toSet() }
    val q = query.trim().lowercase()

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(enabled = false) {},
    ) {
        Row(Modifier.fillMaxSize().padding(top = 40.dp, bottom = 96.dp)) {
            // what can be added
            Column(Modifier.weight(1f).fillMaxHeight().padding(start = 20.dp, end = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.floating_launcher_edit),
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            painterResource(R.drawable.settings_24px),
                            contentDescription = stringResource(R.string.floating_launcher_panel_settings),
                            tint = Color.White,
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White.copy(alpha = 0.16f),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.search_24px), contentDescription = null, tint = Color.White.copy(alpha = 0.8f))
                        Box(Modifier.padding(start = 12.dp).weight(1f)) {
                            if (query.isEmpty()) {
                                Text(
                                    stringResource(R.string.floating_launcher_search_hint),
                                    color = Color.White.copy(alpha = 0.6f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            BasicTextField(
                                value = query,
                                onValueChange = { query = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                                cursorBrush = SolidColor(Color.White),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                CompositionLocalProvider(LocalSidebarStyle provides style.copy(showLabels = true)) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        val tools = SidebarTool.entries.filter { q.isEmpty() || GreekFold.contains(context.getString(it.label), q) }
                        if (tools.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle(R.string.floating_launcher_section_tools) }
                            items(tools, key = { "t-" + it.id }) { tool ->
                                val added = tool.key in addedKeys
                                AddableTile(added = added, onAdd = { settings.addItem(tool.key) }) {
                                    SidebarTile(stringResource(tool.label), {}) { SidebarToolIcon(tool, style.iconSize) }
                                }
                            }
                        }
                        val widgetTypes = SIDEBAR_WIDGET_TYPES.mapNotNull { (type, icon) ->
                            val widget = newSidebarWidget(type) ?: return@mapNotNull null
                            val label = widget.getLabel(context)
                            if (q.isNotEmpty() && !GreekFold.contains(label, q)) null else Triple(type, icon, label)
                        }
                        if (widgetTypes.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle(R.string.floating_launcher_section_widgets) }
                            items(widgetTypes, key = { "w-" + it.first }) { (type, icon, label) ->
                                AddableTile(added = false, onAdd = {
                                    val widget = newSidebarWidget(type) ?: return@AddableTile
                                    widgetRepository.create(widget, position = 0, parentId = SIDEBAR_WIDGET_PARENT)
                                    settings.addItem(WIDGET_KEY_PREFIX + widget.id)
                                }) {
                                    SidebarTile(label, {}) { WidgetTypeIcon(icon, style.iconSize) }
                                }
                            }
                        }
                        val shownApps = sortedApps.filter { q.isEmpty() || GreekFold.contains(it.labelOverride ?: it.label, q) }
                        if (shownApps.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle(R.string.floating_launcher_section_apps) }
                            items(shownApps, key = { "a-" + it.key }) { app ->
                                val added = app.key in addedKeys
                                AddableTile(added = added, onAdd = { settings.addItem(app.key) }) {
                                    FavoriteIcon(
                                        item = app,
                                        iconService = iconService,
                                        appRepository = appRepository,
                                        shutterSettings = shutterSettings,
                                        editMode = false,
                                        onClick = {},
                                        onRemove = {},
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // the sidebar as it is now
            EditorCard(entries, settings, iconService, appRepository, shutterSettings, widgetRepository)
        }

        Surface(
            onClick = onDone,
            shape = RoundedCornerShape(50),
            color = Color.White.copy(alpha = 0.22f),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp).width(220.dp),
        ) {
            Text(
                stringResource(R.string.floating_launcher_done),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 16.dp).fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SectionTitle(label: Int) {
    Text(
        stringResource(label),
        style = MaterialTheme.typography.titleMedium,
        color = Color.White,
        modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun WidgetTypeIcon(icon: Int, size: Dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(size * 0.5f))
    }
}

/** A tile with a plus badge. Already added items are dimmed and cannot be added again. */
@Composable
private fun AddableTile(added: Boolean, onAdd: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .alpha(if (added) 0.4f else 1f)
            .clickable(enabled = !added, onClick = onAdd),
        contentAlignment = Alignment.TopCenter,
    ) {
        content()
        if (!added) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 12.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.add_24px), contentDescription = stringResource(R.string.floating_launcher_add), tint = Color.Black, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun EditorCard(
    entries: List<SidebarEntry>,
    settings: FloatingLauncherSettings,
    iconService: IconService,
    appRepository: AppRepository,
    shutterSettings: ShutterSettings,
    widgetRepository: WidgetRepository,
) {
    val style = LocalSidebarStyle.current
    val columns = style.columns
    val dragState = rememberLazyDragAndDropGridState(
        onItemMove = { from, to ->
            val current = entries.map { it.key }.toMutableList()
            val fromIndex = current.indexOf(from.key)
            val toIndex = current.indexOf(to.key)
            if (fromIndex != -1 && toIndex != -1) {
                current.add(toIndex, current.removeAt(fromIndex))
                settings.setItems(current)
            }
        },
    )
    Surface(
        modifier = Modifier
            .padding(end = 8.dp)
            .width(sidebarCell() * columns + 24.dp)
            .fillMaxHeight(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
        border = androidx.compose.foundation.BorderStroke(2.dp, Color.White.copy(alpha = 0.6f)),
    ) {
        CompositionLocalProvider(LocalSidebarStyle provides style.copy(showLabels = true)) {
            LazyVerticalDragAndDropGrid(
                state = dragState,
                columns = GridCells.Fixed(columns),
                contentPadding = PaddingValues(12.dp),
            ) {
                items(entries, key = { it.key }) { entry ->
                    DraggableItem(state = dragState, key = entry.key) {
                        when (entry) {
                            is SidebarEntry.App -> FavoriteIcon(
                                item = entry.item,
                                iconService = iconService,
                                appRepository = appRepository,
                                shutterSettings = shutterSettings,
                                editMode = true,
                                onClick = {},
                                onRemove = { settings.removeItem(entry.key) },
                            )

                            is SidebarEntry.Tool -> RemovableTile(onRemove = { settings.removeItem(entry.key) }) {
                                SidebarTile(stringResource(entry.tool.label), {}) { SidebarToolIcon(entry.tool, style.iconSize) }
                            }

                            is SidebarEntry.WidgetEntry -> RemovableTile(onRemove = {
                                settings.removeItem(entry.key)
                                widgetRepository.delete(entry.widget)
                            }) {
                                val icon = widgetIcon(entry.widget)
                                SidebarTile(entry.widget.getLabel(LocalContext.current), {}) { WidgetTypeIcon(icon, style.iconSize) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A tile with a minus badge */
@Composable
private fun RemovableTile(onRemove: () -> Unit, content: @Composable () -> Unit) {
    Box(contentAlignment = Alignment.TopCenter) {
        content()
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 2.dp, end = 4.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(Color.White)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(width = 12.dp, height = 2.dp).background(Color.Black))
        }
    }
}

private fun widgetIcon(widget: Widget): Int = when (widget) {
    is WeatherWidget -> R.drawable.light_mode_24px
    is CalendarWidget -> R.drawable.today_24px
    is MusicWidget -> R.drawable.music_note_24px
    is NotesWidget -> R.drawable.sticky_note_2_24px
    is BatteryWidget -> R.drawable.battery_full_24px
    is AtAGlanceWidget -> R.drawable.visibility_24px
    is RemindersWidget -> R.drawable.task_alt_24px
    is NetworkWidget -> R.drawable.wifi_24px
    is SystemWidget -> R.drawable.memory_24px
    is FreezeWidget -> R.drawable.ac_unit_24px
    is AppsWidget -> R.drawable.apps_24px
    else -> R.drawable.widgets_24px
}
