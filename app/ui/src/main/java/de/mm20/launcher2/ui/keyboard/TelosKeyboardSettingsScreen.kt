package de.mm20.launcher2.ui.keyboard

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.base.R as BaseR
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.ui.component.VirtualAppWindow
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.locals.LocalBackStack
import kotlinx.serialization.Serializable

@Serializable
data object TelosKeyboardRoute : NavKey

@Composable
fun TelosKeyboardSettingsScreen() {
    val context = LocalContext.current
    val backStack = LocalBackStack.current

    VirtualAppWindow {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                if (backStack.size > 1) {
                    IconButton(onClick = { backStack.removeLastOrNull() }) {
                        Icon(painterResource(BaseR.drawable.arrow_back_24px), contentDescription = stringResource(I18nR.string.hc_back))
                    }
                }
                Text("Telos Keyboard", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🎹 Ενεργοποίηση Telos Keyboard", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("Ενεργοποιήστε το Telos Keyboard στις ρυθμίσεις πληκτρολογίου του Android και επιλέξτε το ως προεπιλεγμένη μέθοδο εισαγωγής.", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }) {
                            Text("1. Ενεργοποίηση IME")
                        }
                    }
                }
            }

            PreferenceCategory(title = "Ρυθμίσεις Πληκτρολογίου") {
                SwitchPreference(
                    title = "Incognito / Privacy Mode",
                    summary = "Μηδενική καταγραφή λέξεων σε πεδία κωδικών & ιδιωτικής περιήγησης",
                    value = TelosKeyboardState.isIncognitoMode,
                    onValueChanged = { TelosKeyboardState.isIncognitoMode = it }
                )
            }
        }
    }
}
