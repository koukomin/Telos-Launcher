package de.mm20.launcher2.ui.backup
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR


import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.TelosCyber
import de.mm20.launcher2.ui.component.TelosCyberAppBarTitle
import de.mm20.launcher2.ui.component.TelosCyberSectionLabel
import de.mm20.launcher2.ui.component.TelosTechPill
import de.mm20.launcher2.ui.component.VirtualAppWindow
import de.mm20.launcher2.ui.component.telosTechSurface
import kotlinx.serialization.Serializable

@Serializable
data object BackupRoute : NavKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelosBackupScreen() {
    val context = LocalContext.current
    val viewModel: TelosBackupViewModel = viewModel()

    val appList by viewModel.appList.collectAsStateWithLifecycle()
    val systemDataList by viewModel.systemDataList.collectAsStateWithLifecycle()
    val jobProgress by viewModel.jobProgress.collectAsStateWithLifecycle()
    val shizukuAvailable by viewModel.shizukuAvailable.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        viewModel.checkShizuku()
    }

    VirtualAppWindow {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        TelosCyberAppBarTitle(
                            title = stringResource(I18nR.string.preference_backup),
                            status = stringResource(
                                if (shizukuAvailable) I18nR.string.net_hub_status else I18nR.string.telos_backup_shizuku_required
                            ),
                            statusColor = if (shizukuAvailable) TelosCyber.NeonCyan else TelosCyber.NeonMagenta,
                        )
                    },
                    actions = {
                        IconButton(onClick = { /* Open Settings */ }) {
                            Icon(painterResource(BaseR.drawable.settings_24px), contentDescription = stringResource(I18nR.string.hc_settings))
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { /* Start batch backup */ },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(painterResource(BaseR.drawable.save_24px), contentDescription = stringResource(I18nR.string.telos_backup_all))
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (!shizukuAvailable) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .telosTechSurface(borderColor = TelosCyber.NeonMagenta, shape = RoundedCornerShape(12.dp))
                            .padding(16.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(BaseR.drawable.warning_24px), contentDescription = null, tint = TelosCyber.NeonMagenta)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(stringResource(I18nR.string.telos_backup_shizuku_required), fontWeight = FontWeight.Bold)
                                Text(stringResource(I18nR.string.telos_backup_shizuku_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                if (jobProgress.inProgress) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .telosTechSurface(borderColor = TelosCyber.NeonCyan, shape = RoundedCornerShape(12.dp))
                            .padding(16.dp),
                    ) {
                        TelosCyberSectionLabel(jobProgress.currentTask, accent = TelosCyber.NeonCyan)
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { jobProgress.progress },
                            modifier = Modifier.fillMaxWidth(),
                            color = TelosCyber.NeonCyan,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("${jobProgress.completedItems} / ${jobProgress.totalItems}", style = MaterialTheme.typography.labelSmall)
                    }
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TelosTechPill(
                        text = stringResource(I18nR.string.telos_backup_tab_apps, appList.size),
                        isSelected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        accentColor = TelosCyber.NeonGreen,
                    )
                    TelosTechPill(
                        text = stringResource(I18nR.string.telos_backup_tab_system),
                        isSelected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        accentColor = TelosCyber.NeonCyan,
                    )
                    TelosTechPill(
                        text = stringResource(I18nR.string.telos_backup_tab_schedules),
                        isSelected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        accentColor = TelosCyber.NeonOrange,
                    )
                }

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (selectedTab) {
                        0 -> AppListTab(appList)
                        1 -> SystemDataTab(systemDataList)
                        2 -> Text(stringResource(I18nR.string.telos_backup_schedules_heading), modifier = Modifier.padding(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AppListTab(apps: List<AppBackupEntry>) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(apps, key = { it.packageName }) { app ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .telosTechSurface(borderColor = TelosCyber.accentFor(app.packageName.hashCode()), shape = RoundedCornerShape(12.dp))
                    .clickable { /* Show details/options */ }
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(painterResource(BaseR.drawable.android_24px), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(app.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(app.versionName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                            if (app.hasApkBackup) Badge(containerColor = MaterialTheme.colorScheme.tertiaryContainer) { Text(stringResource(I18nR.string.telos_backup_apk)) }
                            if (app.hasDataBackup) Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) { Text(stringResource(I18nR.string.telos_backup_data)) }
                            if (!app.hasApkBackup && !app.hasDataBackup) Badge(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest) { Text(stringResource(I18nR.string.telos_backup_none)) }
                        }
                    }
                    var menuOpen by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(painterResource(BaseR.drawable.more_vert_24px), contentDescription = stringResource(I18nR.string.hc_settings))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(I18nR.string.telos_backup_apk_and_data)) },
                                onClick = { menuOpen = false },
                                leadingIcon = { Icon(painterResource(BaseR.drawable.save_24px), null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(I18nR.string.telos_backup_restore_data)) },
                                onClick = { menuOpen = false },
                                leadingIcon = { Icon(painterResource(BaseR.drawable.settings_backup_restore_24px), null) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SystemDataTab(items: List<String>) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(items) { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .telosTechSurface(borderColor = TelosCyber.NeonCyan, shape = RoundedCornerShape(12.dp))
                    .clickable { /* Backup item */ }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(item, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Icon(painterResource(BaseR.drawable.save_24px), contentDescription = "Backup", tint = TelosCyber.NeonCyan)
            }
        }
    }
}
