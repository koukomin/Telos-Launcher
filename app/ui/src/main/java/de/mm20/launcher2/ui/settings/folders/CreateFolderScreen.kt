package de.mm20.launcher2.ui.settings.folders

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.locals.LocalBackStack
import kotlinx.serialization.Serializable

@Serializable
data object CreateFolderRoute : NavKey

/**
 * Alternative to drag-one-app-onto-another folder creation, which doesn't exist in the app grid
 * (there's no in-grid drop target for it - the only prior folder creation path was the Dock's
 * explicit "Add folder" menu, which starts empty and adds items one at a time afterwards). This
 * screen creates a fully-populated folder in one step and drops it into the first empty dock
 * slot, extending the dock if none is free.
 */
@Composable
fun CreateFolderScreen() {
    val viewModel: CreateFolderScreenVM = viewModel()
    val context = LocalContext.current
    val backStack = LocalBackStack.current

    val apps by viewModel.apps.collectAsStateWithLifecycle(emptyList())
    var folderName by remember { mutableStateOf("") }
    val selected = remember { mutableStateOf(setOf<String>()) }

    PreferenceScreen(
        title = stringResource(R.string.preference_screen_create_folder),
        topBarActions = {
            if (folderName.isNotBlank() && selected.value.size >= 2) {
                IconButton(
                    onClick = {
                        viewModel.createFolder(folderName.trim(), selected.value)
                        Toast.makeText(
                            context,
                            context.getString(R.string.create_folder_success, folderName.trim()),
                            Toast.LENGTH_SHORT,
                        ).show()
                        backStack.removeLastOrNull()
                    }
                ) {
                    Icon(
                        painter = painterResource(R.drawable.check_24px),
                        contentDescription = stringResource(R.string.create_folder_action),
                    )
                }
            }
        }
    ) {
        item {
            OutlinedTextField(
                value = folderName,
                onValueChange = { folderName = it },
                label = { Text(stringResource(R.string.create_folder_name_label)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Text(
                text = stringResource(R.string.create_folder_pick_apps_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        items(apps, key = { it.key }) { app ->
            val icon by viewModel.getIcon(app, 40.dp.value.toInt()).collectAsStateWithLifecycle(null)
            AppCheckRow(
                app = app,
                icon = icon,
                checked = app.key in selected.value,
                onCheckedChange = { checked ->
                    selected.value = if (checked) selected.value + app.key
                    else selected.value - app.key
                },
            )
        }
    }
}

@Composable
private fun AppCheckRow(
    app: Application,
    icon: LauncherIcon?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .background(if (checked) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // === TELOS_PENDING_REVIEW_START: ui_i18n_and_features_batch ===
        ShapedLauncherIcon(
            size = 40.dp, 
            icon = { icon }, 
            grayscale = app.isSuspended
        )
        // === TELOS_PENDING_REVIEW_END: ui_i18n_and_features_batch ===
        Text(
            text = app.label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        // No separate onCheckedChange here - the row's own clickable above already toggles on
        // any tap in the row, including on the checkbox itself. Wiring both up double-toggles
        // when the tap lands on the checkbox specifically (row click + checkbox click both
        // fire), so this is purely the visual indicator; null disables Checkbox's own indication
        // ripple/hit target without graying it out since checked is still driven live.
        Checkbox(checked = checked, onCheckedChange = null)
    }
}
