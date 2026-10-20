package de.mm20.launcher2.ui.media.stremio

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.comms.stremio.StremioPlaybackSettings
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.ListPreferenceItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.withContext
import java.util.Locale
import de.mm20.launcher2.ui.downloads.torrentSettingsItems

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StremioPlaybackSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf<StremioPlaybackSettings?>(null) }
    LaunchedEffect(Unit) {
        settings = withContext(Dispatchers.IO) { StremioPlaybackSettings.load(context) }
        snapshotFlow { settings }.filterNotNull().collect { value ->
            withContext(Dispatchers.IO) { StremioPlaybackSettings.save(context, value) }
        }
    }
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val languages = remember(locale) {
        Locale.getISOLanguages().map { it to Locale.forLanguageTag(it).getDisplayLanguage(locale) }.sortedBy { it.second }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { Text(stringResource(R.string.settings), Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge) }
            settings?.let { s ->
                item {
                    val choices = listOf(ListPreferenceItem(stringResource(R.string.dl_t_select_none), "")) +
                        languages.map { ListPreferenceItem(it.second, it.first) }
                    ListPreference(title = stringResource(R.string.dl_category_audio) + " · " + stringResource(R.string.au3_appearance_cs_primary), value = s.audio,
                        items = choices, onValueChanged = { settings = s.copy(audio = it) })
                    ListPreference(title = stringResource(R.string.dl_category_audio) + " · " + stringResource(R.string.au3_appearance_cs_secondary), value = s.audioFallback,
                        items = choices, onValueChanged = { settings = s.copy(audioFallback = it) })
                    ListPreference(title = stringResource(R.string.hc_subtitles) + " · " + stringResource(R.string.au3_appearance_cs_primary), value = s.subtitles,
                        items = choices, onValueChanged = { settings = s.copy(subtitles = it) })
                    ListPreference(title = stringResource(R.string.hc_subtitles) + " · " + stringResource(R.string.au3_appearance_cs_secondary), value = s.subtitleFallback,
                        items = choices, onValueChanged = { settings = s.copy(subtitleFallback = it) })
                    ListPreference(title = stringResource(R.string.stremio_http_cache), value = s.cacheGb,
                        items = StremioPlaybackSettings.cacheChoices.map { ListPreferenceItem("$it GB", it) },
                        onValueChanged = { settings = s.copy(cacheGb = it) })
                }
            }
            torrentSettingsItems()
        }
    }
}
