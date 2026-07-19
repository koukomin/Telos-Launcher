package de.mm20.launcher2.ui.settings.webappshortcuts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import coil.compose.AsyncImage
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import kotlinx.serialization.Serializable

@Serializable
data object WebAppShortcutsSettingsRoute : NavKey

@Composable
fun WebAppShortcutsSettingsScreen() {
    val viewModel: WebAppShortcutsSettingsScreenVM = viewModel()
    val shortcuts by viewModel.shortcuts.collectAsState()

    PreferenceScreen(
        title = stringResource(R.string.preference_screen_web_app_shortcuts),
    ) {
        item {
            PreferenceCategory {
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
                            IconButton(onClick = { viewModel.delete(shortcut) }) {
                                Icon(
                                    painterResource(R.drawable.delete_24px),
                                    contentDescription = stringResource(R.string.menu_delete),
                                )
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
        onSave = { label, url, iconUri, faviconUrl, rendererPackage ->
            viewModel.save(null, label, url, iconUri, faviconUrl, rendererPackage)
        },
        onDismiss = { viewModel.dismissDialogs() },
        onImportIcon = { uri, sizePx -> viewModel.importIcon(uri, sizePx) },
        onFindFavicon = { url -> viewModel.findFavicon(url) },
    )
    EditWebAppShortcutSheet(
        expanded = editShortcut != null,
        existing = editShortcut,
        onSave = { label, url, iconUri, faviconUrl, rendererPackage ->
            viewModel.save(editShortcut, label, url, iconUri, faviconUrl, rendererPackage)
        },
        onDismiss = { viewModel.dismissDialogs() },
        onImportIcon = { uri, sizePx -> viewModel.importIcon(uri, sizePx) },
        onFindFavicon = { url -> viewModel.findFavicon(url) },
    )
}
