package de.mm20.launcher2.ui.settings.applock

import de.mm20.launcher2.search.GreekFold
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import kotlinx.serialization.Serializable

@Serializable
data object AppLockAppsRoute : NavKey

/**
 * The full per-app lock picker: every installed app, filterable by a search box (same
 * [DockedSearchBar] sticky-header pattern as [de.mm20.launcher2.ui.common.SearchablePicker]), each
 * with a lock [Switch]. Reached from [AppLockSettingsScreen]'s "Locked apps" row rather than
 * embedded there, since the full app list doesn't belong mixed in with the rest of App Lock's
 * settings.
 */
@Composable
fun AppLockAppsScreen() {
    val viewModel: AppLockAppsScreenVM = viewModel()
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val lockedPackages by viewModel.lockedPackages.collectAsStateWithLifecycle()
    val gracePeriodOverrides by viewModel.gracePeriodOverrides.collectAsStateWithLifecycle()
    val gracePeriodOverrideOptions = gracePeriodOverrideOptions()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    val filteredApps = remember(apps, searchQuery) {
        if (searchQuery.isBlank()) apps
        else apps.filter { GreekFold.contains(it.label, searchQuery) }
    }

    val colorSurface = MaterialTheme.colorScheme.surfaceContainer

    PreferenceScreen(
        title = stringResource(R.string.preference_category_app_lock_apps),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        stickyHeader {
            DockedSearchBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        drawRect(
                            brush = Brush.verticalGradient(
                                0.5f to colorSurface,
                                0.5f to colorSurface.copy(alpha = 0f),
                            )
                        )
                    }
                    .padding(bottom = 16.dp),
                expanded = false,
                onExpandedChange = {},
                inputField = {
                    SearchBarDefaults.InputField(
                        leadingIcon = {
                            Icon(painterResource(R.drawable.search_24px), contentDescription = null)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    modifier = Modifier.offset(16.dp),
                                    onClick = { searchQuery = "" },
                                ) {
                                    Icon(painterResource(R.drawable.close_24px), null)
                                }
                            }
                        },
                        onSearch = {},
                        expanded = false,
                        onExpandedChange = {},
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        placeholder = { Text(stringResource(R.string.search_bar_placeholder)) },
                    )
                },
            ) {}
        }
        itemsIndexed(filteredApps, key = { _, it -> it.key }) { _, app ->
            val packageName = app.componentName.packageName
            val icon by viewModel.getIcon(app, 32.dp.value.toInt()).collectAsStateWithLifecycle(null)
            val locked = packageName in lockedPackages
            Column {
                Preference(
                    title = { Text(app.label) },
                    icon = { ShapedLauncherIcon(size = 32.dp, icon = { icon }) },
                    onClick = { viewModel.setLocked(packageName, !locked) },
                    controls = {
                        Switch(
                            checked = locked,
                            onCheckedChange = { viewModel.setLocked(packageName, it) },
                        )
                    },
                )
                AnimatedVisibility(locked) {
                    ListPreference(
                        title = stringResource(R.string.preference_app_lock_grace_period_override),
                        items = gracePeriodOverrideOptions,
                        value = gracePeriodOverrides[packageName],
                        onValueChanged = { viewModel.setGracePeriodOverride(packageName, it) },
                    )
                }
            }
        }
    }
}
