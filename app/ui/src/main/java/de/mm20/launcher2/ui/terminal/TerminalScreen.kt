package de.mm20.launcher2.ui.terminal

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import kotlinx.serialization.Serializable

@Serializable
data object TerminalRoute : NavKey

/** Preserves official Termux's UID, filesystem prefix and signed add-on ecosystem. */
@Composable
fun TerminalScreen() {
    val context = LocalContext.current
    fun open(intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            Toast.makeText(context, R.string.terminal_open_failed, Toast.LENGTH_LONG).show()
        }
    }
    fun website(url: String) = open(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    PreferenceScreen(title = { Text(stringResource(R.string.terminal_title)) }) {
        item(key = "runtime") {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(stringResource(R.string.terminal_runtime_summary))
                Button(onClick = {
                    val launch = context.packageManager.getLaunchIntentForPackage("com.termux")
                    if (launch != null) open(launch)
                    else website("https://f-droid.org/packages/com.termux/")
                }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    Text(stringResource(R.string.hc_open) + " · Termux")
                }
            }
        }
        item(key = "install") {
            Preference(title = { Text(stringResource(R.string.hc_install) + " · Termux") },
                summary = { Text("F-Droid") },
                onClick = { website("https://f-droid.org/packages/com.termux/") })
        }
        item(key = "packages") {
            Preference(title = { Text("pkg · apt · Python · Git · SSH") },
                onClick = { website("https://github.com/termux/termux-packages/wiki/Package-Management") })
        }
        item(key = "plugins") {
            Preference(title = { Text("Termux:API · Boot · Widget · Tasker · Float · Styling") },
                onClick = { website("https://github.com/termux/termux-app#termux-app-and-plugins") })
        }
        item(key = "source") {
            Preference(title = { Text("Termux · GPL-3.0-only") },
                summary = { Text("github.com/termux/termux-app") },
                onClick = { website("https://github.com/termux/termux-app") })
        }
    }
}
