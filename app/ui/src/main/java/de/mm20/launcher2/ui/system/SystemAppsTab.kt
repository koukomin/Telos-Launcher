package de.mm20.launcher2.ui.system

import android.app.ActivityManager
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.getSystemService
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.mm20.launcher2.base.R as BaseR
import de.mm20.launcher2.freeze.BloatDescriptions
import de.mm20.launcher2.freeze.FreezeManager
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.preferences.freeze.FreezeSettings
import de.mm20.launcher2.ui.component.TelosCyber
import de.mm20.launcher2.ui.component.TelosDotMatrixText
import de.mm20.launcher2.ui.component.TelosTechPill
import de.mm20.launcher2.ui.component.telosTechSurface
import de.mm20.launcher2.ui.layout.UiSpacing
import de.mm20.launcher2.ui.locals.LocalBackStack
import de.mm20.launcher2.ui.network.firewall.NetworkPackageFirewallRoute
import de.mm20.launcher2.ui.store.AppIcon
import de.mm20.launcher2.ui.store.EmptyMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

private enum class FreezeDeskFilter {
    All,
    Frozen,
    Running,
    System,
    Bloat,
    User,
}

internal data class InstalledSystemRow(
    val pkg: String,
    val label: String,
    val size: String,
    val frozen: Boolean,
    val system: Boolean,
    val running: Boolean,
    val bloat: Boolean,
)

