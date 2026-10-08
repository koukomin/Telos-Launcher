package de.mm20.launcher2.ui.comms

import de.mm20.launcher2.ui.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.preferences.comms.CommsSettings
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.locals.LocalBackStack
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

@Serializable
data object CallerNotesRoute : NavKey

@Composable
fun CallerNotesScreen() {
    val commsSettings: CommsSettings = koinInject()
    val notes by commsSettings.callerNotes.collectAsStateWithLifecycle(emptyMap())
    val backStack = LocalBackStack.current
    PreferenceScreen(title = { Text(stringResource(R.string.hc_notes)) }) {
        if (notes.isEmpty()) {
            item { Text(stringResource(R.string.hc_no_contact_notes_yet), modifier = Modifier.padding(16.dp)) }
        }
        notes.forEach { (number, note) ->
            item {
                ListItem(
                    headlineContent = { Text(number) },
                    supportingContent = { Text(note) },
                    modifier = Modifier.clickable {
                        backStack.add(ContactDetailsRoute(phoneNumber = number))
                    },
                )
            }
        }
    }
}
