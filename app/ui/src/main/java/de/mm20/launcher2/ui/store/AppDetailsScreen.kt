package de.mm20.launcher2.ui.store

import de.mm20.launcher2.ui.R
import androidx.compose.ui.res.stringResource
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.store.model.AppSource
import de.mm20.launcher2.store.options.ItemOptions
import de.mm20.launcher2.store.parser.StoreUrlParser
import de.mm20.launcher2.ui.locals.LocalBackStack
import de.mm20.launcher2.ui.media.MediaFrame
import kotlinx.serialization.Serializable
import java.text.DateFormat
import java.util.Date
import kotlin.math.ln
import kotlin.math.pow

@Serializable
data class AppDetailsRoute(val itemId: String) : NavKey

@Composable
fun AppDetailsScreen(itemId: String) {
    val viewModel: StoreViewModel = viewModel()
    val context = LocalContext.current
    val backStack = LocalBackStack.current

    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val row = rows.firstOrNull { it.item.id == itemId }
    val installStates by viewModel.installStates.collectAsStateWithLifecycle()
    val checking by viewModel.checking.collectAsStateWithLifecycle()

    MediaFrame(row?.item?.displayName ?: "") {
        if (row == null) return@MediaFrame
        val item = row.item
        val o = row.options
        var confirmRemove by remember { mutableStateOf(false) }
        var renaming by remember { mutableStateOf(false) }
        var filterText by remember(item.source) { mutableStateOf(sourceRegex(item.source).orEmpty()) }
        var prerelease by remember(item.source) { mutableStateOf(sourcePrerelease(item.source)) }
        var category by remember(o.category) { mutableStateOf(o.category) }
        var note by remember(o.note) { mutableStateOf(o.note) }

        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(item.packageName, item.displayName, 64)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.displayName, style = MaterialTheme.typography.titleLarge)
                        Text(
                            if (item.packageName == "unknown.package") stringResource(R.string.hf_store_package_unknown) else item.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(item.source.summary(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    DetailsActionButton(row, installStates[item.id] ?: StoreInstallUiState.Idle) { viewModel.onAppActionClicked(item) }
                    OutlinedButton(onClick = { viewModel.checkOne(item) }, enabled = !checking) {
                        if (checking) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.hc_check_now))
                    }
                }
            }
            item {
                Card(stringResource(R.string.hf_store_versions)) {
                    InfoLine(stringResource(R.string.hf_store_installed), item.installedVersionName?.let { "v$it" } ?: if (item.installedVersionCode != null) stringResource(R.string.hf_store_version_code, item.installedVersionCode.toString()) else stringResource(R.string.hf_store_not_installed))
                    InfoLine(stringResource(R.string.hf_store_latest), item.latestRelease?.version?.let { "v$it" } ?: stringResource(R.string.hf_store_not_checked))
                    item.latestRelease?.size?.let { InfoLine(stringResource(R.string.hf_store_size), formatBytes(it)) }
                    item.latestRelease?.publishedAt?.let { InfoLine(stringResource(R.string.hf_store_published), DateFormat.getDateInstance().format(Date(it))) }
                    item.lastCheckedAt?.let { InfoLine(stringResource(R.string.hf_store_last_checked), DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))) }
                    if (row.updateAvailable) {
                        TextButton(onClick = { viewModel.setOptions(item.id) { it.copy(skippedVersion = item.latestRelease?.version) } }, contentPadding = PaddingValues(0.dp)) {
                            Text(stringResource(R.string.hc_skip_this_version))
                        }
                    }
                    if (o.skippedVersion != null) {
                        TextButton(onClick = { viewModel.setOptions(item.id) { it.copy(skippedVersion = null) } }, contentPadding = PaddingValues(0.dp)) {
                            Text(stringResource(R.string.hc_stop_skipping_version, o.skippedVersion.orEmpty()))
                        }
                    }
                }
            }
            item.latestRelease?.changelog?.takeIf { it.isNotBlank() }?.let { changelog ->
                item { Card(stringResource(R.string.hf_store_changelog)) { Text(changelog.take(4000), style = MaterialTheme.typography.bodyMedium) } }
            }
            item {
                Card(stringResource(R.string.hf_store_app_settings)) {
                    SwitchLine(stringResource(R.string.hf_store_track_only), stringResource(R.string.hf_store_track_only_desc), o.trackOnly) { v ->
                        viewModel.setOptions(item.id) { it.copy(trackOnly = v) }
                    }
                    SwitchLine(stringResource(R.string.hf_store_pin), stringResource(R.string.hf_store_pin_desc), o.pinned) { v ->
                        viewModel.setOptions(item.id) { it.copy(pinned = v) }
                    }
                    SwitchLine(stringResource(R.string.hf_store_exclude_bg), stringResource(R.string.hf_store_exclude_bg_desc), o.excludeFromBackground) { v ->
                        viewModel.setOptions(item.id) { it.copy(excludeFromBackground = v) }
                    }
                    OutlinedTextField(
                        category, { category = it; viewModel.setOptions(item.id) { o2 -> o2.copy(category = it.trim()) } },
                        label = { Text(stringResource(R.string.hc_category)) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    OutlinedTextField(
                        note, { note = it; viewModel.setOptions(item.id) { o2 -> o2.copy(note = it) } },
                        label = { Text(stringResource(R.string.hc_note)) }, modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = { renaming = true }, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.hc_rename)) }
                }
            }
            if (hasFilter(item.source)) {
                item {
                    Card(stringResource(R.string.hf_store_which_file)) {
                        OutlinedTextField(
                            filterText, { filterText = it }, label = { Text(stringResource(R.string.hc_file_name_filter_regular_expression)) }, singleLine = true,
                            supportingText = { Text(stringResource(R.string.hc_for_example_arm64_or_universal_empty_mea)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (hasPrerelease(item.source)) SwitchLine(stringResource(R.string.hf_store_prereleases), null, prerelease) { prerelease = it }
                        Button(onClick = { viewModel.setSourceFilter(item, filterText, prerelease) }, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.hc_apply_and_check)) }
                    }
                }
            }
            item {
                Card(stringResource(R.string.hf_store_source)) {
                    Text(StoreUrlParser.toUrl(item.source), style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        OutlinedButton(onClick = { viewModel.openUrl(StoreUrlParser.toUrl(item.source)) }) { Text(stringResource(R.string.hc_open)) }
                        OutlinedButton(onClick = {
                            val link = "obtainium://add/" + StoreUrlParser.toUrl(item.source)
                            context.startActivity(
                                Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, link), null)
                            )
                        }) { Text(stringResource(R.string.hc_share_link)) }
                    }
                }
            }
            item {
                HorizontalDivider()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    TextButton(onClick = { confirmRemove = true }) { Text(stringResource(R.string.hc_remove_from_list)) }
                    if (item.installedVersionCode != null) TextButton(onClick = { viewModel.uninstall(item) }) { Text(stringResource(R.string.hc_uninstall_app)) }
                }
            }
        }

        if (confirmRemove) {
            AlertDialog(
                onDismissRequest = { confirmRemove = false },
                title = { Text(stringResource(R.string.hc_remove_item_question, item.displayName)) },
                text = { Text(stringResource(R.string.hc_it_is_only_removed_from_this_list_the_in)) },
                confirmButton = {
                    TextButton(onClick = { viewModel.remove(item); confirmRemove = false; backStack.removeLastOrNull() }) { Text(stringResource(R.string.hc_remove)) }
                },
                dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.hc_cancel)) } },
            )
        }
        if (renaming) {
            var name by remember { mutableStateOf(item.displayName) }
            AlertDialog(
                onDismissRequest = { renaming = false },
                title = { Text(stringResource(R.string.hc_rename)) },
                text = { OutlinedTextField(name, { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
                confirmButton = { TextButton(onClick = { viewModel.rename(item, name); renaming = false }) { Text(stringResource(R.string.hc_save)) } },
                dismissButton = { TextButton(onClick = { renaming = false }) { Text(stringResource(R.string.hc_cancel)) } },
            )
        }
    }
}

private fun sourceRegex(s: AppSource): String? = when (s) {
    is AppSource.GitHub -> s.assetNameRegex
    is AppSource.GitLab -> s.assetNameRegex
    is AppSource.Gitea -> s.assetNameRegex
    is AppSource.SourceForge -> s.assetNameRegex
    is AppSource.Html -> s.linkRegex
    else -> null
}

private fun sourcePrerelease(s: AppSource): Boolean = when (s) {
    is AppSource.GitHub -> s.includePrereleases
    is AppSource.GitLab -> s.includePrereleases
    is AppSource.Gitea -> s.includePrereleases
    else -> false
}

private fun hasFilter(s: AppSource) = s is AppSource.GitHub || s is AppSource.GitLab || s is AppSource.Gitea || s is AppSource.SourceForge || s is AppSource.Html
private fun hasPrerelease(s: AppSource) = s is AppSource.GitHub || s is AppSource.GitLab || s is AppSource.Gitea

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
            content()
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value)
    }
}

@Composable
private fun SwitchLine(label: String, description: String?, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label)
            description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable
private fun DetailsActionButton(row: StoreRow, state: StoreInstallUiState, onClick: () -> Unit) {
    when (state) {
        StoreInstallUiState.Downloading, StoreInstallUiState.Installing -> {
            Button(onClick = {}, enabled = false) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(if (state == StoreInstallUiState.Downloading) stringResource(R.string.hf_store_downloading_dots) else stringResource(R.string.hf_store_installing_dots))
            }
        }
        StoreInstallUiState.Failed -> OutlinedButton(onClick = onClick) { Text(stringResource(R.string.hc_failed_try_again)) }
        StoreInstallUiState.Idle, StoreInstallUiState.Installed -> Button(onClick = onClick) { Text(row.actionLabel()) }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB")
    val exponent = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(1, units.size)
    val value = bytes / 1024.0.pow(exponent)
    return "%.1f %s".format(value, units[exponent - 1])
}
