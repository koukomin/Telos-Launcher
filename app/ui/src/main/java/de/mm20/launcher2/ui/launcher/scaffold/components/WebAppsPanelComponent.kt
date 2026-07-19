package de.mm20.launcher2.ui.launcher.scaffold.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.search.WebAppShortcut
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.dragndrop.DraggableItem
import de.mm20.launcher2.ui.component.dragndrop.LazyVerticalDragAndDropGrid
import de.mm20.launcher2.ui.component.dragndrop.rememberLazyDragAndDropGridState
import de.mm20.launcher2.ui.launcher.scaffold.LauncherScaffoldState
import de.mm20.launcher2.ui.launcher.search.common.grid.GridItem
import de.mm20.launcher2.ui.locals.LocalGridSettings
import de.mm20.launcher2.ui.settings.webappshortcuts.EditWebAppShortcutSheet
import kotlinx.coroutines.launch

/**
 * A dedicated grid of web app shortcuts, reached via whichever gesture slot has
 * [de.mm20.launcher2.preferences.GestureAction.WebAppsPanel] assigned - separate from the
 * Floating Launcher sidebar, which stays app-shortcut-only. Reuses [GridItem] (icon loading, tap
 * to launch, long-press popup) for each cell and [LazyVerticalDragAndDropGrid] for in-panel
 * reordering, matching the Widgets screen's own edit-mode pattern.
 */
internal object WebAppsPanelComponent : ScaffoldComponent() {

    @Composable
    override fun Component(
        modifier: Modifier,
        insets: PaddingValues,
        state: LauncherScaffoldState,
    ) {
        val viewModel: WebAppsPanelVM = viewModel()
        val items by viewModel.items.collectAsStateWithLifecycle()
        var editMode by rememberSaveable { mutableStateOf(false) }
        var showCreateSheet by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val columns = LocalGridSettings.current.columnCount.coerceAtLeast(2)

        val gridState = rememberLazyDragAndDropGridState(
            onDragStart = { editMode },
        ) { from, to ->
            viewModel.moveItem(from.index, to.index)
        }

        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(insets),
        ) {
            LazyVerticalDragAndDropGrid(
                state = gridState,
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = if (editMode) 64.dp else 8.dp, bottom = 8.dp),
            ) {
                items(
                    items.size,
                    key = { items[it].key },
                ) { i ->
                    val shortcut = items[i]
                    DraggableItem(state = gridState, key = shortcut.key) { _ ->
                        Box {
                            GridItem(
                                item = shortcut,
                                showLabels = true,
                            )
                            if (editMode) {
                                IconButton(
                                    onClick = { viewModel.remove(shortcut) },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(28.dp),
                                ) {
                                    Icon(
                                        painterResource(R.drawable.close_24px),
                                        contentDescription = stringResource(R.string.widget_action_remove),
                                    )
                                }
                            }
                        }
                    }
                }
                if (editMode) {
                    item(key = "add") {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            IconButton(onClick = { showCreateSheet = true }) {
                                Icon(
                                    painterResource(R.drawable.add_24px),
                                    contentDescription = stringResource(R.string.web_app_shortcut_create),
                                )
                            }
                        }
                    }
                }
            }
            if (editMode) {
                BackHandler {
                    editMode = false
                    scope.launch { state.unlock() }
                }
            }
            AnimatedVisibility(
                editMode,
                modifier = Modifier.zIndex(10f),
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
            ) {
                CenterAlignedTopAppBar(
                    title = { Text(stringResource(R.string.web_apps_panel_edit)) },
                    navigationIcon = {
                        IconButton(onClick = {
                            editMode = false
                            scope.launch { state.unlock() }
                        }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_done))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                )
            }
            if (!editMode) {
                IconButton(
                    onClick = {
                        editMode = true
                        scope.launch { state.lock(hideSearchBar = true) }
                    },
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    Icon(
                        painterResource(R.drawable.tune_24px),
                        contentDescription = stringResource(R.string.web_apps_panel_edit),
                    )
                }
            }
        }

        EditWebAppShortcutSheet(
            expanded = showCreateSheet,
            existing = null,
            onSave = { label, url, iconUri, faviconUrl, rendererPackage ->
                viewModel.createAndAdd(label, url, iconUri, faviconUrl, rendererPackage)
                showCreateSheet = false
            },
            onDismiss = { showCreateSheet = false },
            onImportIcon = { uri, sizePx -> viewModel.importIcon(uri, sizePx) },
            onFindFavicon = { url -> viewModel.findFavicon(url) },
        )
    }
}
