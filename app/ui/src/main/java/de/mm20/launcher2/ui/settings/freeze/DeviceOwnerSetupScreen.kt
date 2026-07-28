package de.mm20.launcher2.ui.settings.freeze

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.Banner
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import kotlinx.serialization.Serializable

@Serializable
data object DeviceOwnerSetupRoute : NavKey

@Composable
fun DeviceOwnerSetupScreen() {
    val viewModel: DeviceOwnerSetupScreenVM = viewModel()
    val isDeviceOwner by viewModel.isDeviceOwner.collectAsStateWithLifecycle()
    var understood by remember { mutableStateOf(false) }

    val clipboardManager = LocalClipboardManager.current
    val hapticFeedback = LocalHapticFeedback.current

    val setupCommand = "adb shell dpm set-device-owner ${viewModel.adminComponent}"
    val removeCommand = "adb shell dpm remove-active-admin ${viewModel.adminComponent}"

    PreferenceScreen(
        title = stringResource(R.string.freeze_device_owner_setup_title),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item {
            Preference(
                icon = R.drawable.ac_unit_24px,
                title = stringResource(
                    if (isDeviceOwner == true) R.string.freeze_device_owner_status_active
                    else R.string.freeze_device_owner_status_inactive
                ),
                onClick = { viewModel.refresh() },
            )
        }
        if (isDeviceOwner == true) {
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Button(onClick = { viewModel.useAsBackend() }) {
                        Text(stringResource(R.string.freeze_device_owner_use_as_backend))
                    }
                }
            }
        }
        item {
            Banner(
                modifier = Modifier.padding(horizontal = 16.dp),
                text = stringResource(R.string.freeze_device_owner_risk_warning),
                icon = R.drawable.error_24px,
            )
        }
        item {
            PreferenceCategory {
                Text(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    text = stringResource(R.string.freeze_device_owner_requirements),
                    style = MaterialTheme.typography.bodyMedium,
                )
                SwitchPreference(
                    title = stringResource(R.string.freeze_device_owner_confirm_understand),
                    value = understood,
                    onValueChanged = { understood = it },
                )
            }
        }
        if (understood) {
            item {
                PreferenceCategory {
                    Text(
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                        text = stringResource(R.string.freeze_device_owner_step1),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                        text = stringResource(R.string.freeze_device_owner_step2),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    CommandBlock(
                        command = setupCommand,
                        onCopy = {
                            clipboardManager.setText(AnnotatedString(setupCommand))
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                    )
                    Text(
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                        text = stringResource(R.string.freeze_device_owner_step3),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(modifier = Modifier.padding(16.dp)) {
                        Button(onClick = { viewModel.refresh() }) {
                            Text(stringResource(R.string.freeze_device_owner_check_status))
                        }
                    }
                }
            }
            item {
                Banner(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    text = stringResource(R.string.freeze_device_owner_scope_note),
                    icon = R.drawable.info_24px,
                )
            }
            item {
                PreferenceCategory(title = stringResource(R.string.freeze_device_owner_undo_title)) {
                    Text(
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                        text = stringResource(R.string.freeze_device_owner_undo_text),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    CommandBlock(
                        command = removeCommand,
                        onCopy = {
                            clipboardManager.setText(AnnotatedString(removeCommand))
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CommandBlock(command: String, onCopy: () -> Unit) {
    OutlinedCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        ) {
            Text(
                modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                text = command,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            )
            IconButton(onClick = onCopy) {
                Icon(
                    painter = painterResource(R.drawable.content_copy_24px),
                    contentDescription = stringResource(R.string.freeze_device_owner_copy_command),
                )
            }
        }
    }
}
