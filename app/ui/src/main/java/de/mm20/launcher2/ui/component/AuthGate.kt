package de.mm20.launcher2.ui.component
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.mm20.launcher2.comms.AuthManager
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.settings.protection.CustomLockDialog
import de.mm20.launcher2.ui.settings.protection.SettingsLockVM
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

/**
 * Device PIN/pattern/password/biometrics, or the launcher custom PIN when that is set.
 */
@Composable
fun AuthGate(
    enabled: Boolean,
    title: String,
    unlocked: Boolean,
    onUnlocked: () -> Unit,
    onCancelled: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (!enabled || unlocked) {
        content()
        return
    }
    val activity = LocalActivity.current as? FragmentActivity
    val lockVm: SettingsLockVM = viewModel()
    val useCustom by lockVm.useCustomLock.collectAsStateWithLifecycle(false)
    val hashed by lockVm.customLockHashed.collectAsStateWithLifecycle(null)
    val custom = useCustom == true && !hashed.isNullOrBlank()
    var showPin by remember { mutableStateOf(false) }
    var pinWrong by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val promptTitle = title

    fun promptSystem() {
        scope.launch {
            val ok = activity != null && AuthManager().authenticateNative(activity, promptTitle)
            if (ok) onUnlocked() else onCancelled()
        }
    }

    LaunchedEffect(custom) {
        if (custom) showPin = true else promptSystem()
    }

    if (showPin) {
        CustomLockDialog(
            title = stringResource(I18nR.string.custom_lock_dialog_title),
            onConfirm = { pin ->
                scope.launch {
                    val ok = lockVm.verifyCustomLock(pin)
                    if (ok) {
                        showPin = false
                        onUnlocked()
                    } else {
                        kotlinx.coroutines.delay(800)
                        pinWrong = true
                    }
                }
            },
            errorMessage = if (pinWrong) stringResource(I18nR.string.au3_secplug_wrong_pin) else null,
            onPinChanged = { pinWrong = false },
            onDismiss = {
                showPin = false
                onCancelled()
            },
        )
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painterResource(BaseR.drawable.lock_48px),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            stringResource(I18nR.string.auth_gate_message),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
        Button(onClick = { if (custom) showPin = true else promptSystem() }, modifier = Modifier.padding(top = 16.dp)) {
            Text(stringResource(I18nR.string.auth_gate_unlock))
        }
    }
}
