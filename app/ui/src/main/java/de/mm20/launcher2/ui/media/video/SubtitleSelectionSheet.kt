package de.mm20.launcher2.ui.media.video
import de.mm20.launcher2.i18n.R as I18nR

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import de.mm20.launcher2.ui.R
import java.util.Locale

private data class SubtitleChoice(val key: String, val group: Tracks.Group, val index: Int,
    val language: String, val label: String, val selected: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SubtitleSelectionSheet(player: Player, onDismiss: () -> Unit, onFile: () -> Unit, onOnline: () -> Unit) {
    var revision by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) { revision++ }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val choices = remember(player, revision, locale, query) {
        player.currentTracks.groups.flatMapIndexed { groupIndex, group ->
            if (group.type != C.TRACK_TYPE_TEXT) emptyList() else (0 until group.length).map { index ->
                val format = group.getTrackFormat(index)
                val language = format.language?.let { Locale.forLanguageTag(it).getDisplayLanguage(locale) }.orEmpty()
                SubtitleChoice("$groupIndex:$index", group, index, language,
                    format.label.orEmpty().ifBlank { format.language.orEmpty().ifBlank { "#${index + 1}" } }, group.isTrackSelected(index))
            }
        }.filter { query.isBlank() || (it.language + " " + it.label).contains(query, ignoreCase = true) }
            .sortedWith(compareBy({ it.language }, { it.label }))
    }
    val disabled = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f).navigationBarsPadding().padding(horizontal = 16.dp)) {
            Text(stringResource(I18nR.string.hc_subtitles), style = MaterialTheme.typography.titleLarge)
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                item {
                    OutlinedTextField(query, { query = it }, label = { Text(stringResource(I18nR.string.hc_search)) },
                        singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
                }
                item {
                    Row(Modifier.fillMaxWidth().clickable {
                        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                        onDismiss()
                    }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = disabled, onClick = null)
                        Text(stringResource(I18nR.string.dl_t_select_none))
                    }
                }
                items(choices, key = { it.key }) { choice ->
                    Row(Modifier.fillMaxWidth().clickable {
                        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            .setOverrideForType(TrackSelectionOverride(choice.group.mediaTrackGroup, choice.index)).build()
                        onDismiss()
                    }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = choice.selected && !disabled, onClick = null)
                        Column(Modifier.weight(1f)) {
                            if (choice.language.isNotBlank()) Text(choice.language, style = MaterialTheme.typography.titleSmall)
                            Text(choice.label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            TextButton(onClick = onOnline, modifier = Modifier.fillMaxWidth()) { Text(stringResource(I18nR.string.hc_search_online)) }
            TextButton(onClick = onFile, modifier = Modifier.fillMaxWidth()) { Text(stringResource(I18nR.string.hc_from_a_file)) }
            Spacer(Modifier.height(16.dp))
        }
    }
}
