package de.mm20.launcher2.ui.sync

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.VirtualAppWindow
import kotlinx.serialization.Serializable

@Serializable
data object SyncRoute : NavKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen() {
    val context = LocalContext.current
    val viewModel: SyncViewModel = viewModel()

    val serviceRunning by viewModel.serviceRunning.collectAsStateWithLifecycle()
    val wifiOnly by viewModel.wifiOnly.collectAsStateWithLifecycle()
    val chargingOnly by viewModel.chargingOnly.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val devices by viewModel.devices.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddFolderDialog by remember { mutableStateOf(false) }
    var showAddDeviceDialog by remember { mutableStateOf(false) }
    var showFontDialog by remember { mutableStateOf(false) }

    VirtualAppWindow {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(de.mm20.launcher2.base.R.drawable.ic_app_sync_fg),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(12.dp))
                            Text("Sync", fontWeight = FontWeight.Bold)
                        }
                    },
                    actions = {
                        IconButton(onClick = { showFontDialog = true }) {
                            Icon(painterResource(R.drawable.text_fields_24px), contentDescription = "Font Size")
                        }
                        TextButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://127.0.0.1:8384"))
                                runCatching { context.startActivity(intent) }
                            }
                        ) {
                            Icon(painterResource(R.drawable.open_in_new_24px), contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Web UI")
                        }
                    }
                )
            },
            floatingActionButton = {
                if (selectedTab == 0) {
                    FloatingActionButton(
                        onClick = { showAddFolderDialog = true },
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(painterResource(R.drawable.add_24px), contentDescription = "Add Folder")
                    }
                } else if (selectedTab == 1) {
                    FloatingActionButton(
                        onClick = { showAddDeviceDialog = true },
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(painterResource(R.drawable.add_24px), contentDescription = "Add Device")
                    }
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Syncthing Service Power Banner
                Surface(
                    color = if (serviceRunning) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = if (serviceRunning) "Syncthing Service Active" else "Syncthing Service Stopped",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (serviceRunning) "Synchronizing files in real-time" else "Tap to start background sync",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(
                            checked = serviceRunning,
                            onCheckedChange = { viewModel.toggleService() }
                        )
                    }
                }

                // Tabs
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Shared Folders (${folders.size})") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Devices (${devices.size})") }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Settings") }
                    )
                }

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (selectedTab) {
                        0 -> FoldersTab(folders, onTogglePause = { viewModel.toggleFolderPause(it) })
                        1 -> DevicesTab(devices, onTogglePause = { viewModel.toggleDevicePause(it) })
                        2 -> SettingsTab(
                            viewModel = viewModel,
                            wifiOnly = wifiOnly,
                            chargingOnly = chargingOnly,
                            onWifiChange = { viewModel.setWifiOnly(it) },
                            onChargingChange = { viewModel.setChargingOnly(it) }
                        )
                    }
                }
            }
        }
    }

    if (showAddFolderDialog) {
        var label by remember { mutableStateOf("") }
        var path by remember { mutableStateOf("") }
        var type by remember { mutableStateOf("Send & Receive") }

        AlertDialog(
            onDismissRequest = { showAddFolderDialog = false },
            title = { Text("Add Shared Folder") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("Folder Label") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = path,
                        onValueChange = { path = it },
                        label = { Text("Local Folder Path") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.addFolder(label, path, type)
                        showAddFolderDialog = false
                    }
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddFolderDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showAddDeviceDialog) {
        var deviceId by remember { mutableStateOf("") }
        var deviceName by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddDeviceDialog = false },
            title = { Text("Add Device / Peer") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = deviceId,
                        onValueChange = { deviceId = it },
                        label = { Text("Syncthing Device ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = deviceName,
                        onValueChange = { deviceName = it },
                        label = { Text("Device Name / Alias") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.addDevice(deviceId, deviceName)
                        showAddDeviceDialog = false
                    }
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDeviceDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showFontDialog) {
        de.mm20.launcher2.ui.component.PerAppFontScaleDialog(
            appKey = "sync",
            appName = "Telos Sync",
            onDismiss = { showFontDialog = false }
        )
    }
}

@Composable
private fun FoldersTab(folders: List<SyncthingFolder>, onTogglePause: (String) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(folders, key = { it.id }) { folder ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.folder_24px),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(folder.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(folder.path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Badge { Text(folder.type) }
                            Badge(containerColor = MaterialTheme.colorScheme.tertiaryContainer) { Text(folder.sizeFormatted) }
                        }
                    }
                    IconButton(onClick = { onTogglePause(folder.id) }) {
                        Icon(
                            painter = painterResource(if (folder.isPaused) R.drawable.play_arrow_24px else R.drawable.pause_24px),
                            contentDescription = "Toggle Pause"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DevicesTab(devices: List<SyncthingDevice>, onTogglePause: (String) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(devices, key = { it.id }) { device ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.hard_disk_24px),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(device.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(device.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Badge(containerColor = if (device.isConnected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer) {
                                Text(if (device.isConnected) "Connected" else "Disconnected")
                            }
                            Text("▲ ${device.downloadSpeed} · ▼ ${device.uploadSpeed}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    IconButton(onClick = { onTogglePause(device.id) }) {
                        Icon(
                            painter = painterResource(if (device.isPaused) R.drawable.play_arrow_24px else R.drawable.pause_24px),
                            contentDescription = "Toggle Pause"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsTab(
    viewModel: SyncViewModel,
    wifiOnly: Boolean,
    chargingOnly: Boolean,
    onWifiChange: (Boolean) -> Unit,
    onChargingChange: (Boolean) -> Unit
) {
    val sleepSyncEnabled by viewModel.sleepSyncEnabled.collectAsStateWithLifecycle()
    val sleepSyncMode by viewModel.sleepSyncMode.collectAsStateWithLifecycle()
    val dndDuringSync by viewModel.dndDuringSync.collectAsStateWithLifecycle()
    val syncOnScreenOff by viewModel.syncOnScreenOff.collectAsStateWithLifecycle()
    
    var showModeDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Sync on Wi-Fi Only", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Prevent sync over cellular data", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = wifiOnly, onCheckedChange = onWifiChange)
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Sync when Charging Only", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Pause background sync while running on battery", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = chargingOnly, onCheckedChange = onChargingChange)
                }
            }
        }
        
        Text("SleepSync (Optimized Night Sync)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp, start = 8.dp))
        
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Enable SleepSync", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Automatically sync large files at night while device is inactive", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = sleepSyncEnabled, onCheckedChange = { viewModel.setSleepSyncEnabled(it) })
                }
                
                if (sleepSyncEnabled) {
                    HorizontalDivider()
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showModeDialog = true },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                            Text("Network Mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(sleepSyncMode, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    
                    HorizontalDivider()
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("DND During SleepSync", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Suppress sync notifications while sleeping", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = dndDuringSync, onCheckedChange = { viewModel.setDndDuringSync(it) })
                    }
                    
                    HorizontalDivider()
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Sync on Screen Off", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Start sync tasks immediately when screen turns off", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = syncOnScreenOff, onCheckedChange = { viewModel.setSyncOnScreenOff(it) })
                    }
                }
            }
        }
    }
    
    if (showModeDialog) {
        AlertDialog(
            onDismissRequest = { showModeDialog = false },
            title = { Text("Network Mode") },
            text = {
                Column {
                    val modes = listOf("Only on Wi-Fi", "Wi-Fi and Cellular", "Any Unmetered")
                    modes.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { 
                                    viewModel.setSleepSyncMode(mode)
                                    showModeDialog = false 
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = sleepSyncMode == mode,
                                onClick = { 
                                    viewModel.setSleepSyncMode(mode)
                                    showModeDialog = false 
                                }
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(mode)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showModeDialog = false }) { Text("Close") }
            }
        )
    }
}