/** Telos System → Apps hub: Freeze Desk (search, filter pills, one-tap freeze). */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SystemAppsTab(onOpenDetails: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backStack = LocalBackStack.current
    val freeze: FreezeManager = koinInject()
    val descriptions: BloatDescriptions = koinInject()
    val settings: FreezeSettings = koinInject()
    val uad by descriptions.data.collectAsStateWithLifecycle()
    val backend by freeze.activeBackend.collectAsStateWithLifecycle()
    var listView by rememberSaveable { mutableStateOf(false) }
    var selectedPackage by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var filterOrdinal by rememberSaveable { mutableIntStateOf(FreezeDeskFilter.All.ordinal) }
    val filter = FreezeDeskFilter.entries.getOrElse(filterOrdinal) { FreezeDeskFilter.All }
    var apps by remember { mutableStateOf<List<InstalledSystemRow>>(emptyList()) }
    var revision by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var busyPkg by remember { mutableStateOf<String?>(null) }
    var outcome by remember { mutableStateOf<String?>(null) }
    val fontScale = LocalDensity.current.fontScale
    val labeledActions = fontScale >= 1.3f

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { revision++ }
    LaunchedEffect(Unit) {
        descriptions.entries()
        if (settings.bloatAutoUpdate.first()) descriptions.refresh()
    }
    LaunchedEffect(revision, uad) {
        loading = true
        apps =
            withContext(de.mm20.launcher2.coroutines.TelosDispatchers.Disk) {
                freeze.refreshBackendState()
                val pm = context.packageManager
                val runningPkgs = runningPackageNames(context)
                pm.getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS)
                    .filter { it.packageName != context.packageName }
                    .map { app ->
                        val pkg = app.packageName
                        InstalledSystemRow(
                            pkg = pkg,
                            label = runCatching { app.loadLabel(pm).toString() }.getOrDefault(pkg),
                            size =
                                android.text.format.Formatter.formatFileSize(
                                    context,
                                    java.io.File(app.sourceDir).length(),
                                ),
                            frozen = freeze.isFrozen(pkg),
                            system = app.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                            running = pkg in runningPkgs,
                            bloat = uad.containsKey(pkg),
                        )
                    }
                    .sortedBy { it.label.lowercase(java.util.Locale.ROOT) }
            }
        loading = false
    }

    val frozenCount = remember(apps) { apps.count { it.frozen } }
    val visible =
        remember(apps, query, filter) {
            val term = query.trim()
            apps.filter { row ->
                val matchesFilter =
                    when (filter) {
                        FreezeDeskFilter.All -> true
                        FreezeDeskFilter.Frozen -> row.frozen
                        FreezeDeskFilter.Running -> row.running
                        FreezeDeskFilter.System -> row.system
                        FreezeDeskFilter.User -> !row.system
                        FreezeDeskFilter.Bloat -> row.bloat
                    }
                matchesFilter &&
                    (term.isBlank() ||
                        row.label.contains(term, ignoreCase = true) ||
                        row.pkg.contains(term, ignoreCase = true))
            }
        }

    val statusLine = buildString {
        append(backend?.name ?: context.getString(I18nR.string.hf_freeze_disconnected))
        append(" · ")
        append(frozenCount)
        append(' ')
        append(context.getString(I18nR.string.hf_freeze_frozen))
    }

    FreezeDeskGrid(
        apps = visible,
        listView = listView,
        onSelect = { selectedPackage = it.pkg },
        header = {
            Column {
                Column(
                    Modifier.fillMaxWidth()
                        .padding(horizontal = UiSpacing.Content, vertical = UiSpacing.Small)
                        .telosTechSurface(borderColor = TelosCyber.NeonCyan)
                        .padding(UiSpacing.Content),
                    verticalArrangement = Arrangement.spacedBy(UiSpacing.Small),
                ) {
                    Text(
                        text = stringResource(I18nR.string.freeze_desk_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    TelosDotMatrixText(
                        text = statusLine.uppercase(),
                        fontSize = 10.sp,
                        color =
                            if (backend == null) TelosCyber.NeonMagenta else TelosCyber.NeonCyan,
                    )
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = UiSpacing.TouchTarget),
                        placeholder = { Text(stringResource(I18nR.string.hf_freeze_search_apps)) },
                        leadingIcon = {
                            Icon(
                                painterResource(BaseR.drawable.search_24px),
                                contentDescription = null,
                            )
                        },
                        singleLine = true,
                        shape = MaterialTheme.shapes.extraLarge,
                    )
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FreezeDeskFilter.entries.forEach { pill ->
                            TelosTechPill(
                                text =
                                    stringResource(
                                        when (pill) {
                                            FreezeDeskFilter.All -> I18nR.string.filter_all
                                            FreezeDeskFilter.Frozen -> I18nR.string.hf_freeze_frozen
                                            FreezeDeskFilter.Running ->
                                                I18nR.string.freeze_dashboard_running_now
                                            FreezeDeskFilter.System ->
                                                I18nR.string.netfw_filter_system
                                            FreezeDeskFilter.User -> I18nR.string.netfw_filter_user
                                            FreezeDeskFilter.Bloat ->
                                                I18nR.string.freeze_desk_filter_bloat
                                        }
                                    ),
                                isSelected = filter == pill,
                                onClick = { filterOrdinal = pill.ordinal },
                                accentColor =
                                    when (pill) {
                                        FreezeDeskFilter.All -> TelosCyber.NeonCyan
                                        FreezeDeskFilter.Frozen -> TelosCyber.NeonCyan
                                        FreezeDeskFilter.Running -> TelosCyber.NeonGreen
                                        FreezeDeskFilter.System -> TelosCyber.NeonOrange
                                        FreezeDeskFilter.User -> TelosCyber.NeonGreen
                                        FreezeDeskFilter.Bloat -> TelosCyber.NeonMagenta
                                    },
                            )
                        }
                    }
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        AppOpsNextButton()
                        FilterChip(
                            selected = listView,
                            onClick = { listView = !listView },
                            label = {
                                Text(stringResource(I18nR.string.preference_grid_list_style))
                            },
                        )
                    }
                }

                outcome?.let {
                    Text(
                        it,
                        modifier =
                            Modifier.padding(horizontal = UiSpacing.Content, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                if (!loading && visible.isEmpty()) {
                    EmptyMessage(
                        stringResource(I18nR.string.hf_store_no_apps_match),
                        stringResource(I18nR.string.hf_store_change_filter),
                    )
                }
            }
        },
    )
    val selectedApp =
        remember(apps, selectedPackage) { apps.firstOrNull { it.pkg == selectedPackage } }
    selectedApp?.let { app ->
        ModalBottomSheet(
            onDismissRequest = { selectedPackage = null },
            containerColor = de.mm20.launcher2.ui.component.virtualAppPopup(),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp)
            ) {
                Column(Modifier.padding(UiSpacing.Content)) {
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            selectedPackage = null
                            onOpenDetails(app.pkg)
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(app.pkg, app.label, 48)
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(
                                app.label,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${app.pkg} · ${app.size}",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (app.frozen) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = MaterialTheme.shapes.small,
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        painterResource(BaseR.drawable.ac_unit_24px),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                    Text(
                                        stringResource(I18nR.string.hf_freeze_frozen),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }
                        }
                    }
                    uad[app.pkg]?.let { info ->
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Column(Modifier.padding(8.dp)) {
                                Text(
                                    stringResource(I18nR.string.freeze_hub_uad_help),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                                Text(
                                    info.recommendation,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                                Text(
                                    info.description,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                    FlowRow(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledTonalButton(
                            onClick = {
                                busyPkg = app.pkg
                                scope.launch {
                                    try {
                                        val ok =
                                            withContext(Dispatchers.IO) {
                                                if (app.frozen) freeze.unfreeze(app.pkg)
                                                else freeze.freeze(app.pkg)
                                            }
                                        outcome =
                                            context.getString(
                                                I18nR.string.freeze_hub_result,
                                                if (app.pkg in ok) 1 else 0,
                                                1,
                                            )
                                        revision++
                                    } finally {
                                        busyPkg = null
                                    }
                                }
                            },
                            enabled = busyPkg == null,
                            modifier =
                                Modifier.sizeIn(
                                    minWidth = UiSpacing.TouchTarget,
                                    minHeight = UiSpacing.TouchTarget,
                                ),
                        ) {
                            Icon(
                                painterResource(
                                    if (app.frozen) BaseR.drawable.play_arrow_24px
                                    else BaseR.drawable.ac_unit_24px
                                ),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.size(6.dp))
                            Text(
                                stringResource(
                                    if (app.frozen) I18nR.string.freeze_hub_unfreeze
                                    else I18nR.string.freeze_widget_freeze_now
                                )
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                selectedPackage = null
                                backStack.add(SystemAppOpsRoute(app.pkg))
                            },
                            enabled = android.os.Build.VERSION.SDK_INT >= 35,
                            modifier =
                                Modifier.sizeIn(
                                    minWidth = UiSpacing.TouchTarget,
                                    minHeight = UiSpacing.TouchTarget,
                                ),
                        ) {
                            Text(
                                if (labeledActions) {
                                    stringResource(I18nR.string.system_appopsnext_name)
                                } else {
                                    stringResource(I18nR.string.system_appopsnext_name)
                                        .substringBefore('·')
                                        .trim()
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                selectedPackage = null
                                backStack.add(NetworkPackageFirewallRoute(app.pkg))
                            },
                            modifier =
                                Modifier.sizeIn(
                                    minWidth = UiSpacing.TouchTarget,
                                    minHeight = UiSpacing.TouchTarget,
                                ),
                        ) {
                            Text(stringResource(I18nR.string.netfw_title))
                        }
                    }
                }
            }
        }
    }
}

@Suppress("DEPRECATION")
private fun runningPackageNames(context: android.content.Context): Set<String> {
    val am = context.getSystemService<ActivityManager>() ?: return emptySet()
    return runCatching { am.runningAppProcesses }
        .getOrNull()
        .orEmpty()
        .flatMap { process ->
            val pkgs = process.pkgList?.toList().orEmpty()
            if (pkgs.isNotEmpty()) pkgs else listOfNotNull(process.processName.substringBefore(':'))
        }
        .toSet()
}
