package de.mm20.launcher2.ui.settings.tweaks

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.Banner
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import kotlinx.serialization.Serializable

@Serializable
data object TweaksSettingsRoute : NavKey

@Composable
fun TweaksSettingsScreen() {
    val vm: TweaksSettingsScreenVM = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        vm.refreshBackendState()
    }

    val hasPrivilegedBackend = state.isShizukuAvailable || state.isRootAvailable
    val backendStatusSummary = when {
        state.isRootAvailable -> "✓ Root Backend Active"
        state.isShizukuAvailable -> "✓ Shizuku Backend Active"
        else -> "🔒 Requires Shizuku or Root permission (Backend Disabled)"
    }

    PreferenceScreen(
        title = { Text("Tweaks & Essentials Engine") },
    ) {
        item {
            Banner(
                text = "Essential tools, mods and performance optimizations. $backendStatusSummary",
                icon = R.drawable.settings_24px,
            )
        }

        item {
            PreferenceCategory(title = "Status Bar & Display Tweaks") {
                SwitchPreference(
                    title = "Show Seconds in Status Bar Clock",
                    summary = if (hasPrivilegedBackend) "Displays seconds on the status bar clock" else "Requires Shizuku or Root",
                    value = state.clockSeconds,
                    enabled = hasPrivilegedBackend,
                    onValueChanged = { vm.toggleClockSeconds(it) },
                )
                SwitchPreference(
                    title = "Hide Gesture Navigation Bar (Pill)",
                    summary = if (hasPrivilegedBackend) "Hides the bottom gesture bar for full screen experience" else "Requires Shizuku or Root",
                    value = state.hideNavPill,
                    enabled = hasPrivilegedBackend,
                    onValueChanged = { vm.toggleHideNavPill(it) },
                )
                SwitchPreference(
                    title = "Force Peak Refresh Rate (120Hz)",
                    summary = if (hasPrivilegedBackend) "Forces max display refresh rate across all apps" else "Requires Shizuku or Root",
                    value = state.peakRefresh120,
                    enabled = hasPrivilegedBackend,
                    onValueChanged = { vm.togglePeakRefresh120(it) },
                )
                SwitchPreference(
                    title = "Always On Display (AOD)",
                    summary = if (hasPrivilegedBackend) "Toggles Ambient / Always On Display mode" else "Requires Shizuku or Root",
                    value = state.alwaysOnDisplay,
                    enabled = hasPrivilegedBackend,
                    onValueChanged = { vm.toggleAlwaysOnDisplay(it) },
                )
            }
        }

        item {
            PreferenceCategory(title = "Sound & Haptics Tweaks") {
                SwitchPreference(
                    title = "Extended 30-Step Volume Control",
                    summary = if (hasPrivilegedBackend) "Increases volume steps for fine-grained control" else "Requires Shizuku or Root",
                    value = state.volume30Steps,
                    enabled = hasPrivilegedBackend,
                    onValueChanged = { vm.toggleVolume30Steps(it) },
                )
                SwitchPreference(
                    title = "Disable High Volume Hearing Protection",
                    summary = if (hasPrivilegedBackend) "Disables high volume safety warning dialog" else "Requires Shizuku or Root",
                    value = state.disableSafeVolume,
                    enabled = hasPrivilegedBackend,
                    onValueChanged = { vm.toggleDisableSafeVolume(it) },
                )
                SwitchPreference(
                    title = "Vibrate on Call Connected",
                    summary = if (hasPrivilegedBackend) "Provides haptic vibration feedback when an outgoing call connects" else "Requires Shizuku or Root",
                    value = state.callVibrateConnect,
                    enabled = hasPrivilegedBackend,
                    onValueChanged = { vm.toggleCallVibrateConnect(it) },
                )
            }
        }

        item {
            PreferenceCategory(title = "Notifications & Torch Alerts") {
                SwitchPreference(
                    title = "Flashlight Pulse on Call Alert",
                    summary = "Flashes LED torch on incoming phone call",
                    value = state.flashPulseCall,
                    enabled = true,
                    onValueChanged = { vm.toggleFlashPulseCall(it) },
                )
            }
        }

        item {
            PreferenceCategory(title = "Network & Connectivity") {
                SwitchPreference(
                    title = "Mobile Data Always Active",
                    summary = if (hasPrivilegedBackend) "Keeps mobile data connected in background during Wi-Fi for fast switching" else "Requires Shizuku or Root",
                    value = state.mobileDataAlwaysOn,
                    enabled = hasPrivilegedBackend,
                    onValueChanged = { vm.toggleMobileDataAlwaysOn(it) },
                )
            }
        }

        item {
            PreferenceCategory(title = "Battery & Power Tweaks") {
                SwitchPreference(
                    title = "Aggressive Doze Mode",
                    summary = if (hasPrivilegedBackend) "Forces immediate deep sleep idle mode when screen turns off" else "Requires Shizuku or Root",
                    value = state.forceAggressiveDoze,
                    enabled = hasPrivilegedBackend,
                    onValueChanged = { vm.toggleForceDoze(it) },
                )
                SwitchPreference(
                    title = "Caffeinate (Keep Screen Awake)",
                    summary = "Prevents screen from sleeping while using Telos",
                    value = state.caffeinate,
                    enabled = true,
                    onValueChanged = { vm.toggleCaffeinate(it) },
                )
                SwitchPreference(
                    title = "Pocket Mode",
                    summary = "Prevents accidental touch events when proximity sensor detects pocket",
                    value = state.pocketMode,
                    enabled = true,
                    onValueChanged = { vm.togglePocketMode(it) },
                )
                SwitchPreference(
                    title = "Double-Tap Status Bar to Sleep",
                    summary = "Double tap empty launcher/status bar space to lock screen",
                    value = state.doubleTapSleep,
                    enabled = true,
                    onValueChanged = { vm.toggleDoubleTapSleep(it) },
                )
            }
        }
    }
}
