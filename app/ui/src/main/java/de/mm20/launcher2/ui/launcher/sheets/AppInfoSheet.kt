package de.mm20.launcher2.ui.launcher.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.DismissableBottomSheet
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.ktx.toPixels

@Composable
fun AppInfoSheet(
    app: Application?,
    onDismiss: () -> Unit,
) {
    DismissableBottomSheet(
        state = app,
        expanded = { it != null },
        onDismissRequest = onDismiss
    ) { currentApp ->
        currentApp ?: return@DismissableBottomSheet

        val viewModel: AppInfoSheetVM = remember(currentApp.key) { AppInfoSheetVM(currentApp) }
        val context = LocalContext.current
        val permissions by viewModel.permissions.collectAsStateWithLifecycle()
        val icon by viewModel.icon.collectAsStateWithLifecycle()
        val isShizukuAvailable by viewModel.isShizukuAvailable.collectAsStateWithLifecycle()

        val iconSize = 64.dp
        val iconSizePx = iconSize.toPixels()

        LaunchedEffect(currentApp.key) {
            viewModel.init(context, iconSizePx.toInt())
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            ShapedLauncherIcon(
                size = iconSize,
                icon = { icon }
            )

            Text(
                text = currentApp.label,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 8.dp)
            )

            Text(
                text = "${currentApp.versionName} (${currentApp.componentName.packageName})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ActionButton(
                    icon = R.drawable.close_24px,
                    label = stringResource(R.string.menu_force_stop),
                    enabled = isShizukuAvailable,
                    onClick = { viewModel.forceStop() }
                )
                ActionButton(
                    icon = R.drawable.ac_unit_24px,
                    label = stringResource(R.string.hf_appinfo_freeze),
                    enabled = isShizukuAvailable,
                    onClick = { viewModel.freeze() }
                )
                ActionButton(
                    icon = R.drawable.info_24px,
                    label = stringResource(R.string.hf_appinfo_system_info),
                    onClick = { currentApp.openAppDetails(context) }
                )
            }

            if (!isShizukuAvailable) {
                Text(
                    text = stringResource(R.string.hf_appinfo_shizuku_missing),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp).clickable { viewModel.requestShizukuPermission() }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Permissions
            Text(
                text = stringResource(R.string.hf_appinfo_permissions),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.Start).padding(bottom = 8.dp)
            )

            permissions.forEach { permission ->
                PermissionItem(permission)
            }
        }
    }
}

@Composable
private fun ActionButton(
    icon: Int,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick).padding(8.dp)
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun PermissionItem(permission: AppInfoSheetVM.PermissionInfo) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(if (permission.isGranted) R.drawable.check_24px else R.drawable.close_24px),
            contentDescription = null,
            tint = if (permission.isGranted) Color(0xFF4CAF50) else Color(0xFFF44336),
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(text = permission.name, style = MaterialTheme.typography.bodyMedium)
            Text(text = permission.fullName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
        }
    }
}
