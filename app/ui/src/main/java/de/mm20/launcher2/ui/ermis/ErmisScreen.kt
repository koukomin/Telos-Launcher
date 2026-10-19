package de.mm20.launcher2.ui.ermis

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.webapp.WebAppLaunchContract
import kotlinx.serialization.Serializable

@Serializable
data object ErmisRoute : NavKey

/** Real webmail access. Native IMAP/SMTP accounts are not implemented by this hub. */
@Composable
fun ErmisScreen() {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("ermis_webmail", 0) }
    var customUrl by remember { mutableStateOf(preferences.getString("url", "").orEmpty()) }
    var editing by remember { mutableStateOf(false) }
    var enteredUrl by remember { mutableStateOf(customUrl) }
    fun openWebmail(label: String, url: String) {
        runCatching {
            context.startActivity(Intent().setClassName(context.packageName, WebAppLaunchContract.ACTIVITY_CLASS_NAME)
                .putExtra(WebAppLaunchContract.EXTRA_URL, url)
                .putExtra(WebAppLaunchContract.EXTRA_LABEL, label)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { Toast.makeText(context, R.string.webapp_error_loading, Toast.LENGTH_LONG).show() }
    }
    PreferenceScreen(title = { Text("Ermis · Webmail") }) {
        for ((label, url) in listOf("Gmail" to "https://mail.google.com/", "Outlook" to "https://outlook.live.com/mail/", "Yahoo Mail" to "https://mail.yahoo.com/", "Proton Mail" to "https://mail.proton.me/")) {
            item(key = label) {
                Preference(title = { Text(label) }, summary = { Text(Uri.parse(url).host.orEmpty()) },
                    icon = { Icon(painterResource(R.drawable.mail_24px), contentDescription = null) },
                    onClick = { openWebmail(label, url) })
            }
        }
        if (customUrl.isNotBlank()) item(key = "custom") {
            Preference(title = { Text(Uri.parse(customUrl).host.orEmpty()) }, onClick = { openWebmail("Ermis", customUrl) })
        }
        item(key = "add") {
            Preference(title = { Text(stringResource(R.string.hc_add)) }, summary = { Text("HTTPS · Webmail") },
                onClick = { enteredUrl = customUrl; editing = true })
        }
    }
    if (editing) {
        val uri = Uri.parse(enteredUrl.trim())
        val valid = uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null
        AlertDialog(onDismissRequest = { editing = false }, title = { Text("Webmail") },
            text = { OutlinedTextField(value = enteredUrl, onValueChange = { enteredUrl = it }, label = { Text(stringResource(R.string.hc_address)) }, modifier = Modifier.fillMaxWidth(), singleLine = true) },
            confirmButton = { TextButton(enabled = valid, onClick = {
                customUrl = enteredUrl.trim(); preferences.edit().putString("url", customUrl).apply(); editing = false
                openWebmail("Ermis", customUrl)
            }) { Text(stringResource(R.string.hc_save)) } },
            dismissButton = { TextButton(onClick = { editing = false }) { Text(stringResource(R.string.hc_cancel)) } })
    }
}
