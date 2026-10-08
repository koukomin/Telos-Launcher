package de.mm20.launcher2.ui.launcher.search.notes

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.applock.SettingsDeepLinkContract
import de.mm20.launcher2.ui.launcher.search.common.list.ListItemSurface
import de.mm20.launcher2.ui.notes.Note

/** Notes of Telos Notes that match the search. A tap opens the notes app. */
fun LazyListScope.NoteResults(notes: List<Note>, reverse: Boolean) {
    notes.forEachIndexed { index, note ->
        item(key = "note-${note.id}") {
            val context = LocalContext.current
            ListItemSurface(isFirst = index == 0, isLast = index == notes.lastIndex, reverse = reverse) {
                Column(
                    Modifier.fillMaxWidth().clickable {
                        context.startActivity(Intent().apply {
                            setClassName(context.packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
                            putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_NOTES)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    }.padding(16.dp),
                ) {
                    Text(note.title.ifBlank { note.body.lineSequence().firstOrNull().orEmpty() }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (note.title.isNotBlank() && note.body.isNotBlank()) Text(note.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
