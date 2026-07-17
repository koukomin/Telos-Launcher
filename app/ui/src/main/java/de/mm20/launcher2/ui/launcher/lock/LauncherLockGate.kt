package de.mm20.launcher2.ui.launcher.lock

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
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.preferences.protection.ProtectionSettings
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.settings.protection.authenticateSettings
import de.mm20.launcher2.ui.settings.protection.canAuthenticateSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class LauncherLockGateVM : ViewModel(), KoinComponent {
    private val protectionSettings: ProtectionSettings by inject()

    val lockLauncher = protectionSettings.lockLauncher
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val lockMethod = protectionSettings.lockMethod
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
}

/**
 * Gates [content] (the home screen) behind the system BiometricPrompt, opt-in via
 * [ProtectionSettings.lockLauncher]. Deliberately does not offer the custom-PIN option that
 * the settings lock has - this is a wider-blast-radius surface, so it only ever accepts the
 * system credential.
 *
 * The unlocked state resets on [Lifecycle.Event.ON_STOP], i.e. whenever the launcher activity
 * stops being visible (another app comes to the front, or the screen turns off), so returning
 * home always requires re-authenticating - it is not a one-time unlock for the process
 * lifetime.
 *
 * When [enabled] is false, content is always shown unconditionally: used by the caller to skip
 * the lock entirely for non-home surfaces (e.g. assistant mode) regardless of the preference.
 */
@Composable
fun LauncherLockGate(
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        content()
        return
    }

    val viewModel: LauncherLockGateVM = viewModel()
    val lockLauncher by viewModel.lockLauncher.collectAsStateWithLifecycle()
    val lockMethod by viewModel.lockMethod.collectAsStateWithLifecycle()

    var unlocked by remember { mutableStateOf(false) }
    val activity = LocalContext.current as? FragmentActivity
    val promptTitle = stringResource(R.string.launcher_lock_prompt_title)

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                unlocked = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    when {
        lockLauncher == null || lockMethod == null -> {}

        lockLauncher == false || unlocked -> content()

        else -> {
            val currentMethod = lockMethod!!
            val authAvailable = activity != null && canAuthenticateSettings(activity, currentMethod)

            if (authAvailable) {
                LaunchedEffect(Unit) {
                    authenticateSettings(activity, currentMethod, promptTitle) {
                        unlocked = it
                    }
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
                            if (authAvailable) R.string.launcher_locked_message
                            else R.string.settings_locked_no_authenticator
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                    if (authAvailable) {
                        Button(onClick = {
                            authenticateSettings(activity, currentMethod, promptTitle) {
                                unlocked = it
                            }
                        }) {
                            Text(stringResource(R.string.settings_locked_unlock))
                        }
                    }
                }
            }
        }
    }
}
