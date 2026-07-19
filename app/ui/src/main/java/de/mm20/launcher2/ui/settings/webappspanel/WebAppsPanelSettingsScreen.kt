package de.mm20.launcher2.ui.settings.webappspanel

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import coil.compose.AsyncImage
import de.mm20.launcher2.search.WebAppShortcut
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.DismissableBottomSheet
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.ListPreferenceItem
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.settings.webappshortcuts.EditWebAppShortcutSheet
import kotlinx.serialization.Serializable

@Serializable
data object WebAppsPanelSettingsRoute : NavKey

@Composable
fun WebAppsPanelSettingsScreen() {
    val viewModel: WebAppsPanelSettingsScreenVM = viewModel()

    val items by viewModel.items.collectAsStateWithLifecycle()
    val availableToAdd by viewModel.availableToAdd.collectAsStateWithLifecycle()
    val direction by viewModel.direction.collectAsStateWithLifecycle()

    var editShortcut by remember { mutableStateOf<WebAppShortcut?>(null) }
    var showCreateSheet by remember { mutableStateOf(false) }
    var showAddExistingSheet by remember { mutableStateOf(false) }

    PreferenceScreen(title = stringResource(R.string.preference_screen_web_apps_panel)) {
        item {
            PreferenceCategory {
                SwitchPreference(
                    title = stringResource(R.string.preference_web_apps_panel),
                    summary = stringResource(R.string.preference_web_apps_panel_summary),
                    value = direction != null,
                    onValueChanged = { viewModel.setEnabled(it) },
                )
                if (direction != null) {
                    ListPreference(
                        title = stringResource(R.string.web_apps_panel_direction),
                        items = listOf(
                            ListPreferenceItem(stringResource(R.string.web_apps_panel_direction_left), PanelDirection.Left),
                            ListPreferenceItem(stringResource(R.string.web_apps_panel_direction_right), PanelDirection.Right),
                        ),
                        value = direction ?: PanelDirection.Right,
                        onValueChanged = { viewModel.setDirection(it) },
                    )
                }
            }
        }
        item {
            PreferenceCategory(title = stringResource(R.string.web_apps_panel_items)) {
                for (shortcut in items) {
                    Preference(
                        icon = {
                            val model = shortcut.iconUri ?: shortcut.faviconUrl
                            if (model != null) {
                                AsyncImage(
                                    model = model,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(MaterialTheme.shapes.extraSmall),
                                )
                            } else {
                                Icon(painterResource(R.drawable.language_24px), contentDescription = null)
                            }
                        },
                        title = shortcut.label,
                        summary = shortcut.url,
                        onClick = { editShortcut = shortcut },
                        controls = {
                            IconButton(onClick = { viewModel.remove(shortcut) }) {
                                Icon(
                                    painterResource(R.drawable.close_24px),
                                    contentDescription = stringResource(R.string.widget_action_remove),
                                )
                            }
                        }
                    )
                }
                Preference(
                    icon = R.drawable.add_24px,
                    title = stringResource(R.string.web_app_shortcut_create),
                    onClick = { showCreateSheet = true },
                )
                if (availableToAdd.isNotEmpty()) {
                    Preference(
                        icon = R.drawable.language_24px,
                        title = stringResource(R.string.web_apps_panel_add_existing),
                        onClick = { showAddExistingSheet = true },
                    )
                }
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

    val shortcutBeingEdited = editShortcut
    if (shortcutBeingEdited != null) {
        EditWebAppShortcutSheet(
            expanded = true,
            existing = shortcutBeingEdited,
            onSave = { label, url, iconUri, faviconUrl, rendererPackage ->
                viewModel.update(shortcutBeingEdited, label, url, iconUri, faviconUrl, rendererPackage)
                editShortcut = null
            },
            onDismiss = { editShortcut = null },
            onImportIcon = { uri, sizePx -> viewModel.importIcon(uri, sizePx) },
            onFindFavicon = { url -> viewModel.findFavicon(url) },
        )
    }

    DismissableBottomSheet(
        expanded = showAddExistingSheet,
        onDismissRequest = { showAddExistingSheet = false },
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            items(availableToAdd) { shortcut ->
                Preference(
                    icon = {
                        val model = shortcut.iconUri ?: shortcut.faviconUrl
                        if (model != null) {
                            AsyncImage(
                                model = model,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(MaterialTheme.shapes.extraSmall),
                            )
                        } else {
                            Icon(painterResource(R.drawable.language_24px), contentDescription = null)
                        }
                    },
                    title = shortcut.label,
                    summary = shortcut.url,
                    onClick = {
                        viewModel.addExisting(shortcut)
                        showAddExistingSheet = false
                    },
                )
            }
        }
    }
}
