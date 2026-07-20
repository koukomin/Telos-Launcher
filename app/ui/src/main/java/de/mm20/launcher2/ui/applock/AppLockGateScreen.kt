package de.mm20.launcher2.ui.applock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import de.mm20.launcher2.preferences.SettingsLockMethod
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.settings.protection.authenticateSettings
import de.mm20.launcher2.ui.settings.protection.canAuthenticateSettings

/**
 * Full-screen gate shown by [AppLockActivity] over a locked app the instant it comes to the
 * foreground - same visual language and BiometricPrompt plumbing as
 * [de.mm20.launcher2.ui.launcher.lock.LauncherLockGate], just parameterized with the target app's
 * label instead of protecting the launcher's own home screen.
 *
 * Strictly fail-closed: [onUnlocked] only ever fires after an explicit successful
 * authentication. Dismissing or failing the prompt calls [onCancelled] instead, which the
 * activity uses to send the user home rather than revealing the locked app underneath.
 */
@Composable
fun AppLockGateScreen(
    appLabel: String,
    lockMethod: SettingsLockMethod,
    onUnlocked: () -> Unit,
    onCancelled: () -> Unit,
) {
    var attempted by remember { mutableStateOf(false) }
    val activity = LocalContext.current as? FragmentActivity
    val promptTitle = stringResource(R.string.app_lock_prompt_title, appLabel)
    val authAvailable = activity != null && canAuthenticateSettings(activity, lockMethod)

    fun authenticate() {
        if (!authAvailable) return
        authenticateSettings(activity!!, lockMethod, promptTitle) { success ->
            if (success) onUnlocked() else onCancelled()
        }
    }

    LaunchedEffect(Unit) {
        if (!attempted) {
            attempted = true
            authenticate()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                painterResource(R.drawable.lock_48px),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(
                    if (authAvailable) R.string.app_lock_locked_message
                    else R.string.settings_locked_no_authenticator,
                    appLabel,
                ),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 16.dp),
            )
            if (authAvailable) {
                Button(onClick = { authenticate() }) {
                    Text(stringResource(R.string.settings_locked_unlock))
                }
            } else {
                Button(onClick = onCancelled) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        }
    }
}
