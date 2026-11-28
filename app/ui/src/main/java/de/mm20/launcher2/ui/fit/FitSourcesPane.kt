package de.mm20.launcher2.ui.fit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.ui.component.telosTechCard

@Composable
internal fun FitSourcesPane(
    healthConnectAvailable: Boolean,
    healthConnectSynced: Boolean,
    needsPermission: Boolean,
    cycleTrackerEnabled: Boolean = false,
    onToggleCycleTracker: (Boolean) -> Unit = {},
    onRequestHealthConnect: () -> Unit,
    onOpenHealthConnect: () -> Unit,
    onOpenVendor: (String) -> Unit,
) {
    val context = LocalContext.current
    val neonCyan = Color(0xFF00F0FF)
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth().telosTechCard(borderColor = Color(0xFFFF007F)),
                shape = RoundedCornerShape(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Cycle Tracker 🌸", fontWeight = FontWeight.Bold)
                        Text("Track menstrual cycle, fertility window and predictions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    androidx.compose.material3.Switch(
                        checked = cycleTrackerEnabled,
                        onCheckedChange = onToggleCycleTracker
                    )
                }
            }
        }
        item {
            Text(
                stringResource(I18nR.string.fit_sources_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(FitHealthHub.vendors, key = { it.packageName }) { vendor ->
            val installed = FitHealthHub.isInstalled(context, vendor.packageName)
            val title = stringResource(
                when (vendor.id) {
                    FitHealthSourceId.HealthConnect -> I18nR.string.fit_source_health_connect
                    FitHealthSourceId.GoogleFit -> I18nR.string.fit_source_google_fit
                    FitHealthSourceId.Garmin -> I18nR.string.fit_source_garmin
                    FitHealthSourceId.Samsung -> I18nR.string.fit_source_samsung
                    FitHealthSourceId.Huawei -> I18nR.string.fit_source_huawei
                }
            )
            val summary = when {
                vendor.id == FitHealthSourceId.HealthConnect && healthConnectSynced ->
                    stringResource(I18nR.string.fit_status_synced)
                vendor.id == FitHealthSourceId.HealthConnect && needsPermission ->
                    stringResource(I18nR.string.fit_source_grant)
                vendor.id == FitHealthSourceId.HealthConnect && healthConnectAvailable ->
                    stringResource(I18nR.string.fit_status_partial)
                installed -> stringResource(I18nR.string.fit_source_installed_bridge)
                else -> stringResource(I18nR.string.fit_source_not_installed)
            }
            Surface(
                modifier = Modifier.fillMaxWidth().telosTechCard(borderColor = neonCyan),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, fontWeight = FontWeight.Bold)
                    Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (vendor.id == FitHealthSourceId.HealthConnect) {
                            FilledTonalButton(onClick = onRequestHealthConnect) {
                                Text(stringResource(I18nR.string.fit_source_connect))
                            }
                            if (healthConnectAvailable) {
                                OutlinedButton(onClick = onOpenHealthConnect) {
                                    Text(stringResource(I18nR.string.fit_source_manage))
                                }
                            }
                        } else if (installed) {
                            FilledTonalButton(onClick = { onOpenVendor(vendor.packageName) }) {
                                Text(stringResource(I18nR.string.fit_source_open_app))
                            }
                        }
                    }
                }
            }
        }
    }
}

