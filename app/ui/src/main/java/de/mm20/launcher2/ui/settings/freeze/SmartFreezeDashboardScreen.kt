package de.mm20.launcher2.ui.settings.freeze

import android.os.Process
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.component.TelosSearchBar
import de.mm20.launcher2.ui.component.preferences.*
import kotlinx.serialization.Serializable

/** Legacy route retained for navigation/backups; both entry points now open one hub. */
@Serializable
data object SmartFreezeDashboardRoute : NavKey

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SmartFreezeDashboardScreen(tabs: @Composable () -> Unit = {}) {
    val vm: FreezeSettingsScreenVM = viewModel()
    val apps by vm.allApps.collectAsStateWithLifecycle()
    val frozen by vm.frozenStates.collectAsStateWithLifecycle()
    val candidates by vm.candidates.collectAsStateWithLifecycle()
    val never by vm.neverFreezeApps.collectAsStateWithLifecycle()
    val system by vm.showSystemApps.collectAsStateWithLifecycle()
    val iconless by vm.showIconlessApps.collectAsStateWithLifecycle()
    val busy by vm.bulkBusy.collectAsStateWithLifecycle()
    val result by vm.bulkResult.collectAsStateWithLifecycle()
    val backend by vm.activeBackend.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableStateOf(listOf<String>()) }
    var detailKey by rememberSaveable { mutableStateOf<String?>(null) }
    LifecycleResumeEffect(Unit) {
        vm.refreshBackendState(); vm.refreshAppStates()
        onPauseOrDispose { }
    }
    val shown = remember(apps, query, filter, frozen, candidates) {
        apps.filter { app ->
            (app.label.contains(query, true) || app.componentName.packageName.contains(query, true)) &&
                when (filter) { 1 -> frozen[app.key] == true; 2 -> app.componentName.packageName in candidates; else -> true }
        }
    }
    val picked = remember(apps, selected) { apps.filter { it.key in selected } }
    PreferenceScreen(title = stringResource(R.string.preference_screen_freeze)) {
        item { tabs() }
        item {
            Preference(title = stringResource(R.string.hf_freeze_backend_status),
                summary = backend?.name ?: stringResource(R.string.hf_freeze_disconnected),
                onClick = { vm.refreshBackendState() })
            Text(stringResource(R.string.freeze_hub_selection_help), modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall)
            TelosSearchBar(value = query, onValueChange = { query = it },
                placeholder = stringResource(R.string.hf_freeze_search_apps))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf(R.string.filter_all, R.string.hf_freeze_frozen, R.string.hf_freeze_auto_list)) { label ->
                    val i = when (label) { R.string.filter_all -> 0; R.string.hf_freeze_frozen -> 1; else -> 2 }
                    FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text(stringResource(label)) })
                }
            }
            SwitchPreference(title = stringResource(R.string.preference_freeze_show_system_apps), value = system,
                onValueChanged = vm::setShowSystemApps)
            SwitchPreference(title = stringResource(R.string.preference_freeze_show_iconless_apps), value = iconless,
                onValueChanged = vm::setShowIconlessApps)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(enabled = !busy, onClick = { selected = (selected + shown.map { it.key }).distinct() }) {
                    Text(stringResource(R.string.nwg_select_all))
                }
                TextButton(enabled = !busy, onClick = { selected = emptyList() }) { Text(stringResource(R.string.nwg_clear_selection)) }
            }
            if (picked.isNotEmpty()) {
                Text(stringResource(R.string.nwg_selected, picked.size), modifier = Modifier.padding(horizontal = 16.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(R.string.freeze_widget_freeze_now, R.string.hf_freeze_active, R.string.hf_freeze_auto_list)
                        .forEachIndexed { action, label ->
                            Button(enabled = !busy, onClick = { vm.bulkAction(picked, action) }) {
                                Text(if (action == 1) stringResource(R.string.freeze_hub_unfreeze) else stringResource(label))
                            }
                        }
                }
            }
            if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            result?.let { Text(it, modifier = Modifier.padding(16.dp)) }
        }
        items(shown, key = { it.key }) { app ->
            val pkg = app.componentName.packageName
            val icon by vm.getIcon(app, 40).collectAsStateWithLifecycle(null)
            val isFrozen = frozen[app.key] == true
            val status = stringResource(if (isFrozen) R.string.hf_freeze_frozen else R.string.hf_freeze_active)
            Preference(title = app.label,
                summary = "$pkg · $status" + if (pkg in never) " · " + stringResource(R.string.freeze_app_state_never)
                    else if (pkg in candidates) " · " + stringResource(R.string.hf_freeze_auto) else "",
                icon = { ShapedLauncherIcon(size = 40.dp, icon = { icon }, frozen = isFrozen) },
                onClick = { detailKey = app.key }, controls = {
                    Checkbox(checked = app.key in selected, enabled = !busy,
                        onCheckedChange = { checked -> selected = if (checked) (selected + app.key).distinct() else selected - app.key },
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp))
                })
        }
    }
    apps.firstOrNull { it.key == detailKey }?.let { app ->
        FreezeAppPolicyDialog(app, apps, vm) { detailKey = null }
    }
}
