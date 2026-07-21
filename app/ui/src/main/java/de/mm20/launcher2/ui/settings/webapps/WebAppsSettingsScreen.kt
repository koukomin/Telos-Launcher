package de.mm20.launcher2.ui.settings.webapps

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import coil.compose.AsyncImage
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.ListPreferenceItem
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.settings.webapps.EditWebAppShortcutSheet
import de.mm20.launcher2.ui.settings.webapps.WebAppsSettingsRoute
import de.mm20.launcher2.ui.settings.webapps.WebAppsSettingsScreenVM
import de.mm20.launcher2.ui.settings.webapps.PanelDirection
import kotlinx.serialization.Serializable

@Serializable
data object WebAppsSettingsRoute : NavKey

@Composable
fun WebAppsSettingsScreen() {
    val viewModel: WebAppsSettingsScreenVM = viewModel()
    val shortcuts by viewModel.shortcuts.collectAsState()
    val direction by viewModel.direction.collectAsState()
    val adBlockEnabled by viewModel.adBlockEnabled.collectAsState()
    val zoomControlsEnabled by viewModel.zoomControlsEnabled.collectAsState()
    val trackingParamStrippingEnabled by viewModel.trackingParamStrippingEnabled.collectAsState()

    PreferenceScreen(
        title = stringResource(R.string.preference_screen_web_app_shortcuts),
    ) {
        item {
            PreferenceCategory(title = stringResource(R.string.preference_screen_web_apps_panel)) {
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
                        onValueChanged = { if (it != null) viewModel.setDirection(it) },
                    )
                }
            }
        }
        item {
            PreferenceCategory(title = stringResource(R.string.preference_category_web_app_browsing)) {
                SwitchPreference(
                    title = stringResource(R.string.preference_web_app_ad_block),
                    summary = stringResource(R.string.preference_web_app_ad_block_summary),
                    value = adBlockEnabled,
                    onValueChanged = { viewModel.setAdBlockEnabled(it) },
                )
                SwitchPreference(
                    title = stringResource(R.string.preference_web_app_zoom_controls),
                    summary = stringResource(R.string.preference_web_app_zoom_controls_summary),
                    value = zoomControlsEnabled,
                    onValueChanged = { viewModel.setZoomControlsEnabled(it) },
                )
                SwitchPreference(
                    title = stringResource(R.string.preference_web_app_tracking_param_stripping),
                    summary = stringResource(R.string.preference_web_app_tracking_param_stripping_summary),
                    value = trackingParamStrippingEnabled,
                    onValueChanged = { viewModel.setTrackingParamStrippingEnabled(it) },
                )
            }
        }
        item {
            PreferenceCategory(title = stringResource(R.string.web_apps_panel_items)) {
                for (shortcut in shortcuts) {
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
                                Icon(
                                    painterResource(R.drawable.language_24px),
                                    contentDescription = null,
                                )
                            }
                        },
                        title = shortcut.label,
                        summary = shortcut.url,
                        onClick = {
                            viewModel.editShortcut(shortcut)
                        },
                        controls = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Checkbox(
                                        checked = shortcut.showInGrid,
                                        onCheckedChange = { viewModel.setPlacement(shortcut, it, shortcut.showInPanel) }
                                    )
                                    Text(stringResource(R.string.search_filter_apps), style = MaterialTheme.typography.labelSmall)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Checkbox(
                                        checked = shortcut.showInPanel,
                                        onCheckedChange = { viewModel.setPlacement(shortcut, shortcut.showInGrid, it) }
                                    )
                                    Text(stringResource(R.string.preference_screen_web_apps_panel), style = MaterialTheme.typography.labelSmall)
                                }
                                IconButton(onClick = { viewModel.delete(shortcut) }) {
                                    Icon(
                                        painterResource(R.drawable.delete_24px),
                                        contentDescription = stringResource(R.string.menu_delete),
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
        item {
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                FilledTonalButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    onClick = {
                        viewModel.createShortcut()
                    }) {
                    Icon(
                        painterResource(R.drawable.add_20px),
                        null,
                        modifier = Modifier
                            .padding(end = ButtonDefaults.IconSpacing)
                            .size(ButtonDefaults.IconSize)
                    )
                    Text(stringResource(R.string.web_app_shortcut_create))
                }
            }
        }
    }

    val createShortcut by viewModel.showCreateDialog
    val editShortcut by viewModel.showEditDialogFor

    EditWebAppShortcutSheet(
        expanded = createShortcut,
        existing = null,
        onSave = { label, url, iconUri, faviconUrl, rendererPackage, showInGrid, showInPanel, iconSource, customCss ->
            viewModel.save(null, label, url, iconUri, faviconUrl, rendererPackage, showInGrid, showInPanel, iconSource, customCss)
        },
        onDismiss = { viewModel.dismissDialogs() },
        onImportIcon = { uri, sizePx -> viewModel.importIcon(uri, sizePx) },
        onFindFavicon = { url -> viewModel.findFavicon(url) },
    )
    EditWebAppShortcutSheet(
        expanded = editShortcut != null,
        existing = editShortcut,
        onSave = { label, url, iconUri, faviconUrl, rendererPackage, showInGrid, showInPanel, iconSource, customCss ->
            viewModel.save(editShortcut, label, url, iconUri, faviconUrl, rendererPackage, showInGrid, showInPanel, iconSource, customCss)
        },
        onDismiss = { viewModel.dismissDialogs() },
        onImportIcon = { uri, sizePx -> viewModel.importIcon(uri, sizePx) },
        onFindFavicon = { url -> viewModel.findFavicon(url) },
    )
}
