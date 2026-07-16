package de.mm20.launcher2.ui.settings.filesearch

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen

@Composable
fun ExcludedFoldersSettingsScreen() {
    val viewModel: FileSearchSettingsScreenVM = viewModel()
    val context = LocalContext.current

    val excludedFolders by viewModel.excludedFolders.collectAsState()

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null && !viewModel.addExcludedFolder(uri)) {
            Toast.makeText(context, R.string.excluded_folders_unsupported, Toast.LENGTH_LONG)
                .show()
        }
    }

    PreferenceScreen(title = stringResource(R.string.preference_file_search_excluded_folders)) {
        item {
            PreferenceCategory {
                Preference(
                    icon = R.drawable.add_24px,
                    title = stringResource(R.string.excluded_folders_add),
                    onClick = {
                        folderPicker.launch(null)
                    }
                )
            }
        }
        item {
            PreferenceCategory {
                if (excludedFolders.isEmpty()) {
                    Text(
                        text = stringResource(R.string.excluded_folders_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    for (folder in excludedFolders.sorted()) {
                        Preference(
                            icon = R.drawable.folder_24px,
                            title = folder.substringAfterLast('/'),
                            summary = folder,
                            controls = {
                                IconButton(onClick = { viewModel.removeExcludedFolder(folder) }) {
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
        }
    }
}
