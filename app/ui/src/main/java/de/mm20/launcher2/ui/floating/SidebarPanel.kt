package de.mm20.launcher2.ui.floating

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.applications.AppRepository
import de.mm20.launcher2.freeze.FreezeManager
import de.mm20.launcher2.icons.IconService
import de.mm20.launcher2.preferences.FloatingLauncherEdge
import de.mm20.launcher2.preferences.ui.FloatingLauncherSettings
import de.mm20.launcher2.preferences.ui.ShutterSettings
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.searchable.SavableSearchableRepository
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.launcher.widgets.WidgetItem
import de.mm20.launcher2.ui.overlays.OverlayHost
import de.mm20.launcher2.ui.settings.SettingsActivity
import de.mm20.launcher2.ui.theme.LauncherTheme
import de.mm20.launcher2.widgets.WidgetRepository
import kotlinx.coroutines.launch

/**
 * The window content of the sidebar while it is open: the card next to the handle, or the editor.
 * The look follows the launcher theme, the layout follows the Smart Sidebar of OxygenOS.
 */
@Composable
internal fun SidebarPanelContent(
    startInEditMode: Boolean,
    settings: FloatingLauncherSettings,
    iconService: IconService,
    searchableRepository: SavableSearchableRepository,
    freezeManager: FreezeManager,
    appRepository: AppRepository,
    shutterSettings: ShutterSettings,
    widgetRepository: WidgetRepository,
    fileDockItems: List<FileDockItem>,
    onDismiss: () -> Unit,
    onEditModeChanged: (Boolean) -> Unit,
    onAppDropped: (String) -> Unit,
    onExternalContentDropped: (ClipData) -> Unit,
    onRemoveFileDockItem: (String) -> Unit,
    onTool: (SidebarTool) -> Unit,
) {
    val context = LocalContext.current
    val side by settings.side.collectAsState(FloatingLauncherEdge.Right)
    val handleY by settings.handleY.collectAsState(0.5f)
    val columns by settings.columns.collectAsState(2)
    val maxPerColumn by settings.maxPerColumn.collectAsState(10)
    val showLabels by settings.showLabels.collectAsState(true)
    val panelAlpha by settings.panelAlpha.collectAsState(0.85f)
    val iconSize by settings.iconSize.collectAsState(48)
    val floatingWindows by settings.floatingWindows.collectAsState(true)
    val fileDock by settings.fileDock.collectAsState(true)
    val keys by settings.items.collectAsState(emptyList())

    val style = SidebarStyle(
        iconSize = iconSize.dp,
        showLabels = showLabels,
        panelAlpha = panelAlpha,
        floatingWindows = floatingWindows,
        columns = columns,
        fileDock = fileDock,
    )
    val entries = rememberSidebarEntries(keys, searchableRepository, widgetRepository)
    var editMode by remember { mutableStateOf(startInEditMode) }
    LaunchedEffect(editMode) { onEditModeChanged(editMode) }

    LauncherTheme {
        CompositionLocalProvider(LocalSidebarStyle provides style) {
            OverlayHost {
                if (editMode) {
                    SidebarEditor(
                        entries = entries,
                        settings = settings,
                        iconService = iconService,
                        appRepository = appRepository,
                        widgetRepository = widgetRepository,
                        onDone = { editMode = false },
                        onOpenSettings = {
                            onDismiss()
                            // the settings are an activity of the app, started from the service
                            context.startActivity(
                                Intent(context, SettingsActivity::class.java).apply {
                                    putExtra(SettingsActivity.EXTRA_ROUTE, SettingsActivity.ROUTE_FLOATING_LAUNCHER)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        },
                    )
                } else {
                    SidebarCard(
                        side = side,
                        handleY = handleY,
                        entries = entries,
                        maxPerColumn = maxPerColumn,
                        iconService = iconService,
                        searchableRepository = searchableRepository,
                        freezeManager = freezeManager,
                        appRepository = appRepository,
                        shutterSettings = shutterSettings,
                        fileDockItems = fileDockItems,
                        onDismiss = onDismiss,
                        onEdit = { editMode = true },
                        onAppDropped = onAppDropped,
                        onExternalContentDropped = onExternalContentDropped,
                        onRemoveFileDockItem = onRemoveFileDockItem,
                        onTool = onTool,
                    )
                }
            }
        }
    }
}

@Composable
private fun SidebarCard(
    side: FloatingLauncherEdge,
    handleY: Float,
    entries: List<SidebarEntry>,
    maxPerColumn: Int,
    iconService: IconService,
    searchableRepository: SavableSearchableRepository,
    freezeManager: FreezeManager,
    appRepository: AppRepository,
    shutterSettings: ShutterSettings,
    fileDockItems: List<FileDockItem>,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onAppDropped: (String) -> Unit,
    onExternalContentDropped: (ClipData) -> Unit,
    onRemoveFileDockItem: (String) -> Unit,
    onTool: (SidebarTool) -> Unit,
) {
    val context = LocalContext.current
    val style = LocalSidebarStyle.current
    val coroutineScope = rememberCoroutineScope()
    var isDropTarget by remember { mutableStateOf(false) }
    var showFileDock by remember { mutableStateOf(false) }
    var showRecentFiles by remember { mutableStateOf(false) }
    var showTools by remember { mutableStateOf(false) }
    val hasWidget = entries.any { it is SidebarEntry.WidgetEntry }
    val columns = style.columns

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.12f))
            .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) },
        // The card opens next to the handle it came from
        contentAlignment = BiasAlignment(
            horizontalBias = if (side == FloatingLauncherEdge.Left) -1f else 1f,
            verticalBias = handleY * 2f - 1f,
        ),
    ) {
        val maxCardHeight = maxHeight - 48.dp
        Surface(
            modifier = Modifier
                .padding(8.dp)
                .widthIn(max = if (hasWidget) 320.dp else sidebarCell() * columns + 32.dp)
                .heightIn(max = minOf(maxCardHeight, sidebarCell() * maxPerColumn + 160.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                .dragAndDropTarget(
                    shouldStartDragAndDrop = { true },
                    target = object : DragAndDropTarget {
                        override fun onEntered(event: DragAndDropEvent) { isDropTarget = true }
                        override fun onExited(event: DragAndDropEvent) { isDropTarget = false }
                        override fun onEnded(event: DragAndDropEvent) { isDropTarget = false }
                        override fun onDrop(event: DragAndDropEvent): Boolean {
                            isDropTarget = false
                            val clipData = event.toAndroidDragEvent().clipData?.takeIf { it.itemCount > 0 } ?: return false
                            if (clipData.description.label == APP_DRAG_CLIP_LABEL) {
                                val key = clipData.getItemAt(0)?.text?.toString() ?: return false
                                onAppDropped(key)
                            } else {
                                onExternalContentDropped(clipData)
                            }
                            return true
                        }
                    },
                ),
            color = if (isDropTarget) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = (style.panelAlpha + 0.07f).coerceAtMost(1f))
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = style.panelAlpha)
            },
            shape = RoundedCornerShape(28.dp),
            shadowElevation = 8.dp,
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (style.fileDock) {
                    SidebarWideButton(
                        icon = R.drawable.ic_sidebar_file_dock,
                        label = R.string.floating_launcher_file_dock,
                        highlighted = fileDockItems.isNotEmpty(),
                    ) { showFileDock = true }
                    HorizontalDivider(Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(
                        entries,
                        key = { it.key },
                        span = { entry -> if (entry is SidebarEntry.WidgetEntry) GridItemSpan(maxLineSpan) else GridItemSpan(1) },
                    ) { entry ->
                        when (entry) {
                            is SidebarEntry.App -> FavoriteIcon(
                                item = entry.item,
                                iconService = iconService,
                                appRepository = appRepository,
                                shutterSettings = shutterSettings,
                                editMode = false,
                                onClick = {
                                    val item = entry.item
                                    if (item is Application && freezeManager.isFrozen(item.componentName.packageName)) {
                                        coroutineScope.launch {
                                            freezeManager.unfreeze(item.componentName.packageName)
                                            launchInSidebar(context, item, style.floatingWindows)
                                        }
                                    } else {
                                        launchInSidebar(context, item, style.floatingWindows)
                                    }
                                    onDismiss()
                                },
                                onRemove = {},
                            )

                            is SidebarEntry.Tool -> SidebarTile(
                                label = stringResource(entry.tool.label),
                                onClick = {
                                    if (entry.tool == SidebarTool.RecentFiles) showRecentFiles = true else onTool(entry.tool)
                                },
                            ) { SidebarToolIcon(entry.tool, style.iconSize) }

                            is SidebarEntry.WidgetEntry -> WidgetItem(
                                widget = entry.widget,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                    }
                    item(key = "all") {
                        SidebarTile(stringResource(R.string.floating_launcher_tools_all), { showTools = true }) {
                            SidebarAllToolsIcon(style.iconSize)
                        }
                    }
                    item(key = "edit") {
                        SidebarTile(stringResource(R.string.floating_launcher_edit), onEdit) { SidebarEditIcon(style.iconSize) }
                    }
                }
            }
        }

        if (showFileDock) {
            FileDockOverlay(items = fileDockItems, onRemove = onRemoveFileDockItem, onDismiss = { showFileDock = false })
        }
        if (showRecentFiles) {
            RecentFilesOverlay(
                onOpened = {
                    showRecentFiles = false
                    onDismiss()
                },
                onDismiss = { showRecentFiles = false },
            )
        }
        if (showTools) {
            SidebarToolsOverlay(
                onTool = { tool ->
                    showTools = false
                    if (tool == SidebarTool.RecentFiles) showRecentFiles = true else onTool(tool)
                },
                onDismiss = { showTools = false },
            )
        }
    }
}
