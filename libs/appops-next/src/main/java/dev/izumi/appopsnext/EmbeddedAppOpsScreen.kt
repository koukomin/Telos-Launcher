package dev.izumi.appopsnext

import android.os.Bundle
import android.content.Intent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import dev.izumi.appopsnext.monitor.AppOpsMonitorService
import dev.izumi.appopsnext.newapps.NewAppPolicyNotifier
import kotlinx.coroutines.flow.first
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.izumi.appopsnext.presentation.AppOpsRootScreen
import dev.izumi.appopsnext.presentation.app_detail.AppDetailViewModel
import dev.izumi.appopsnext.presentation.app_list.AppListViewModel
import dev.izumi.appopsnext.presentation.batch.BatchOperationUiState
import dev.izumi.appopsnext.presentation.batch.BatchOperationsViewModel
import dev.izumi.appopsnext.presentation.diagnostics.DiagnosticsViewModel
import dev.izumi.appopsnext.presentation.experimental.ExperimentalViewModel
import dev.izumi.appopsnext.presentation.experimental.WatchersViewModel
import dev.izumi.appopsnext.presentation.history.HistoryViewModel
import dev.izumi.appopsnext.presentation.permissions.PermissionBrowserViewModel
import dev.izumi.appopsnext.presentation.settings.SettingsViewModel
import dev.izumi.appopsnext.presentation.templates.TemplatesViewModel

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.LifecycleResumeEffect

