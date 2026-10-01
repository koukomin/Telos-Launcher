package de.mm20.launcher2.ui.store

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.store.model.AppSource
import de.mm20.launcher2.store.model.StoreItem
import de.mm20.launcher2.ui.component.LauncherCard
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.locals.LocalBackStack
import kotlinx.serialization.Serializable

@Serializable
data object StoreDashboardRoute : NavKey

@Composable
fun StoreDashboardScreen() {
    val viewModel: StoreViewModel = viewModel()
    val backStack = LocalBackStack.current

    val items by viewModel.items.collectAsStateWithLifecycle()
    val installStates by viewModel.installStates.collectAsStateWithLifecycle()

    PreferenceScreen(title = "Store") {
        if (items.isEmpty()) {
            item { EmptyStoreMessage() }
        }
        items(items.size, key = { items[it].id }) { index ->
            val item = items[index]
            StoreItemRow(
                item = item,
                installState = installStates[item.id] ?: StoreInstallUiState.Idle,
                onClick = { backStack.add(AppDetailsRoute(itemId = item.id)) },
                onActionClick = { viewModel.onAppActionClicked(item) },
            )
        }
    }
}

@Composable
private fun EmptyStoreMessage() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "No apps yet",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Apps you add to the Store will show up here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun StoreItemRow(
    item: StoreItem,
    installState: StoreInstallUiState,
    onClick: () -> Unit,
    onActionClick: () -> Unit,
) {
    LauncherCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppAvatar(displayName = item.displayName)

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.source.summary(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.width(12.dp))

            // The action button has its own clickable surface nested inside the row's - Compose
            // dispatches the tap to the innermost one, so this doesn't trigger onClick (navigate
            // to details) at the same time as onActionClick (install/update/open).
            StoreActionButton(item = item, state = installState, onClick = onActionClick)
        }
    }
}

@Composable
private fun AppAvatar(displayName: String) {
    Surface(
        modifier = Modifier.size(44.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
internal fun StoreActionButton(
    item: StoreItem,
    state: StoreInstallUiState,
    onClick: () -> Unit,
) {
    when (state) {
        StoreInstallUiState.Downloading, StoreInstallUiState.Installing -> {
            OutlinedButton(onClick = {}, enabled = false) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
                Text(if (state == StoreInstallUiState.Downloading) "Downloading" else "Installing")
            }
        }

        StoreInstallUiState.Failed -> {
            OutlinedButton(onClick = onClick) {
                Text("Retry")
            }
        }

        StoreInstallUiState.Idle, StoreInstallUiState.Installed -> {
            val label = item.actionLabel()
            if (label == "Open") {
                OutlinedButton(onClick = onClick) { Text(label) }
            } else {
                Button(onClick = onClick) { Text(label) }
            }
        }
    }
}

/** "Install" (never installed), "Update" (installed but outdated), or "Open" (installed, current). */
internal fun StoreItem.actionLabel(): String = when {
    installedVersionCode == null -> "Install"
    hasUpdate -> "Update"
    else -> "Open"
}

internal fun AppSource.summary(): String = when (this) {
    is AppSource.GitHub -> "$owner/$repo on GitHub"
    is AppSource.FDroid -> "F-Droid"
    is AppSource.DirectApk -> "Direct download"
    is AppSource.AffiliatePlayStore -> "Play Store"
    is AppSource.AffiliateDirect -> "Direct download"
}
