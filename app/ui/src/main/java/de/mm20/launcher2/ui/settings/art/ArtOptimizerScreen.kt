package de.mm20.launcher2.ui.settings.art
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.appmanagement.art.AuditedAppEntry
import de.mm20.launcher2.appmanagement.art.OptimizationBucket
import de.mm20.launcher2.appmanagement.art.ProfileDiagnosticResult
import de.mm20.launcher2.appmanagement.art.TelosArtOptimizer
import de.mm20.launcher2.preferences.ArtBenchmarkRecord
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.TelosSearchBar
import de.mm20.launcher2.ui.component.preferences.CheckboxPreference
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
data object ArtOptimizerRoute : NavKey

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ArtOptimizerScreen() {
    val context = LocalContext.current
    val viewModel: ArtOptimizerViewModel = viewModel()

    val masterEnabled by viewModel.masterEnabled.collectAsStateWithLifecycle(false)
    val selectedProfile by viewModel.profile.collectAsStateWithLifecycle(TelosArtOptimizer.PROFILE_SPEED_PROFILE)
    val fallbackEmptyProf by viewModel.fallbackEmptyProfileToSpeed.collectAsStateWithLifecycle(true)
    val storePostInstall by viewModel.storePostInstall.collectAsStateWithLifecycle(false)
    val skipFrozenApps by viewModel.skipFrozenApps.collectAsStateWithLifecycle(false)
    val frozenAppsMode by viewModel.frozenAppsMode.collectAsStateWithLifecycle(TelosArtOptimizer.FROZEN_MODE_SKIP)
    val sqliteEnabled by viewModel.sqliteOptimizeEnabled.collectAsStateWithLifecycle(false)

    val scheduled by viewModel.scheduled.collectAsStateWithLifecycle(false)
    val frequencyDays by viewModel.frequencyDays.collectAsStateWithLifecycle(1L)
    val requireCharging by viewModel.requireCharging.collectAsStateWithLifecycle(true)
    val requireIdle by viewModel.requireIdle.collectAsStateWithLifecycle(true)
    val requireBatteryNotLow by viewModel.requireBatteryNotLow.collectAsStateWithLifecycle(true)
    val skipPowerSave by viewModel.skipPowerSave.collectAsStateWithLifecycle(true)

    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val sqliteState by viewModel.sqliteState.collectAsStateWithLifecycle()
    val auditReport by viewModel.auditReport.collectAsStateWithLifecycle()
    val isScanningAudit by viewModel.isScanningAudit.collectAsStateWithLifecycle()
    val privilegedAvailable by viewModel.privilegedAvailable.collectAsStateWithLifecycle()
    val benchmarkRecords by viewModel.benchmarkRecords.collectAsStateWithLifecycle(emptyMap())

    val selectedApp by viewModel.selectedApp.collectAsStateWithLifecycle()
    val selectedAppDiag by viewModel.selectedAppDiagnostic.collectAsStateWithLifecycle()
    val isBenchmarking by viewModel.isBenchmarking.collectAsStateWithLifecycle()
    val benchmarkError by viewModel.benchmarkError.collectAsStateWithLifecycle()
    val isSingleAppBusy by viewModel.isSingleAppBusy.collectAsStateWithLifecycle()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var activeFilterName by rememberSaveable { mutableStateOf(ArtAppFilter.ALL.name) }
    val activeFilter = remember(activeFilterName) {
        ArtAppFilter.entries.firstOrNull { it.name == activeFilterName } ?: ArtAppFilter.ALL
    }
    var showOverflowMenu by remember { mutableStateOf(false) }

    LaunchedEffect(masterEnabled) {
        if (masterEnabled) {
            viewModel.refreshAudit(context)
        }
    }

    LifecycleResumeEffect(masterEnabled) {
        if (masterEnabled) {
            viewModel.refreshAudit(context)
        }
        onPauseOrDispose { }
    }

    val filteredApps = remember(auditReport, searchQuery, activeFilter) {
        val base = auditReport?.allEntries.orEmpty()
        val q = searchQuery.trim().lowercase()
        base.filter { entry ->
            val matchesFilter = when (activeFilter) {
                ArtAppFilter.ALL -> true
                ArtAppFilter.NEEDS_OPTIMIZATION ->
                    entry.bucket == OptimizationBucket.NEEDS_OPTIMIZATION ||
                        entry.bucket == OptimizationBucket.DEGRADED
                ArtAppFilter.UP_TO_DATE -> entry.bucket == OptimizationBucket.UP_TO_DATE
                ArtAppFilter.DEGRADED -> entry.bucket == OptimizationBucket.DEGRADED
                ArtAppFilter.FROZEN -> entry.isFrozen
            }
            val matchesQuery = q.isEmpty() ||
                entry.label.lowercase().contains(q) ||
                entry.packageName.lowercase().contains(q)
            matchesFilter && matchesQuery
        }
    }

    PreferenceScreen(
        title = { Text(stringResource(I18nR.string.art_optimizer_title)) },
        topBarActions = {
            if (masterEnabled) {
                IconButton(
                    onClick = { viewModel.refreshAudit(context) },
                    enabled = !isScanningAudit && !progress.isRunning,
                ) {
                    Icon(
                        painter = painterResource(BaseR.drawable.autorenew_24px),
                        contentDescription = stringResource(I18nR.string.art_optimizer_audit_refresh),
                    )
                }
                Box {
                    IconButton(onClick = { showOverflowMenu = true }) {
                        Icon(
                            painter = painterResource(BaseR.drawable.more_vert_24px),
                            contentDescription = null,
                        )
                    }
                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false },
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(I18nR.string.art_optimizer_btn_force_recompile)) },
                            enabled = !progress.isRunning && privilegedAvailable,
                            onClick = {
                                showOverflowMenu = false
                                viewModel.startForceRecompileAll(
                                    context = context,
                                    selectedProfile = selectedProfile,
                                    skipFrozen = skipFrozenApps,
                                    frozenMode = frozenAppsMode,
                                    fallbackEmpty = fallbackEmptyProf,
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(I18nR.string.art_optimizer_btn_reset_all)) },
                            enabled = !progress.isRunning && privilegedAvailable,
                            onClick = {
                                showOverflowMenu = false
                                viewModel.resetAllOptimizations(context)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(I18nR.string.art_optimizer_btn_trim_bg)) },
                            enabled = !progress.isRunning && privilegedAvailable,
                            onClick = {
                                showOverflowMenu = false
                                viewModel.trimBgDexopt(context)
                            }
                        )
                    }
                }
            }
        }
    ) {
        item(key = "master_toggle") {
            PreferenceCategory {
                SwitchPreference(
                    title = stringResource(I18nR.string.art_optimizer_master_toggle),
                    summary = stringResource(I18nR.string.art_optimizer_summary),
                    value = masterEnabled,
                    onValueChanged = { viewModel.setMasterEnabled(context, it) }
                )
            }
        }

        if (masterEnabled) {
            if (!privilegedAvailable) {
                item(key = "shizuku_banner") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                painter = painterResource(BaseR.drawable.warning_24px),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(24.dp),
                            )
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = stringResource(I18nR.string.art_optimizer_shizuku_required_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = stringResource(I18nR.string.art_optimizer_shizuku_required_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }

            item(key = "audit_dashboard") {
                AuditDashboardCard(
                    auditReport = auditReport,
                    isScanning = isScanningAudit,
                    progress = progress,
                    privilegedAvailable = privilegedAvailable,
                    onRefresh = { viewModel.refreshAudit(context) },
                    onSmartOptimize = {
                        viewModel.startSmartOptimization(
                            context = context,
                            selectedProfile = selectedProfile,
                            skipFrozen = skipFrozenApps,
                            frozenMode = frozenAppsMode,
                            fallbackEmpty = fallbackEmptyProf,
                        )
                    },
                    onForceRecompileAll = {
                        viewModel.startForceRecompileAll(
                            context = context,
                            selectedProfile = selectedProfile,
                            skipFrozen = skipFrozenApps,
                            frozenMode = frozenAppsMode,
                            fallbackEmpty = fallbackEmptyProf,
                        )
                    },
                    onCancel = { viewModel.cancelOptimization() },
                )
            }

            item(key = "tradeoff_card") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(BaseR.drawable.info_24px),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(
                                text = stringResource(I18nR.string.art_optimizer_warning_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(
                            text = stringResource(I18nR.string.art_optimizer_warning_pros),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(I18nR.string.art_optimizer_warning_cons),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            item(key = "profile_category") {
                PreferenceCategory(stringResource(I18nR.string.art_optimizer_profile_title)) {
                    ListPreference(
                        title = stringResource(I18nR.string.art_optimizer_profile_title),
                        value = selectedProfile,
                        items = listOf(
                            stringResource(I18nR.string.art_optimizer_profile_speed_profile) to TelosArtOptimizer.PROFILE_SPEED_PROFILE,
                            stringResource(I18nR.string.art_optimizer_profile_speed) to TelosArtOptimizer.PROFILE_SPEED,
                            stringResource(I18nR.string.art_optimizer_profile_everything) to TelosArtOptimizer.PROFILE_EVERYTHING,
                        ),
                        onValueChanged = { if (it != null) viewModel.setProfile(context, it) }
                    )

                    SwitchPreference(
                        title = stringResource(I18nR.string.art_optimizer_fallback_empty_prof),
                        summary = stringResource(I18nR.string.art_optimizer_fallback_empty_prof_summary),
                        value = fallbackEmptyProf,
                        onValueChanged = { viewModel.setFallbackEmptyProfileToSpeed(it) }
                    )

                    SwitchPreference(
                        title = stringResource(I18nR.string.art_optimizer_store_post_install),
                        summary = stringResource(I18nR.string.art_optimizer_store_post_install_summary),
                        value = storePostInstall,
                        onValueChanged = { viewModel.setStorePostInstall(it) }
                    )
                }
            }

            item(key = "frozen_synergy_category") {
                PreferenceCategory(stringResource(I18nR.string.art_optimizer_frozen_category)) {
                    SwitchPreference(
                        title = stringResource(I18nR.string.art_optimizer_frozen_toggle),
                        summary = stringResource(I18nR.string.art_optimizer_frozen_toggle_summary),
                        value = skipFrozenApps,
                        onValueChanged = { viewModel.setSkipFrozenApps(context, it) }
                    )

                    AnimatedVisibility(skipFrozenApps) {
                        Column {
                            ListPreference(
                                title = stringResource(I18nR.string.art_optimizer_frozen_mode_title),
                                value = frozenAppsMode,
                                items = listOf(
                                    stringResource(I18nR.string.art_optimizer_frozen_mode_skip) to TelosArtOptimizer.FROZEN_MODE_SKIP,
                                    stringResource(I18nR.string.art_optimizer_frozen_mode_strip) to TelosArtOptimizer.FROZEN_MODE_STRIP_RESET,
                                ),
                                onValueChanged = { if (it != null) viewModel.setFrozenAppsMode(it) }
                            )

                            if (frozenAppsMode == TelosArtOptimizer.FROZEN_MODE_STRIP_RESET) {
                                OutlinedButton(
                                    onClick = { viewModel.stripFrozenAppsNow(context) },
                                    enabled = !progress.isRunning && privilegedAvailable,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                ) {
                                    Text(stringResource(I18nR.string.art_optimizer_btn_strip_frozen_now))
                                }
                            }
                        }
                    }
                }
            }

            item(key = "sqlite_category") {
                PreferenceCategory(stringResource(I18nR.string.art_optimizer_sqlite_category)) {
                    SwitchPreference(
                        title = stringResource(I18nR.string.art_optimizer_sqlite_toggle),
                        summary = stringResource(I18nR.string.art_optimizer_sqlite_toggle_summary),
                        value = sqliteEnabled,
                        onValueChanged = { viewModel.setSqliteOptimizeEnabled(it) }
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (sqliteState.isRunning) {
                            Text(
                                text = stringResource(I18nR.string.art_optimizer_sqlite_running_fmt, sqliteState.currentTarget),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        } else {
                            if (sqliteState.completed) {
                                val formattedBytes = Formatter.formatFileSize(context, sqliteState.bytesReclaimed)
                                Text(
                                    text = stringResource(
                                        I18nR.string.art_optimizer_sqlite_done_fmt,
                                        sqliteState.databasesOptimized,
                                        formattedBytes,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                            OutlinedButton(
                                onClick = { viewModel.runSqliteVacuumNow(context) },
                                enabled = !progress.isRunning,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp),
                            ) {
                                Text(stringResource(I18nR.string.art_optimizer_sqlite_btn_run))
                            }
                        }
                    }
                }
            }

            item(key = "scheduler_category") {
                PreferenceCategory(stringResource(I18nR.string.art_optimizer_scheduler_title)) {
                    SwitchPreference(
                        title = stringResource(I18nR.string.art_optimizer_scheduler_enable),
                        value = scheduled,
                        onValueChanged = {
                            viewModel.setScheduled(context, it, frequencyDays, requireCharging, requireIdle, requireBatteryNotLow)
                        }
                    )

                    AnimatedVisibility(scheduled) {
                        Column {
                            ListPreference(
                                title = stringResource(I18nR.string.art_optimizer_scheduler_frequency),
                                value = frequencyDays,
                                items = listOf(
                                    stringResource(I18nR.string.art_optimizer_freq_daily) to 1L,
                                    stringResource(I18nR.string.art_optimizer_freq_weekly) to 7L,
                                    stringResource(I18nR.string.art_optimizer_freq_monthly) to 30L,
                                ),
                                onValueChanged = {
                                    if (it != null) {
                                        viewModel.setFrequencyDays(context, it, requireCharging, requireIdle, requireBatteryNotLow)
                                    }
                                }
                            )

                            CheckboxPreference(
                                title = stringResource(I18nR.string.art_optimizer_constraint_charging),
                                value = requireCharging,
                                onValueChanged = {
                                    viewModel.setRequireCharging(context, it, frequencyDays, requireIdle, requireBatteryNotLow)
                                }
                            )

                            CheckboxPreference(
                                title = stringResource(I18nR.string.art_optimizer_constraint_idle),
                                value = requireIdle,
                                onValueChanged = {
                                    viewModel.setRequireIdle(context, it, frequencyDays, requireCharging, requireBatteryNotLow)
                                }
                            )

                            CheckboxPreference(
                                title = stringResource(I18nR.string.art_optimizer_constraint_battery_low),
                                value = requireBatteryNotLow,
                                onValueChanged = {
                                    viewModel.setRequireBatteryNotLow(context, it, frequencyDays, requireCharging, requireIdle)
                                }
                            )

                            CheckboxPreference(
                                title = stringResource(I18nR.string.art_optimizer_constraint_power_saver),
                                value = skipPowerSave,
                                onValueChanged = { viewModel.setSkipPowerSave(it) }
                            )
                        }
                    }
                }
            }

            item(key = "per_app_header") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(I18nR.string.art_optimizer_apps_category),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    )
                    TelosSearchBar(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = stringResource(I18nR.string.art_optimizer_search_apps_hint),
                        filters = {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FilterChip(
                                    selected = activeFilter == ArtAppFilter.ALL,
                                    onClick = { activeFilterName = ArtAppFilter.ALL.name },
                                    label = { Text(stringResource(I18nR.string.art_optimizer_filter_all)) },
                                )
                                FilterChip(
                                    selected = activeFilter == ArtAppFilter.NEEDS_OPTIMIZATION,
                                    onClick = { activeFilterName = ArtAppFilter.NEEDS_OPTIMIZATION.name },
                                    label = { Text(stringResource(I18nR.string.art_optimizer_filter_needed)) },
                                )
                                FilterChip(
                                    selected = activeFilter == ArtAppFilter.UP_TO_DATE,
                                    onClick = { activeFilterName = ArtAppFilter.UP_TO_DATE.name },
                                    label = { Text(stringResource(I18nR.string.art_optimizer_filter_optimized)) },
                                )
                                FilterChip(
                                    selected = activeFilter == ArtAppFilter.DEGRADED,
                                    onClick = { activeFilterName = ArtAppFilter.DEGRADED.name },
                                    label = { Text(stringResource(I18nR.string.art_optimizer_filter_degraded)) },
                                )
                                FilterChip(
                                    selected = activeFilter == ArtAppFilter.FROZEN,
                                    onClick = { activeFilterName = ArtAppFilter.FROZEN.name },
                                    label = { Text(stringResource(I18nR.string.art_optimizer_filter_frozen)) },
                                )
                            }
                        }
                    )
                }
            }

            items(
                items = filteredApps,
                key = { "app_item_${it.packageName}" },
            ) { appEntry ->
                AuditedAppRow(
                    entry = appEntry,
                    benchmark = benchmarkRecords[appEntry.packageName],
                    onClick = { viewModel.selectApp(context, appEntry) },
                )
            }
        }
    }

    val currentSelected = selectedApp
    if (currentSelected != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { viewModel.selectApp(context, null) },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            PerAppInspectorSheetContent(
                entry = currentSelected,
                diagnostic = selectedAppDiag,
                benchmarkRecord = benchmarkRecords[currentSelected.packageName],
                isBenchmarking = isBenchmarking,
                benchmarkError = benchmarkError,
                isCompiling = isSingleAppBusy,
                selectedProfile = selectedProfile,
                privilegedAvailable = privilegedAvailable,
                onBenchmark = {
                    viewModel.runBenchmarkForApp(
                        context = context,
                        packageName = currentSelected.packageName,
                        currentFilter = currentSelected.currentFilter,
                    )
                },
                onClearBenchmark = { viewModel.clearBenchmarkScore(currentSelected.packageName) },
                onCompileSelectedProfile = {
                    viewModel.compileSingleApp(
                        context = context,
                        packageName = currentSelected.packageName,
                        targetProfile = selectedProfile,
                        fallbackEmpty = fallbackEmptyProf,
                    )
                },
                onCompileFullSpeed = {
                    viewModel.compileSingleApp(
                        context = context,
                        packageName = currentSelected.packageName,
                        targetProfile = TelosArtOptimizer.PROFILE_SPEED,
                        fallbackEmpty = false,
                    )
                },
                onResetApp = {
                    viewModel.compileSingleApp(
                        context = context,
                        packageName = currentSelected.packageName,
                        targetProfile = TelosArtOptimizer.PROFILE_RESET,
                        fallbackEmpty = false,
                    )
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AuditDashboardCard(
    auditReport: de.mm20.launcher2.appmanagement.art.StartupAuditReport?,
    isScanning: Boolean,
    progress: de.mm20.launcher2.appmanagement.art.OptimizationProgress,
    privilegedAvailable: Boolean,
    onRefresh: () -> Unit,
    onSmartOptimize: () -> Unit,
    onForceRecompileAll: () -> Unit,
    onCancel: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        painter = painterResource(BaseR.drawable.speed_24px),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        text = stringResource(I18nR.string.art_optimizer_audit_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (!isScanning && !progress.isRunning) {
                    TextButton(
                        onClick = onRefresh,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(I18nR.string.art_optimizer_audit_refresh))
                    }
                }
            }

            if (isScanning && auditReport == null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(
                        text = stringResource(I18nR.string.art_optimizer_audit_scanning),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else if (auditReport != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource(
                                I18nR.string.art_optimizer_audit_summary_fmt,
                                auditReport.upToDate.size,
                                auditReport.totalActionableCount,
                                auditReport.estimatedMinutesSaved,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (auditReport.degraded.isNotEmpty()) {
                            Text(
                                text = stringResource(I18nR.string.art_optimizer_audit_degraded_fmt, auditReport.degraded.size),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        } else if (auditReport.totalActionableCount == 0 && auditReport.privilegedAvailable) {
                            Text(
                                text = stringResource(I18nR.string.art_optimizer_audit_all_done),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            if (progress.isRunning) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(
                            I18nR.string.art_optimizer_progress_fmt,
                            progress.currentPackage,
                            progress.currentIndex,
                            progress.totalPackages,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    LinearProgressIndicator(
                        progress = {
                            if (progress.totalPackages > 0) {
                                progress.currentIndex.toFloat() / progress.totalPackages
                            } else {
                                0f
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .align(Alignment.End)
                            .heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(I18nR.string.art_optimizer_btn_cancel))
                    }
                }
            } else {
                val hasWork = (auditReport?.totalActionableCount ?: 1) > 0
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onSmartOptimize,
                        enabled = privilegedAvailable && hasWork && !isScanning,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(I18nR.string.art_optimizer_btn_smart_optimize))
                    }
                    OutlinedButton(
                        onClick = onForceRecompileAll,
                        enabled = privilegedAvailable && !isScanning,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(I18nR.string.art_optimizer_btn_force_recompile))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AuditedAppRow(
    entry: AuditedAppEntry,
    benchmark: ArtBenchmarkRecord?,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = entry.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BucketBadge(entry = entry)
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "[status=${entry.currentFilter}]",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (entry.isFrozen) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Text(
                            text = stringResource(I18nR.string.art_optimizer_filter_frozen),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                if (benchmark?.beforeMs != null) {
                    BenchmarkInlineBadge(record = benchmark)
                }
            }
        }
    }
}

@Composable
private fun BucketBadge(entry: AuditedAppEntry) {
    val (bg, fg, labelRes) = when (entry.bucket) {
        OptimizationBucket.UP_TO_DATE -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            I18nR.string.art_optimizer_bucket_up_to_date,
        )
        OptimizationBucket.DEGRADED -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            I18nR.string.art_optimizer_bucket_degraded,
        )
        OptimizationBucket.FROZEN_SKIPPED -> Triple(
            MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.onSurfaceVariant,
            I18nR.string.art_optimizer_bucket_frozen_skipped,
        )
        OptimizationBucket.NEEDS_OPTIMIZATION -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            I18nR.string.art_optimizer_bucket_needs_opt,
        )
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bg,
        contentColor = fg,
    ) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun BenchmarkInlineBadge(record: ArtBenchmarkRecord) {
    val before = record.beforeMs ?: return
    val after = record.afterMs
    val text = if (after != null && before > 0L) {
        val gainPct = (((before - after).toDouble() / before.toDouble()) * 100.0).roundToInt()
        if (gainPct > 0) {
            stringResource(I18nR.string.art_optimizer_benchmark_comparison_fmt, before, after, gainPct)
        } else {
            stringResource(I18nR.string.art_optimizer_benchmark_slower_fmt, before, after)
        }
    } else {
        "${before}ms"
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PerAppInspectorSheetContent(
    entry: AuditedAppEntry,
    diagnostic: ProfileDiagnosticResult?,
    benchmarkRecord: ArtBenchmarkRecord?,
    isBenchmarking: Boolean,
    benchmarkError: BenchmarkUiError?,
    isCompiling: Boolean,
    selectedProfile: String,
    privilegedAvailable: Boolean,
    onBenchmark: () -> Unit,
    onClearBenchmark: () -> Unit,
    onCompileSelectedProfile: () -> Unit,
    onCompileFullSpeed: () -> Unit,
    onResetApp: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = entry.label,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = entry.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(
                    I18nR.string.art_optimizer_sheet_status_fmt,
                    entry.currentFilter,
                    entry.compilationReason,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        // 1. Baseline & Cloud Profile Diagnostic Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (diagnostic?.recommendsFullSpeedFallback == true) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                contentColor = if (diagnostic?.recommendsFullSpeedFallback == true) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            ),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(I18nR.string.art_optimizer_profile_diag_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                if (diagnostic == null) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    val yesStr = stringResource(I18nR.string.art_optimizer_yes)
                    val noStr = stringResource(I18nR.string.art_optimizer_no)
                    Text(
                        text = stringResource(
                            I18nR.string.art_optimizer_profile_diag_details_fmt,
                            if (diagnostic.hasEmbeddedBaselineProf) yesStr else noStr,
                            if (diagnostic.hasDexMetadataDm) yesStr else noStr,
                            diagnostic.refProfileBytes,
                            diagnostic.curProfileBytes,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        text = if (diagnostic.hasUsableProfile) {
                            stringResource(I18nR.string.art_optimizer_profile_diag_ok)
                        } else {
                            stringResource(I18nR.string.art_optimizer_profile_diag_empty_warn)
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        // 2. Cold-Start Launch Benchmark Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(I18nR.string.art_optimizer_benchmark_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(I18nR.string.art_optimizer_benchmark_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                val before = benchmarkRecord?.beforeMs
                val after = benchmarkRecord?.afterMs
                if (before != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        val summary = if (after != null && before > 0L) {
                            val gainPct = (((before - after).toDouble() / before.toDouble()) * 100.0).roundToInt()
                            if (gainPct > 0) {
                                stringResource(I18nR.string.art_optimizer_benchmark_comparison_fmt, before, after, gainPct)
                            } else {
                                stringResource(I18nR.string.art_optimizer_benchmark_slower_fmt, before, after)
                            }
                        } else {
                            stringResource(
                                I18nR.string.art_optimizer_benchmark_before_only_fmt,
                                before,
                                benchmarkRecord.waitBeforeMs ?: before,
                            )
                        }
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }

                if (benchmarkError != null) {
                    val errText = when (benchmarkError) {
                        BenchmarkUiError.FROZEN -> stringResource(I18nR.string.art_optimizer_benchmark_err_frozen)
                        BenchmarkUiError.NO_ACTIVITY -> stringResource(I18nR.string.art_optimizer_benchmark_err_no_activity)
                        BenchmarkUiError.FAILED -> stringResource(I18nR.string.art_optimizer_benchmark_err_failed)
                    }
                    Text(
                        text = errText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onBenchmark,
                        enabled = privilegedAvailable && !isBenchmarking && !isCompiling,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                    ) {
                        if (isBenchmarking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(I18nR.string.art_optimizer_benchmark_running))
                        } else {
                            Text(stringResource(I18nR.string.art_optimizer_benchmark_btn_run))
                        }
                    }

                    if (before != null) {
                        OutlinedButton(
                            onClick = onClearBenchmark,
                            enabled = !isBenchmarking,
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text(stringResource(I18nR.string.art_optimizer_benchmark_btn_reset))
                        }
                    }
                }
            }
        }

        HorizontalDivider()

        // 3. Per-App Compilation Actions
        if (isCompiling) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onCompileFullSpeed,
                enabled = privilegedAvailable && !isCompiling && !isBenchmarking,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
            ) {
                Text(stringResource(I18nR.string.art_optimizer_app_btn_optimize_speed))
            }

            if (selectedProfile != TelosArtOptimizer.PROFILE_SPEED) {
                OutlinedButton(
                    onClick = onCompileSelectedProfile,
                    enabled = privilegedAvailable && !isCompiling && !isBenchmarking,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(I18nR.string.art_optimizer_app_btn_optimize_selected, selectedProfile))
                }
            }

            OutlinedButton(
                onClick = onResetApp,
                enabled = privilegedAvailable && !isCompiling && !isBenchmarking,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(I18nR.string.art_optimizer_app_btn_reset))
            }
        }

        Spacer(Modifier.size(12.dp))
    }
}
