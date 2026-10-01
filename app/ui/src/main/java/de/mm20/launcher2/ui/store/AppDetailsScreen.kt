package de.mm20.launcher2.ui.store

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.store.model.AppSource
import de.mm20.launcher2.store.model.StoreItem
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import kotlinx.serialization.Serializable
import kotlin.math.ln
import kotlin.math.pow

@Serializable
data class AppDetailsRoute(val itemId: String) : NavKey

@Composable
fun AppDetailsScreen(itemId: String) {
    val viewModel: StoreViewModel = viewModel()

    val item by remember(viewModel, itemId) { viewModel.item(itemId) }.collectAsStateWithLifecycle(null)
    val installStates by viewModel.installStates.collectAsStateWithLifecycle()

    val current = item
    PreferenceScreen(title = current?.displayName ?: "") {
        if (current == null) return@PreferenceScreen

        item {
            AppDetailsHeader(
                item = current,
                installState = installStates[current.id] ?: StoreInstallUiState.Idle,
                onActionClick = { viewModel.onAppActionClicked(current) },
            )
        }

        current.latestRelease?.changelog?.takeIf { it.isNotBlank() }?.let { changelog ->
            item {
                PreferenceCategory(title = "Changelog") {
                    Text(
                        text = changelog,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AppDetailsHeader(
    item: StoreItem,
    installState: StoreInstallUiState,
    onActionClick: () -> Unit,
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = item.displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(item.displayName, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = item.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            AssistChip(
                onClick = {},
                label = { Text(item.source.badgeLabel()) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            )

            item.latestRelease?.version?.let { version ->
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "v$version",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item.latestRelease?.size?.let { size ->
                Spacer(Modifier.width(8.dp))
                Text(
                    text = formatBytes(size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        AppDetailsActionButton(item = item, state = installState, onClick = onActionClick)

        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
    }
}

@Composable
private fun AppDetailsActionButton(
    item: StoreItem,
    state: StoreInstallUiState,
    onClick: () -> Unit,
) {
    when (state) {
        StoreInstallUiState.Downloading, StoreInstallUiState.Installing -> {
            Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(if (state == StoreInstallUiState.Downloading) "Downloading…" else "Installing…")
            }
        }

        StoreInstallUiState.Failed -> {
            OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
                Text("Install failed - tap to retry")
            }
        }

        StoreInstallUiState.Idle, StoreInstallUiState.Installed -> {
            Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
                Text(item.actionLabel())
            }
        }
    }
}

private fun AppSource.badgeLabel(): String = when (this) {
    is AppSource.GitHub -> "GitHub"
    is AppSource.FDroid -> "F-Droid"
    is AppSource.DirectApk -> "Direct APK"
    is AppSource.AffiliatePlayStore -> "Play Store Partner"
    is AppSource.AffiliateDirect -> "Partner Download"
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB")
    val exponent = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(1, units.size)
    val value = bytes / 1024.0.pow(exponent)
    return "%.1f %s".format(value, units[exponent - 1])
}