@Composable
fun EmbeddedAppOpsScreen(packageName: String? = null, reportPackage: String? = null, installedAt: Long = -1) {
    val context = LocalContext.current
    val prepared by produceState<AppOpsNextApplication?>(null, context) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            AppOpsNextApplication.get(context).also {
                it.diagnosticEnvironment
                context.applicationContext.getSharedPreferences("appopsnext_host", android.content.Context.MODE_PRIVATE)
                    .edit().putBoolean("enabled", true).apply()
            }
        }
    }
    val graph = prepared
    if (graph == null) { androidx.compose.material3.CircularProgressIndicator(); return }
    val diagnosticsViewModel: DiagnosticsViewModel = viewModel()
    val appListViewModel: AppListViewModel = viewModel()
    val historyViewModel: HistoryViewModel = viewModel()
    val appDetailViewModel: AppDetailViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()
    val templatesViewModel: TemplatesViewModel = viewModel()
    val batchOperationsViewModel: BatchOperationsViewModel = viewModel()
    val permissionBrowserViewModel: PermissionBrowserViewModel = viewModel()
    val experimentalViewModel: ExperimentalViewModel = viewModel()
    val watchersViewModel: WatchersViewModel = viewModel()
    LifecycleResumeEffect(Unit) {
        graph.newAppPolicyCoordinator.onAppForeground()
        graph.historyArchiveRecorder.onAppForeground()
        appListViewModel.refreshAfterResume()
        appDetailViewModel.refreshIfReady()
        historyViewModel.setForeground(true)
        onPauseOrDispose { historyViewModel.setForeground(false) }
    }
    LaunchedEffect(reportPackage, installedAt) {
        reportPackage?.let { graph.newAppPolicyCoordinator.reportFor(it, installedAt)?.let(batchOperationsViewModel::showReport) }
    }
    LaunchedEffect(Unit) {
        if (graph.userSettingsRepository.settings.first().backgroundMonitor && !graph.appOpsMonitorController.isRunning) {
            runCatching { AppOpsMonitorService.start(context) }
        }
    }
            val diagnosticsUiState =
                diagnosticsViewModel.uiState.collectAsStateWithLifecycle()
            val appListUiState =
                appListViewModel.uiState.collectAsStateWithLifecycle()
            val historyUiState =
                historyViewModel.uiState.collectAsStateWithLifecycle()
            val appDetailUiState =
                appDetailViewModel.uiState.collectAsStateWithLifecycle()
            val appOpModeChangeUiState =
                appDetailViewModel.modeChangeState.collectAsStateWithLifecycle()
            val settingsUiState =
                settingsViewModel.uiState.collectAsStateWithLifecycle()
            val templatesUiState =
                templatesViewModel.uiState.collectAsStateWithLifecycle()
            val batchOperationUiState =
                batchOperationsViewModel.uiState.collectAsStateWithLifecycle()
            val appOpSearchQuery =
                appDetailViewModel.searchQuery.collectAsStateWithLifecycle()
            val permissionBrowserState =
                permissionBrowserViewModel.state.collectAsStateWithLifecycle()
            val permissionModeChangeState =
                permissionBrowserViewModel.change.collectAsStateWithLifecycle()
            val experimentalUiState =
                experimentalViewModel.uiState.collectAsStateWithLifecycle()
            val watchersUiState = watchersViewModel.uiState.collectAsStateWithLifecycle()
            val monitorLogEntries = experimentalViewModel.logEntries.collectAsStateWithLifecycle()

                AppOpsRootScreen(
                    initialPackage = packageName,
                    diagnosticsUiState = diagnosticsUiState.value,
                    historyUiState = historyUiState.value,
                    appListUiState = appListUiState.value,
                    appDetailUiState = appDetailUiState.value,
                    appOpModeChangeUiState = appOpModeChangeUiState.value,
                    settingsUiState = settingsUiState.value,
                    templatesUiState = templatesUiState.value,
                    batchOperationUiState = batchOperationUiState.value,
                    appOpSearchQuery = appOpSearchQuery.value,
                    permissionBrowserState = permissionBrowserState.value,
                    permissionModeChangeState = permissionModeChangeState.value,
                    onPermissionSelected = permissionBrowserViewModel::select,
                    onRefreshPermission = permissionBrowserViewModel::refresh,
                    onPermissionModeChangeRequested = permissionBrowserViewModel::request,
                    onPermissionModeChangeConfirmed = permissionBrowserViewModel::confirm,
                    onPermissionModeChangeDismissed = permissionBrowserViewModel::dismiss,
                    onPermissionDenyNoticeDismissed = permissionBrowserViewModel::dismissDenyNotice,
                    onPermissionFlagNoticeDismissedForBrowser = permissionBrowserViewModel::dismissFlagNotice,
                    onPermissionForegroundRequested = permissionBrowserViewModel::requestForeground,
                    onOperationBatchRequested = batchOperationsViewModel::requestOperationBatch,
                    experimentalUiState = experimentalUiState.value,
                    // Read only where the log is shown: it changes every couple of seconds
                    // while the monitor is busy, which must not recompose every other page.
                    monitorLogEntries = { monitorLogEntries.value },
                    onClearMonitorLog = experimentalViewModel::clearLog,
                    watchersUiState = watchersUiState.value,
                    onRefreshWatchers = watchersViewModel::refresh,
                    onMonitorEnabledChange =
                        experimentalViewModel::setMonitorEnabled,
                    onEnableUnconfirmedMonitor =
                        experimentalViewModel::enableUnconfirmedMonitor,
                    onMonitorHeadsUpChange =
                        experimentalViewModel::setHeadsUp,
                    onCheckForUpdate =
                        settingsViewModel::checkForUpdate,
                    onOpenBatterySettings = { runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } },
                    onDismissBatteryNotice =
                        experimentalViewModel::dismissBatteryNotice,
                    onRefreshBatteryExemption =
                        experimentalViewModel::refreshBatteryExemption,
                    onMonitorOperationsChange =
                        experimentalViewModel::setOperations,
                    onMonitorThrottleChange =
                        experimentalViewModel::setThrottle,
                    onMonitorPointHeadsUpChange =
                        experimentalViewModel::setPointHeadsUp,
                    onMonitorPointOutcomesChange =
                        experimentalViewModel::setPointOutcomes,
                    onMonitorPointBackgroundOnlyChange =
                        experimentalViewModel::setPointBackgroundOnly,
                    onMonitorShowAllOperationsChange =
                        experimentalViewModel::setShowAllOperations,
                    onMonitorSuppressAllOperationsWarning =
                        experimentalViewModel::suppressAllOperationsWarning,
                    onShizukuAction =
                        diagnosticsViewModel::performShizukuAction,
                    onPrivilegedServiceRetry =
                        diagnosticsViewModel::retryPrivilegedService,
                    onClearDiagnosticLog =
                        diagnosticsViewModel::clearDiagnosticLog,
                    onAppSearchQueryChange = appListViewModel::updateSearchQuery,
                    onRefreshApps = appListViewModel::refresh,
                    onRefreshHistory = historyViewModel::refresh,
                    onHistoryVisibilityChanged = historyViewModel::setVisible,
                    onHistoryPermissionsChanged =
                        historyViewModel::setPermissions,
                    onHistoryPermissionOrderChanged =
                        historyViewModel::setPermissionOrder,
                    onAppSelected = appDetailViewModel::selectApp,
                    onRefreshAppDetail = appDetailViewModel::refresh,
                    onAppOpSearchQueryChange =
                        appDetailViewModel::updateSearchQuery,
                    onAppOpModeChangeRequested =
                        appDetailViewModel::requestModeChange,
                    onAppOpModeChangeConfirmed =
                        appDetailViewModel::confirmModeChange,
                    onAppOpModeChangeDismissed =
                        appDetailViewModel::dismissModeChange,
                    onDenyFallbackNoticeDismissed =
                        appDetailViewModel::dismissDenyFallbackNotice,
                    onPermissionFlagNoticeDismissed =
                        appDetailViewModel::dismissPermissionFlagNotice,
                    onForegroundAlternativeRequested =
                        appDetailViewModel::requestForegroundAlternative,
                    onHideSystemAppsChange =
                        settingsViewModel::setHideSystemApps,
                    onSaveIndividualHistoryChange =
                        settingsViewModel::setSaveIndividualHistory,
                    onSavedHistoryOperationChange =
                        settingsViewModel::setHistoryOperationSaved,
                    countSavedHistory =
                        settingsViewModel::countSavedHistory,
                    onDeleteSavedHistory =
                        settingsViewModel::deleteSavedHistory,
                    onAppLanguageChange =
                        settingsViewModel::setAppLanguage,
                    onCreateTemplate = templatesViewModel::createTemplate,
                    onSelectTemplate = templatesViewModel::selectTemplate,
                    onCloseTemplateEditor = templatesViewModel::closeEditor,
                    onDeleteTemplate = templatesViewModel::deleteTemplate,
                    onTemplateRuleModeChange =
                        templatesViewModel::setRuleMode,
                    onTemplateRuleSelectionChange =
                        templatesViewModel::setRuleSelection,
                    onTemplateRuleOrderChange =
                        templatesViewModel::setRuleOrder,
                    onAutoApplyNewAppTemplateChange =
                        templatesViewModel::setAutoApplyNewAppTemplate,
                    onTemplateApplyRequested =
                        batchOperationsViewModel::requestTemplateApplication,
                    onPermissionBatchRequested =
                        batchOperationsViewModel::requestPermissionBatch,
                    onBatchOperationConfirm =
                        batchOperationsViewModel::confirm,
                    onBatchOperationDismiss = {
                        // Cancelling a confirmation changed nothing, so only
                        // closing a finished report needs a reload.
                        val finished = batchOperationsViewModel.uiState.value is
                            BatchOperationUiState.Finished
                        batchOperationsViewModel.dismiss()
                        if (finished && permissionBrowserState.value.operation == null) {
                            appDetailViewModel.refresh()
                        }
                    },
                )
}
