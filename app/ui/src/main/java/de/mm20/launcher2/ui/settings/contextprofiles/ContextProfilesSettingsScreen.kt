package de.mm20.launcher2.ui.settings.contextprofiles

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.preferences.ChargingType
import de.mm20.launcher2.preferences.ContextProfile
import de.mm20.launcher2.preferences.ContextProfileGestureOverrides
import de.mm20.launcher2.preferences.ContextProfileIcon
import de.mm20.launcher2.preferences.ContextProfileTrigger
import de.mm20.launcher2.preferences.FreezeProfile
import de.mm20.launcher2.preferences.GestureAction
import de.mm20.launcher2.preferences.WidgetScreenTarget
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.common.SearchablePicker
import de.mm20.launcher2.ui.component.preferences.GuardedPreference
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.ListPreferenceItem
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SliderPreference
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.component.preferences.TextPreference
import de.mm20.launcher2.ui.component.DismissableBottomSheet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data object ContextProfilesSettingsRoute : NavKey

@Composable
fun ContextProfilesSettingsScreen() {
    val viewModel: ContextProfilesSettingsScreenVM =
        viewModel(factory = ContextProfilesSettingsScreenVM.Factory)
    val context = LocalContext.current

    val enabled by viewModel.enabled.collectAsStateWithLifecycle(false)
    val profiles by viewModel.profiles.collectAsStateWithLifecycle(emptyList())
    val manualOverrideId by viewModel.manualOverrideId.collectAsStateWithLifecycle(null)
    val hasLocationPermission by viewModel.hasLocationPermission.collectAsStateWithLifecycle(null)
    val hasBluetoothPermission by viewModel.hasBluetoothPermission.collectAsStateWithLifecycle(null)
    val hasNotificationPolicyPermission by viewModel.hasNotificationPolicyPermission.collectAsStateWithLifecycle(null)
    val hasWriteSettingsPermission by viewModel.hasWriteSettingsPermission.collectAsStateWithLifecycle(null)

    var editingProfile by remember { mutableStateOf<ContextProfile?>(null) }
    var showNewProfileSheet by remember { mutableStateOf(false) }

    PreferenceScreen(title = stringResource(R.string.preference_screen_context_profiles)) {
        item {
            PreferenceCategory {
                SwitchPreference(
                    title = stringResource(R.string.preference_context_profiles_enabled),
                    summary = stringResource(R.string.preference_context_profiles_enabled_summary),
                    value = enabled,
                    onValueChanged = { viewModel.setEnabled(it) },
                )
            }
        }
        if (enabled) {
            item {
                PreferenceCategory(title = stringResource(R.string.preference_category_context_profiles)) {
                    for (profile in profiles) {
                        val isManuallyActive = manualOverrideId == profile.id
                        Preference(
                            icon = iconFor(profile.icon),
                            title = profile.name,
                            summary = triggerSummary(profile.trigger) +
                                    if (isManuallyActive) " · " + stringResource(R.string.context_profile_active_now) else "",
                            onClick = { editingProfile = profile },
                        )
                    }
                    Preference(
                        icon = R.drawable.add_24px,
                        title = stringResource(R.string.context_profile_add),
                        onClick = { showNewProfileSheet = true },
                    )
                }
            }
        }
    }

    if (showNewProfileSheet) {
        ContextProfileEditSheet(
            profile = ContextProfile(id = UUID.randomUUID().toString(), name = ""),
            isNew = true,
            manuallyActive = false,
            hasLocationPermission = hasLocationPermission,
            hasBluetoothPermission = hasBluetoothPermission,
            hasNotificationPolicyPermission = hasNotificationPolicyPermission,
            hasWriteSettingsPermission = hasWriteSettingsPermission,
            onRequestLocationPermission = { viewModel.requestLocationPermission(context as AppCompatActivity) },
            onRequestBluetoothPermission = { viewModel.requestBluetoothPermission(context as AppCompatActivity) },
            onRequestNotificationPolicyPermission = { viewModel.requestNotificationPolicyPermission(context as AppCompatActivity) },
            onRequestWriteSettingsPermission = { viewModel.requestWriteSettingsPermission(context as AppCompatActivity) },
            resolveSearchable = { viewModel.resolveSearchable(it) },
            onSave = { viewModel.saveProfile(it) },
            onDelete = null,
            onSetManuallyActive = { },
            onDismiss = { showNewProfileSheet = false },
        )
    }

    val profileBeingEdited = editingProfile
    if (profileBeingEdited != null) {
        ContextProfileEditSheet(
            profile = profileBeingEdited,
            isNew = false,
            manuallyActive = manualOverrideId == profileBeingEdited.id,
            hasLocationPermission = hasLocationPermission,
            hasBluetoothPermission = hasBluetoothPermission,
            hasNotificationPolicyPermission = hasNotificationPolicyPermission,
            hasWriteSettingsPermission = hasWriteSettingsPermission,
            onRequestLocationPermission = { viewModel.requestLocationPermission(context as AppCompatActivity) },
            onRequestBluetoothPermission = { viewModel.requestBluetoothPermission(context as AppCompatActivity) },
            onRequestNotificationPolicyPermission = { viewModel.requestNotificationPolicyPermission(context as AppCompatActivity) },
            onRequestWriteSettingsPermission = { viewModel.requestWriteSettingsPermission(context as AppCompatActivity) },
            resolveSearchable = { viewModel.resolveSearchable(it) },
            onSave = { viewModel.saveProfile(it) },
            onDelete = {
                viewModel.deleteProfile(profileBeingEdited.id)
                editingProfile = null
            },
            onSetManuallyActive = {
                viewModel.setManualOverride(if (it) profileBeingEdited.id else null)
            },
            onDismiss = { editingProfile = null },
        )
    }
}

