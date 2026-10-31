package de.mm20.launcher2.ui.media.stremio
import de.mm20.launcher2.i18n.R as I18nR

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.comms.stremio.StremioPlaybackSettings
import de.mm20.launcher2.comms.stremio.StremioStreamingServer
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import kotlinx.coroutines.launch

@Composable
internal fun StremioSharedCacheControls(settings: StremioPlaybackSettings, onChanged: (StremioPlaybackSettings) -> Unit) {
    val scope = rememberCoroutineScope()
    val latestSettings by rememberUpdatedState(settings)
    var address by remember(settings.streamingServer) { mutableStateOf(settings.streamingServer) }
    var root by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<StremioStreamingServer.Settings?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val normalized = remember(address) { StremioStreamingServer.normalizeAddress(address) }
    SwitchPreference(title = stringResource(I18nR.string.stremio_shared_cache), value = settings.sharedServer,
        onValueChanged = { onChanged(settings.copy(sharedServer = it)) })
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(I18nR.string.stremio_shared_hint), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(address, { address = it; status = null; failed = false }, label = { Text(stringResource(I18nR.string.stremio_server_address)) },
            modifier = Modifier.fillMaxWidth(), enabled = !busy, isError = normalized == null, singleLine = true)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (failed) Text(stringResource(I18nR.string.stremio_shared_unavailable), color = MaterialTheme.colorScheme.error)
        OutlinedButton(onClick = {
            val target = normalized ?: return@OutlinedButton
            busy = true; failed = false
            scope.launch {
                try {
                    val reply = StremioStreamingServer.settings(target)
                    status = reply; root = reply.cacheRoot
                    onChanged(latestSettings.copy(streamingServer = target))
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { failed = true; status = null }
                finally { busy = false }
            }
        }, enabled = !busy && normalized != null) { Text(stringResource(I18nR.string.stremio_server_connect)) }
        status?.let { info ->
            Text("Stremio ${info.version}", style = MaterialTheme.typography.labelLarge)
            OutlinedTextField(root, { root = it }, label = { Text(stringResource(I18nR.string.stremio_server_cache_folder)) }, modifier = Modifier.fillMaxWidth(), enabled = !busy)
            Text(stringResource(I18nR.string.stremio_server_cache_hint), style = MaterialTheme.typography.bodySmall)
            Button(onClick = {
                val target = normalized ?: return@Button
                busy = true; failed = false
                scope.launch {
                    try { status = StremioStreamingServer.configure(target, settings.cacheGb, root.trim().takeIf { it.isNotEmpty() }) }
                    catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (_: Exception) { failed = true }
                    finally { busy = false }
                }
            }, enabled = !busy && normalized != null) { Text(stringResource(I18nR.string.stremio_server_apply_cache)) }
        }
    }
}
