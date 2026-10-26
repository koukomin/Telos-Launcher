package de.mm20.launcher2.ui.comms

import de.mm20.launcher2.ui.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.catch
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
data object HiddenContactsRoute : NavKey

@Composable
fun HiddenContactsScreen() {
    val commsSettings: CommsSettings = koinInject()
    val hidden by commsSettings.hiddenNumbers.collectAsStateWithLifecycle(emptyMap())
    val directory: de.mm20.launcher2.comms.repository.ContactDirectoryRepository = koinInject()
    val contacts by remember(directory) { directory.observeContacts().catch { emit(emptyList()) } }
        .collectAsStateWithLifecycle(emptyList())
    val groups = remember(hidden, contacts) {
        hidden.keys.groupBy { number ->
            contacts.firstOrNull { contact -> contact.phoneNumbers.any {
                de.mm20.launcher2.ktx.PhoneNumberKey.matches(it, number)
            } }?.id?.toString() ?: "number:$number"
        }.map { (id, aliases) ->
            val contact = contacts.firstOrNull { it.id.toString() == id }
            Triple(id, contact?.displayName?.takeIf(String::isNotBlank) ?: aliases.first(),
                (contact?.phoneNumbers.orEmpty() + aliases).distinct())
        }
    }
    val backStack = LocalBackStack.current
    PreferenceScreen(title = { Text(stringResource(R.string.hc_hidden_contacts)) }) {
        if (hidden.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.hc_dial_pin_on_the_keypad_to_unlock_hide_a),
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        groups.forEach { (key, name, numbers) ->
            item(key = key) {
                ListItem(
                    headlineContent = { Text(name) },
                    supportingContent = { Text(numbers.joinToString(" · ")) },
                    trailingContent = {
                        TextButton(onClick = { commsSettings.setHiddenNumbers(numbers, false) }) {
                            Text(stringResource(R.string.hc_unhide))
                        }
                    },
                    modifier = Modifier.padding(0.dp),
                )
            }
        }
        item {
            TextButton(onClick = {
                de.mm20.launcher2.comms.privacy.PrivacySession.lockHider()
                backStack.removeLastOrNull()
            }) { Text(stringResource(R.string.hc_lock_hidden_contacts)) }
        }
    }
}
