package de.mm20.launcher2.ui.launcher.search.appmanagement

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.LauncherCard
import de.mm20.launcher2.ui.launcher.search.common.list.ListItem
import de.mm20.launcher2.ui.theme.transparency.transparency

fun LazyListScope.FossUpdateResults(
    updates: List<Application>,
    onDismiss: (Application) -> Unit,
    reverse: Boolean,
) {
    // Lazy list keys must be unique
    @Suppress("NAME_SHADOWING")
    val updates = updates.distinctBy { it.key }
    if (updates.isEmpty()) return

    item(key = "foss_updates_header") {
        Text(
            text = stringResource(R.string.hf_available_updates),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }

    items(updates.size, key = { "foss_update_${updates[it].key}" }) { index ->
        val app = updates[index]
        val context = LocalContext.current
        LauncherCard(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.surface.copy(MaterialTheme.transparency.surface)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ListItem(
                    modifier = Modifier.weight(1f),
                    item = app,
                    showDetails = false,
                    onShowDetails = {},
                )
                IconButton(onClick = { onDismiss(app) }) {
                    Icon(painterResource(R.drawable.close_24px), contentDescription = stringResource(R.string.hc_dismiss))
                }
            }
        }
    }
}
