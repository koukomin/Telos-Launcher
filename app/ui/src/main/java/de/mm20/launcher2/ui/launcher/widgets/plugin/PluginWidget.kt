package de.mm20.launcher2.ui.launcher.widgets.plugin

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.plugin.contracts.WidgetPluginContract
import de.mm20.launcher2.plugin.data.withColumns
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.widgets.PluginWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.time.Duration.Companion.minutes

@Composable
fun PluginWidget(widget: PluginWidget) {
    val context = LocalContext.current
    var items by remember(widget.config.authority) { mutableStateOf<List<PluginWidgetItem>?>(null) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(widget.config.authority, lifecycle) {
        // Refresh on every return to the launcher and every 5 minutes while it is visible
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                items = queryPluginWidgetItems(context, widget.config.authority)
                delay(5.minutes)
            }
        }
    }

    val currentItems = items
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        if (currentItems == null || currentItems.isEmpty()) {
            Text(
                text = stringResource(R.string.widget_plugin_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            for (item in currentItems) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (item.subtitle != null) {
                            Text(
                                text = item.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (item.value != null) {
                        Text(
                            text = item.value,
                            modifier = Modifier.padding(start = 12.dp),
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

private data class PluginWidgetItem(
    val title: String,
    val subtitle: String?,
    val value: String?,
)

private suspend fun queryPluginWidgetItems(
    context: Context,
    authority: String,
): List<PluginWidgetItem> = withContext(Dispatchers.IO) {
    if (authority.isBlank()) return@withContext emptyList()
    try {
        val uri = Uri.Builder()
            .scheme("content")
            .authority(authority)
            .path(WidgetPluginContract.Paths.Items)
            .appendQueryParameter(WidgetPluginContract.Params.Language, Locale.getDefault().language)
            .build()
        val cursor = context.contentResolver.query(uri, null, null, null) ?: return@withContext emptyList()
        cursor.use { c ->
            val results = mutableListOf<PluginWidgetItem>()
            c.withColumns(WidgetPluginContract.ItemColumns) {
                while (c.moveToNext()) {
                    val title = c[WidgetPluginContract.ItemColumns.Title] ?: continue
                    results += PluginWidgetItem(
                        title = title,
                        subtitle = c[WidgetPluginContract.ItemColumns.Subtitle],
                        value = c[WidgetPluginContract.ItemColumns.Value],
                    )
                }
            }
            results
        }
    } catch (e: Exception) {
        emptyList()
    }
}
