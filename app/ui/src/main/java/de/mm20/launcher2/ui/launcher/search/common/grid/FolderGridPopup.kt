package de.mm20.launcher2.ui.launcher.search.common.grid

import de.mm20.launcher2.ui.R
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.mm20.launcher2.applications.FolderImpl
import de.mm20.launcher2.preferences.ui.UiSettings
import de.mm20.launcher2.search.Folder
import de.mm20.launcher2.searchable.SavableSearchableRepository
import de.mm20.launcher2.ui.launcher.search.common.SearchableItemVM
import de.mm20.launcher2.ui.launcher.search.listItemViewModel
import org.koin.compose.koinInject

@Composable
fun FolderGridPopup(
    folder: Folder,
    show: MutableTransitionState<Boolean>,
    animationProgress: Float,
    origin: IntRect,
    onDismiss: () -> Unit
) {
    val viewModel: SearchableItemVM = listItemViewModel(key = "folder-${folder.key}")
    val items by viewModel.children.collectAsStateWithLifecycle(emptyList())
    val searchableRepository: SavableSearchableRepository = koinInject()
    val uiSettings: UiSettings = koinInject()
    val folderBackgroundColor by uiSettings.folderBackgroundColor.collectAsState(null)
    val folderCoverEnabled by uiSettings.folderCoverEnabled.collectAsState(true)

    // The popup keeps showing the folder instance it was opened with, so edits must be applied
    // on top of the latest saved version instead of that stale instance (otherwise a second
    // edit would silently revert the first one).
    var latestFolder by remember(folder.key) { mutableStateOf(folder as? FolderImpl) }

    var showPicker by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    LaunchedEffect(folder) {
        viewModel.init(folder, 48)
    }

    if (folderCoverEnabled && show.targetState) {
        val firstItem = items.firstOrNull()
        if (firstItem != null) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .graphicsLayer {
                        alpha = (1f - animationProgress).coerceIn(0f, 1f)
                        val scale = 1f + animationProgress * 0.3f
                        scaleX = scale
                        scaleY = scale
                    },
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.runtime.CompositionLocalProvider(
                    de.mm20.launcher2.ui.locals.LocalGridSettings provides de.mm20.launcher2.preferences.ui.GridSettings(
                        iconSize = 96,
                    )
                ) {
                    GridItem(
                        item = firstItem,
                        showLabels = false,
                    )
                }
            }
        }
    }

    AnimatedVisibility(
        show,
        enter = expandIn(
            animationSpec = tween(300),
            expandFrom = Alignment.Center,
        ) { origin.size },
        exit = shrinkOut(
            animationSpec = tween(300),
            shrinkTowards = Alignment.Center,
        ) { origin.size },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .background(
                    folderBackgroundColor?.let { androidx.compose.ui.graphics.Color(it) }
                        ?: MaterialTheme.colorScheme.surfaceContainerHigh,
                    MaterialTheme.shapes.large
                )
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = folder.labelOverride ?: folder.label,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .clickable {
                        showRenameDialog = true
                    }
            )
            
            SearchResultGrid(
                items = items,
                columns = 4,
                showLabels = true,
                enableShutterGesture = false,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = { showPicker = true }) {
                    Text(stringResource(R.string.hf_folder_manage_apps))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        }
    }

    if (showPicker) {
        de.mm20.launcher2.ui.component.DismissableBottomSheet(
            expanded = true,
            onDismissRequest = { showPicker = false }
        ) {
            de.mm20.launcher2.ui.common.SearchablePicker(
                value = null,
                onValueChanged = { searchable ->
                    val base = latestFolder
                    if (searchable != null && base != null) {
                        val currentKeys = base.itemKeys.toMutableList()
                        if (currentKeys.contains(searchable.key)) {
                            currentKeys.remove(searchable.key)
                        } else {
                            currentKeys.add(searchable.key)
                        }
                        val updated = base.copy(itemKeys = currentKeys)
                        latestFolder = updated
                        searchableRepository.upsert(updated)
                    }
                },
                contentPadding = PaddingValues(16.dp)
            )
        }
    }

    if (showRenameDialog) {
        var newName by remember { mutableStateOf(folder.labelOverride ?: folder.label) }
        androidx.compose.ui.window.Dialog(onDismissRequest = { showRenameDialog = false }) {
            androidx.compose.material3.Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.hf_folder_rename), style = MaterialTheme.typography.titleMedium)
                    androidx.compose.material3.OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showRenameDialog = false }) {
                            Text(stringResource(android.R.string.cancel))
                        }
                        TextButton(onClick = {
                            latestFolder?.let {
                                val updated = it.copy(labelOverride = newName.trim().ifBlank { null })
                                latestFolder = updated
                                searchableRepository.upsert(updated)
                            }
                            showRenameDialog = false
                        }) {
                            Text(stringResource(android.R.string.ok))
                        }
                    }
                }
            }
        }
    }
}