@Composable
private fun ContextProfileEditSheet(
    profile: ContextProfile,
    isNew: Boolean,
    manuallyActive: Boolean,
    hasLocationPermission: Boolean?,
    hasBluetoothPermission: Boolean?,
    hasNotificationPolicyPermission: Boolean?,
    hasWriteSettingsPermission: Boolean?,
    onRequestLocationPermission: () -> Unit,
    onRequestBluetoothPermission: () -> Unit,
    onRequestNotificationPolicyPermission: () -> Unit,
    onRequestWriteSettingsPermission: () -> Unit,
    resolveSearchable: (String) -> Flow<SavableSearchable?>,
    onSave: (ContextProfile) -> Unit,
    onDelete: (() -> Unit)?,
    onSetManuallyActive: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var current by remember(profile.id) { mutableStateOf(profile) }
    var showLaunchAppPicker by remember { mutableStateOf(false) }

    DismissableBottomSheet(
        expanded = true,
        onDismissRequest = {
            if (current.name.isNotBlank()) onSave(current)
            onDismiss()
        },
    ) {
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                bottom = 16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
            ),
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (icon in ContextProfileIcon.entries) {
                        val selected = current.icon == icon
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceBright
                                )
                                .clickable { current = current.copy(icon = icon) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painterResource(iconFor(icon)),
                                contentDescription = null,
                                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item {
                TextPreference(
                    title = stringResource(R.string.context_profile_name),
                    value = current.name,
                    onValueChanged = { current = current.copy(name = it) },
                )
            }
            item {
                PreferenceCategory(title = stringResource(R.string.context_profile_trigger)) {
                    ListPreference(
                        title = stringResource(R.string.context_profile_trigger),
                        items = listOf(
                            ListPreferenceItem(stringResource(R.string.context_profile_trigger_manual), 0),
                            ListPreferenceItem(stringResource(R.string.context_profile_trigger_time), 1),
                            ListPreferenceItem(stringResource(R.string.context_profile_trigger_wifi), 2),
                            ListPreferenceItem(stringResource(R.string.context_profile_trigger_bluetooth), 3),
                            ListPreferenceItem(stringResource(R.string.context_profile_trigger_battery_saver), 4),
                            ListPreferenceItem(stringResource(R.string.context_profile_trigger_charging), 5),
                        ),
                        value = when (current.trigger) {
                            is ContextProfileTrigger.Manual -> 0
                            is ContextProfileTrigger.TimeWindow -> 1
                            is ContextProfileTrigger.Wifi -> 2
                            is ContextProfileTrigger.Bluetooth -> 3
                            is ContextProfileTrigger.BatterySaver -> 4
                            is ContextProfileTrigger.Charging -> 5
                        },
                        onValueChanged = {
                            current = current.copy(
                                trigger = when (it) {
                                    1 -> ContextProfileTrigger.TimeWindow(7, 0, 17, 0)
                                    2 -> ContextProfileTrigger.Wifi()
                                    3 -> ContextProfileTrigger.Bluetooth()
                                    4 -> ContextProfileTrigger.BatterySaver
                                    5 -> ContextProfileTrigger.Charging()
                                    else -> ContextProfileTrigger.Manual
                                }
                            )
                        },
                    )
                    when (val trigger = current.trigger) {
                        is ContextProfileTrigger.TimeWindow -> {
                            Text(
                                stringResource(
                                    R.string.context_profile_trigger_time_summary,
                                    "%02d:%02d".format(trigger.startHour, trigger.startMinute),
                                    "%02d:%02d".format(trigger.endHour, trigger.endMinute),
                                ),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        is ContextProfileTrigger.Wifi -> {
                            GuardedPreference(
                                locked = hasLocationPermission == false,
                                description = stringResource(R.string.missing_permission_context_profile_wifi),
                                onUnlock = onRequestLocationPermission,
                            ) {
                                TextPreference(
                                    title = stringResource(R.string.context_profile_trigger_wifi_ssid),
                                    summary = stringResource(R.string.context_profile_trigger_wifi_ssid_summary),
                                    value = trigger.ssids.firstOrNull() ?: "",
                                    onValueChanged = {
                                        current = current.copy(
                                            trigger = trigger.copy(ssids = setOfNotNull(it.takeIf { s -> s.isNotBlank() }))
                                        )
                                    },
                                )
                            }
                        }

                        is ContextProfileTrigger.Bluetooth -> {
                            GuardedPreference(
                                locked = hasBluetoothPermission == false,
                                description = stringResource(R.string.missing_permission_context_profile_bluetooth),
                                onUnlock = onRequestBluetoothPermission,
                            ) {
                                TextPreference(
                                    title = stringResource(R.string.context_profile_trigger_bluetooth_device),
                                    summary = stringResource(R.string.context_profile_trigger_bluetooth_device_summary),
                                    value = trigger.deviceNames.firstOrNull() ?: "",
                                    onValueChanged = {
                                        current = current.copy(
                                            trigger = trigger.copy(deviceNames = setOfNotNull(it.takeIf { s -> s.isNotBlank() }))
                                        )
                                    },
                                )
                            }
                        }

                        is ContextProfileTrigger.Charging -> {
                            ListPreference(
                                title = stringResource(R.string.context_profile_trigger_charging_type),
                                items = listOf(
                                    ListPreferenceItem(stringResource(R.string.charging_type_any), ChargingType.Any),
                                    ListPreferenceItem(stringResource(R.string.charging_type_usb), ChargingType.Usb),
                                    ListPreferenceItem(stringResource(R.string.charging_type_ac), ChargingType.Ac),
                                    ListPreferenceItem(stringResource(R.string.charging_type_wireless), ChargingType.Wireless),
                                ),
                                value = trigger.type,
                                onValueChanged = {
                                    current = current.copy(trigger = trigger.copy(type = it))
                                },
                            )
                        }

                        else -> {}
                    }
                }
            }
            item {
                PreferenceCategory(title = stringResource(R.string.context_profile_overrides)) {
                    ListPreference(
                        title = stringResource(R.string.preference_freeze_profile),
                        items = listOf(
                            ListPreferenceItem(stringResource(R.string.context_profile_no_override), null),
                            ListPreferenceItem(stringResource(R.string.freeze_profile_battery_saver), FreezeProfile.BatterySaver),
                            ListPreferenceItem(stringResource(R.string.freeze_profile_balanced), FreezeProfile.Balanced),
                            ListPreferenceItem(stringResource(R.string.freeze_profile_aggressive), FreezeProfile.Aggressive),
                            ListPreferenceItem(stringResource(R.string.freeze_profile_ultra_aggressive), FreezeProfile.UltraAggressive),
                        ),
                        value = current.freezeProfileOverride,
                        onValueChanged = { current = current.copy(freezeProfileOverride = it) },
                    )
                    ListPreference(
                        title = stringResource(R.string.context_profile_widget_page),
                        items = listOf(
                            ListPreferenceItem(stringResource(R.string.context_profile_no_override), null),
                            ListPreferenceItem(stringResource(R.string.gesture_action_widgets), WidgetScreenTarget.Widgets1),
                            ListPreferenceItem(
                                stringResource(R.string.gesture_action_widgets_indexed, 2),
                                WidgetScreenTarget.Widgets2
                            ),
                            ListPreferenceItem(
                                stringResource(R.string.gesture_action_widgets_indexed, 3),
                                WidgetScreenTarget.Widgets3
                            ),
                            ListPreferenceItem(
                                stringResource(R.string.gesture_action_widgets_indexed, 4),
                                WidgetScreenTarget.Widgets4
                            ),
                        ),
                        value = current.widgetScreenTargetOverride,
                        onValueChanged = { current = current.copy(widgetScreenTargetOverride = it) },
                    )
                    GestureOverrideRow(
                        title = stringResource(R.string.preference_gesture_swipe_up),
                        value = current.gestureOverrides.swipeUp,
                        onValueChanged = {
                            current = current.copy(gestureOverrides = current.gestureOverrides.copy(swipeUp = it))
                        },
                    )
                    GestureOverrideRow(
                        title = stringResource(R.string.preference_gesture_swipe_down),
                        value = current.gestureOverrides.swipeDown,
                        onValueChanged = {
                            current = current.copy(gestureOverrides = current.gestureOverrides.copy(swipeDown = it))
                        },
                    )
                    GestureOverrideRow(
                        title = stringResource(R.string.preference_gesture_double_tap),
                        value = current.gestureOverrides.doubleTap,
                        onValueChanged = {
                            current = current.copy(gestureOverrides = current.gestureOverrides.copy(doubleTap = it))
                        },
                    )
                    GuardedPreference(
                        locked = hasNotificationPolicyPermission == false,
                        description = stringResource(R.string.missing_permission_context_profile_dnd),
                        onUnlock = onRequestNotificationPolicyPermission,
                    ) {
                        ListPreference(
                            title = stringResource(R.string.context_profile_override_dnd),
                            items = listOf(
                                ListPreferenceItem(stringResource(R.string.context_profile_no_override), null),
                                ListPreferenceItem(stringResource(R.string.context_profile_override_dnd_on), true),
                                ListPreferenceItem(stringResource(R.string.context_profile_override_dnd_off), false),
                            ),
                            value = current.doNotDisturbOverride,
                            onValueChanged = { current = current.copy(doNotDisturbOverride = it) },
                        )
                    }
                    GuardedPreference(
                        locked = hasWriteSettingsPermission == false,
                        description = stringResource(R.string.missing_permission_context_profile_brightness),
                        onUnlock = onRequestWriteSettingsPermission,
                    ) {
                        Column {
                            SwitchPreference(
                                title = stringResource(R.string.context_profile_override_brightness),
                                value = current.brightnessOverride != null,
                                onValueChanged = {
                                    current = current.copy(brightnessOverride = if (it) 50 else null)
                                },
                            )
                            val brightness = current.brightnessOverride
                            if (brightness != null) {
                                SliderPreference(
                                    title = stringResource(R.string.context_profile_override_brightness_value),
                                    value = brightness,
                                    min = 1,
                                    max = 100,
                                    onValueChanged = { current = current.copy(brightnessOverride = it) },
                                    label = { Text("$it%") },
                                )
                            }
                        }
                    }
                    val launchAppKey = current.launchAppOverride
                    val launchApp by (launchAppKey?.let { resolveSearchable(it) } ?: emptyFlow())
                        .collectAsStateWithLifecycle(null)
                    Preference(
                        icon = R.drawable.open_in_new_24px,
                        title = stringResource(R.string.context_profile_override_launch_app),
                        summary = launchApp?.label ?: stringResource(R.string.context_profile_no_override),
                        onClick = { showLaunchAppPicker = true },
                        controls = if (launchAppKey != null) {
                            {
                                IconButton(onClick = { current = current.copy(launchAppOverride = null) }) {
                                    Icon(
                                        painterResource(R.drawable.close_24px),
                                        contentDescription = stringResource(R.string.context_profile_no_override),
                                    )
                                }
                            }
                        } else null,
                    )
                }
            }
            if (!isNew) {
                item {
                    PreferenceCategory {
                        SwitchPreference(
                            title = stringResource(R.string.context_profile_activate_now),
                            summary = stringResource(R.string.context_profile_activate_now_summary),
                            value = manuallyActive,
                            onValueChanged = onSetManuallyActive,
                        )
                        if (onDelete != null) {
                            Preference(
                                icon = R.drawable.delete_24px,
                                title = stringResource(R.string.context_profile_delete),
                                onClick = onDelete,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showLaunchAppPicker) {
        val launchAppKey = current.launchAppOverride
        val launchApp by (launchAppKey?.let { resolveSearchable(it) } ?: emptyFlow())
            .collectAsStateWithLifecycle(null)
        DismissableBottomSheet(
            expanded = true,
            onDismissRequest = { showLaunchAppPicker = false },
        ) {
            SearchablePicker(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                ),
                value = launchApp,
                onValueChanged = {
                    current = current.copy(launchAppOverride = it?.key)
                    showLaunchAppPicker = false
                },
            )
        }
    }
}

@Composable
private fun GestureOverrideRow(
    title: String,
    value: GestureAction?,
    onValueChanged: (GestureAction?) -> Unit,
) {
    ListPreference(
        title = title,
        items = listOf(
            ListPreferenceItem(stringResource(R.string.context_profile_no_override), null),
            ListPreferenceItem(stringResource(R.string.gesture_action_none), GestureAction.NoAction),
            ListPreferenceItem(stringResource(R.string.gesture_action_open_search), GestureAction.Search),
            ListPreferenceItem(stringResource(R.string.gesture_action_notifications), GestureAction.Notifications),
            ListPreferenceItem(stringResource(R.string.gesture_action_quick_settings), GestureAction.QuickSettings),
            ListPreferenceItem(stringResource(R.string.gesture_action_lock_screen), GestureAction.ScreenLock),
            ListPreferenceItem(stringResource(R.string.gesture_action_recents), GestureAction.Recents),
        ),
        value = value,
        onValueChanged = onValueChanged,
        iconPadding = true,
    )
}

private fun iconFor(icon: ContextProfileIcon): Int = when (icon) {
    ContextProfileIcon.Home -> R.drawable.home_24px
    ContextProfileIcon.Work -> R.drawable.dashboard_2_24px
    ContextProfileIcon.Car -> R.drawable.directions_car_24px
    ContextProfileIcon.Gaming -> R.drawable.sports_esports_24px
    ContextProfileIcon.BatterySaver -> R.drawable.battery_full_24px
    ContextProfileIcon.Sleep -> R.drawable.bed_24px
    ContextProfileIcon.Custom -> R.drawable.tune_24px
}

@Composable
private fun triggerSummary(trigger: ContextProfileTrigger): String = when (trigger) {
    is ContextProfileTrigger.Manual -> stringResource(R.string.context_profile_trigger_manual)
    is ContextProfileTrigger.TimeWindow -> stringResource(
        R.string.context_profile_trigger_time_summary,
        "%02d:%02d".format(trigger.startHour, trigger.startMinute),
        "%02d:%02d".format(trigger.endHour, trigger.endMinute),
    )

    is ContextProfileTrigger.Wifi -> stringResource(R.string.context_profile_trigger_wifi)
    is ContextProfileTrigger.Bluetooth -> stringResource(R.string.context_profile_trigger_bluetooth)
    is ContextProfileTrigger.BatterySaver -> stringResource(R.string.context_profile_trigger_battery_saver)
    is ContextProfileTrigger.Charging -> stringResource(R.string.context_profile_trigger_charging)
}
