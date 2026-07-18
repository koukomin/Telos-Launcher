package de.mm20.launcher2.ui.settings.floating

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.preferences.FloatingLauncherZone
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
data class FloatingLauncherZoneAppsRoute(val zone: String) : NavKey

@Composable
fun FloatingLauncherZoneAppsScreen(zone: String) {
    val floatingLauncherZone = remember(zone) { FloatingLauncherZone.valueOf(zone) }
    val viewModel: FloatingLauncherZoneAppsScreenVM = viewModel()
    LaunchedEffect(floatingLauncherZone) {
        viewModel.init(floatingLauncherZone)
    }

    val apps by viewModel.apps.collectAsStateWithLifecycle(emptyList())
    val selectedKeys by viewModel.selectedKeys.collectAsStateWithLifecycle(emptySet())

    PreferenceScreen(title = stringResource(zoneLabelRes(floatingLauncherZone))) {
        if (apps.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.floating_launcher_zone_apps_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            items(apps, key = { it.key }) { app ->
                AppCheckRow(
                    app = app,
                    checked = app.key in selectedKeys,
                    isFrozen = viewModel.isFrozen(app),
                    onCheckedChange = { viewModel.toggleApp(app.key) },
                    getIcon = { size -> viewModel.getIcon(app, size) },
                )
            }
        }
    }
}

@Composable
private fun AppCheckRow(
    app: Application,
    checked: Boolean,
    isFrozen: Boolean,
    onCheckedChange: () -> Unit,
    getIcon: (Int) -> Flow<LauncherIcon?>,
) {
    val icon by remember(app.key) { getIcon(48) }.collectAsState(null)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCheckedChange)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ShapedLauncherIcon(size = 40.dp, icon = { icon }, grayscale = isFrozen)
        Text(
            text = app.labelOverride ?: app.label,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Checkbox(checked = checked, onCheckedChange = { onCheckedChange() })
    }
}
