package de.mm20.launcher2.ui.comms

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
data object HiddenContactsRoute : NavKey

@Composable
fun HiddenContactsScreen() {
    val commsSettings: CommsSettings = koinInject()
    val hidden by commsSettings.hiddenNumbers.collectAsStateWithLifecycle(emptyMap())
    val backStack = LocalBackStack.current
    PreferenceScreen(title = { Text("Hidden contacts") }) {
        if (hidden.isEmpty()) {
            item {
                Text(
                    "Dial #PIN# on the keypad to unlock. Hide a contact from their details page.",
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        hidden.keys.forEach { number ->
            item {
                ListItem(
                    headlineContent = { Text(number) },
                    trailingContent = {
                        TextButton(onClick = { commsSettings.setHiddenNumber(number, false) }) {
                            Text("Unhide")
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
            }) { Text("Lock hidden contacts") }
        }
    }
}
