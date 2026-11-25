package de.mm20.launcher2.ui.settings.comms
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.ListPreferenceItem
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.component.preferences.SliderPreference
internal fun LazyListScope.PhoneCallsCategory(vm: CommsSettingsScreenVM) {
item {
            PreferenceCategory(title = stringResource(I18nR.string.hc_t9_search_settings)) {
                ListPreference(
                    title = stringResource(I18nR.string.hc_t9_language_alphabet),
                    items = listOf(
                        stringResource(I18nR.string.au3_commsfreeze_t9_latin) to "latin",
                        stringResource(I18nR.string.au3_commsfreeze_t9_greek) to "greek",
                        stringResource(I18nR.string.au3_commsfreeze_t9_cyrillic) to "cyrillic"
                    ),
                    value = t9Alphabet,
                    onValueChanged = { viewModel.setT9Alphabet(it) }
                )
            }
        }
}

internal fun LazyListScope.DialerCategory(vm: CommsSettingsScreenVM) {
item {
            PreferenceCategory(title = stringResource(I18nR.string.hc_incoming_call)) {
                Preference(title = stringResource(I18nR.string.caller_privacy_title), onClick = { backStack.add(CallerPrivacyRoute) })
                ListPreference(
                    title = stringResource(I18nR.string.hc_answer_style),
                    items = listOf(
                        stringResource(I18nR.string.au3_commsfreeze_answer_buttons) to "buttons",
                        stringResource(I18nR.string.au3_commsfreeze_answer_swipe) to "swipe",
                    ),
                    value = answerStyle,
                    onValueChanged = { viewModel.setAnswerStyle(it) }
                )
                ListPreference(
                    title = stringResource(I18nR.string.hc_sim_1_color),
                    items = listOf(
                        stringResource(I18nR.string.au3_commsfreeze_color_green) to "green", stringResource(I18nR.string.au3_commsfreeze_color_blue) to "blue", stringResource(I18nR.string.au3_commsfreeze_color_orange) to "orange", stringResource(I18nR.string.au3_commsfreeze_color_red) to "red",
                        stringResource(I18nR.string.au3_commsfreeze_color_purple) to "purple", stringResource(I18nR.string.au3_commsfreeze_color_pink) to "pink", stringResource(I18nR.string.au3_commsfreeze_color_teal) to "teal",
                    ),
                    value = sim1Color,
                    onValueChanged = { viewModel.setSim1Color(it) }
                )
                ListPreference(
                    title = stringResource(I18nR.string.hc_sim_2_color),
                    items = listOf(
                        stringResource(I18nR.string.au3_commsfreeze_color_green) to "green", stringResource(I18nR.string.au3_commsfreeze_color_blue) to "blue", stringResource(I18nR.string.au3_commsfreeze_color_orange) to "orange", stringResource(I18nR.string.au3_commsfreeze_color_red) to "red",
                        stringResource(I18nR.string.au3_commsfreeze_color_purple) to "purple", stringResource(I18nR.string.au3_commsfreeze_color_pink) to "pink", stringResource(I18nR.string.au3_commsfreeze_color_teal) to "teal",
                    ),
                    value = sim2Color,
                    onValueChanged = { viewModel.setSim2Color(it) }
                )
                val activeSims = remember { de.mm20.launcher2.comms.sms.SmsSims.active(context) }
                LaunchedEffect(simNames) {
                    simNames.forEach { (id, name) ->
                        id.toIntOrNull()?.let { de.mm20.launcher2.comms.sms.SmsSims.setCustomName(context, it, name) }
                    }
                }
                activeSims.forEach { sim ->
                    val key = sim.subId.toString()
                    TextPreference(
                        title = stringResource(I18nR.string.comms_sim_name, sim.slot + 1),
                        value = simNames[key].orEmpty(),
                        summary = simNames[key]?.takeIf { it.isNotBlank() } ?: sim.label,
                        placeholder = sim.label,
                        onValueChanged = { viewModel.setSimName(key, it) },
                    )
                }
                SwitchPreference(
                    title = stringResource(I18nR.string.hc_secure_call_screen),
                    summary = stringResource(I18nR.string.hc_block_screenshots_and_hide_the_call_scre),
                    value = secureCallScreen,
                    onValueChanged = { viewModel.setSecureCallScreen(it) }
                )
            }
        }
}

internal fun LazyListScope.IncomingCallsCategory(vm: CommsSettingsScreenVM) {
item {
            PreferenceCategory(title = stringResource(I18nR.string.hc_sounds_vibrations)) {
                SwitchPreference(
                    title = stringResource(I18nR.string.hc_dialpad_sounds),
                    summary = stringResource(I18nR.string.hc_play_sound_tones_when_using_the_dialpad),
                    value = dialpadSounds,
                    onValueChanged = { viewModel.setDialpadSounds(it) }
                )
                SwitchPreference(
                    title = stringResource(I18nR.string.hc_dialpad_vibration),
                    summary = stringResource(I18nR.string.hc_vibrate_when_using_the_dialpad),
                    value = dialpadVibration,
                    onValueChanged = { viewModel.setDialpadVibration(it) }
                )
                SwitchPreference(
                    title = stringResource(I18nR.string.hc_hide_dialpad_letters),
                    summary = stringResource(I18nR.string.hc_show_only_digits_on_the_keypad),
                    value = hideLetters,
                    onValueChanged = { viewModel.setHideDialpadLetters(it) }
                )
                SwitchPreference(
                    title = stringResource(I18nR.string.comms_dialer_rotate),
                    summary = stringResource(I18nR.string.comms_dialer_rotate_sum),
                    value = allowDialerLandscape,
                    onValueChanged = { viewModel.setAllowDialerLandscape(it) }
                )
                SwitchPreference(
                    title = stringResource(I18nR.string.hc_vibrate_on_answer),
                    summary = stringResource(I18nR.string.hc_vibrate_when_an_outgoing_call_is_answere),
                    value = vibrateOnAnswer,
                    onValueChanged = { viewModel.setVibrateOnAnswer(it) }
                )
                SwitchPreference(
                    title = stringResource(I18nR.string.hc_vibrate_on_hangup),
                    summary = stringResource(I18nR.string.hc_vibrate_when_a_call_is_disconnected),
                    value = vibrateOnHangup,
                    onValueChanged = { viewModel.setVibrateOnHangup(it) }
                )
            }
        }
}

internal fun LazyListScope.CallRecordingCategory(vm: CommsSettingsScreenVM) {
item {
            PreferenceCategory(title = stringResource(I18nR.string.hc_calling)) {
                SwitchPreference(
                    title = stringResource(I18nR.string.hc_tap_to_call),
                    summary = stringResource(I18nR.string.hc_tapping_a_recent_or_contact_places_a_cal),
                    value = tapToCall,
                    onValueChanged = { viewModel.setTapToCall(it) },
                )
                SwitchPreference(
                    title = stringResource(I18nR.string.hc_confirm_before_calling),
                    summary = stringResource(I18nR.string.hc_show_a_confirmation_sheet_before_placing),
                    value = confirmBeforeCall,
                    onValueChanged = { viewModel.setConfirmBeforeCall(it) },
                )
                SwitchPreference(
                    title = stringResource(I18nR.string.hc_remember_dialpad_digits),
                    summary = stringResource(I18nR.string.hc_keep_the_last_typed_number_when_reopenin),
                    value = rememberDialpad,
                    onValueChanged = { viewModel.setRememberDialpad(it) },
                )
                SwitchPreference(
                    title = stringResource(I18nR.string.hc_open_dialpad_on_launch),
                    summary = stringResource(I18nR.string.hc_show_the_keypad_when_phone_opens),
                    value = autoOpenDialpad,
                    onValueChanged = { viewModel.setAutoOpenDialpad(it) },
                )
                SwitchPreference(
                    title = stringResource(I18nR.string.hc_auto_redial),
                    summary = stringResource(I18nR.string.hc_retry_busy_unanswered_or_rejected_outgoi),
                    value = autoRedial,
                    onValueChanged = { viewModel.setAutoRedial(it) },
                )
                if (autoRedial) {
                    de.mm20.launcher2.ui.component.preferences.SliderPreference(
                        title = stringResource(I18nR.string.au4_settingsnew1_redial_attempts),
                        value = autoRedialAttempts.coerceIn(1, 10),
                        min = 1,
                        max = 10,
                        step = 1,
                        onValueChanged = { viewModel.setAutoRedialAttempts(it) },
                        label = { Text(it.toString()) },
                    )
                    de.mm20.launcher2.ui.component.preferences.SliderPreference(
                        title = stringResource(I18nR.string.au4_settingsnew1_redial_delay),
                        value = autoRedialDelaySec.coerceIn(3, 60),
                        min = 3,
                        max = 60,
                        step = 1,
                        onValueChanged = { viewModel.setAutoRedialDelaySec(it) },
                        label = { Text(stringResource(I18nR.string.au4_settingsnew1_seconds, it)) },
                    )
                }
                SwitchPreference(
                    title = stringResource(I18nR.string.hc_pocket_mode),
                    summary = stringResource(I18nR.string.hc_ignore_taps_on_incoming_calls_when_the_p),
                    value = pocketMode,
                    onValueChanged = { viewModel.setPocketMode(it) },
                )
                SwitchPreference(
                    title = stringResource(I18nR.string.hc_proximity_speaker),
                    summary = stringResource(I18nR.string.hc_switch_to_speaker_when_the_phone_is_away),
                    value = proximitySpeaker,
                    onValueChanged = { viewModel.setProximitySpeaker(it) },
                )
                SwitchPreference(
                    title = stringResource(I18nR.string.hc_show_numbers_in_recents),
                    value = showNumbersInRecents,
                    onValueChanged = { viewModel.setShowNumbersInRecents(it) },
                )
                SwitchPreference(
                    title = stringResource(I18nR.string.hc_missed_call_popup),
                    summary = stringResource(I18nR.string.hc_show_a_callback_sheet_after_a_missed_cal),
                    value = missedCallPopup,
                    onValueChanged = { viewModel.setMissedCallPopup(it) },
                )
                SwitchPreference(
                    title = stringResource(I18nR.string.hc_popup_after_every_call),
                    value = postCallPopup,
                    onValueChanged = { viewModel.setPostCallPopup(it) },
                )
                SwitchPreference(
                    title = stringResource(I18nR.string.hc_in_call_notes),
                    summary = stringResource(I18nR.string.hc_notes_button_and_floating_note_when_you),
                    value = inCallNotes,
                    onValueChanged = { viewModel.setInCallNotes(it) },
                )
                Preference(
                    title = stringResource(I18nR.string.hc_reject_with_sms),
                    summary = if (rejectSmsTemplate.isBlank()) stringResource(I18nR.string.au3_commsfreeze_off) else rejectSmsTemplate,
                    onClick = { showRejectSms = true },
                )
                Preference(
                    title = stringResource(I18nR.string.hc_notes),
                    summary = stringResource(I18nR.string.hc_all_contact_call_notes),
                    onClick = { backStack.add(CallerNotesRoute) },
                )
            }
        }
}

internal fun LazyListScope.PrivacyCategory(vm: CommsSettingsScreenVM) {
item {
            PreferenceCategory(title = stringResource(I18nR.string.hc_privacy)) {
                SwitchPreference(
                    title = stringResource(I18nR.string.hc_lock_phone_app),
                    summary = stringResource(I18nR.string.hc_require_biometrics_or_device_pin_when_op),
                    value = phoneAppLock,
                    onValueChanged = { viewModel.setPhoneAppLock(it) },
                )
                ListPreference(
                    title = stringResource(I18nR.string.hc_biometric_before_placing_a_call),
                    items = listOf(
                        stringResource(I18nR.string.au3_commsfreeze_off) to "none",
                        stringResource(I18nR.string.au3_commsfreeze_protect_all) to "all",
                        stringResource(I18nR.string.au3_commsfreeze_protect_listed) to "listed",
                    ),
                    value = callProtectMode,
                    onValueChanged = { if (it != null) viewModel.setCallProtectMode(it) },
                )
                Preference(
                    title = stringResource(I18nR.string.hc_hidden_contacts_pin),
                    summary = stringResource(I18nR.string.hc_dial_pin_on_the_keypad_4_6_digits),
                    onClick = { showPinDialog = true },
                )
                if (!stealthHiderMenu || hiderUnlocked) {
                    Preference(
                        title = stringResource(I18nR.string.hc_hidden_contacts),
                        summary = stringResource(I18nR.string.hc_numbers_hidden_from_lists_until_unlocked),
                        onClick = { backStack.add(HiddenContactsRoute) },
                    )
                    SwitchPreference(
                        title = stringResource(I18nR.string.hc_hide_from_contacts_tab),
                        value = hideFromContacts,
                        onValueChanged = { viewModel.setHideFromContacts(it) },
                    )
                    SwitchPreference(
                        title = stringResource(I18nR.string.hc_hide_from_recents),
                        value = hideFromRecents,
                        onValueChanged = { viewModel.setHideFromRecents(it) },
                    )
                    SwitchPreference(
                        title = stringResource(I18nR.string.hc_mask_name_on_incoming_calls),
                        value = maskHiddenIncoming,
                        onValueChanged = { viewModel.setMaskHiddenIncoming(it) },
                    )
                    SwitchPreference(
                        title = stringResource(I18nR.string.hc_hide_this_menu_after_pin_is_set),
                        summary = stringResource(I18nR.string.hc_stealth_privacy_hider_items_disappear_un),
                        value = stealthHiderMenu,
                        onValueChanged = { viewModel.setStealthHiderMenu(it) },
                    )
                }
            }
        }
}

internal fun LazyListScope.CallQualityCategory(vm: CommsSettingsScreenVM) {
item {
            PreferenceCategory(title = stringResource(I18nR.string.hc_gestures)) {
                SwitchPreference(
                    title = stringResource(I18nR.string.hc_raise_to_answer),
                    summary = stringResource(I18nR.string.hc_answer_incoming_calls_by_lifting_the_pho),
                    value = raiseToAnswer,
                    onValueChanged = { viewModel.setRaiseToAnswer(it) },
                )
                SwitchPreference(
                    title = stringResource(I18nR.string.hc_flip_to_decline),
                    summary = stringResource(I18nR.string.hc_decline_by_turning_the_phone_face_down),
                    value = flipToDecline,
                    onValueChanged = { viewModel.setFlipToDecline(it) },
                )
                SwitchPreference(
                    title = stringResource(I18nR.string.hc_rain_mode),
                    summary = stringResource(I18nR.string.hc_answer_with_a_left_right_left_right_shak),
                    value = rainMode,
                    onValueChanged = { viewModel.setRainMode(it) },
                )
                SwitchPreference(
                    title = stringResource(I18nR.string.hc_volume_keys_toggle_dnd),
                    summary = stringResource(I18nR.string.hc_up_up_down_down_enable_the_accessibility),
                    value = volumeDnd,
                    onValueChanged = { viewModel.setVolumeDnd(it) },
                )
                SwitchPreference(
                    title = stringResource(I18nR.string.hc_volume_dnd_only_on_lock_screen),
                    enabled = volumeDnd,
                    value = volumeDndLockOnly,
                    onValueChanged = { viewModel.setVolumeDndLockOnly(it) },
                )
                Preference(
                    title = stringResource(I18nR.string.hc_accessibility_service),
                    onClick = {
                        context.tryStartActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                )
                Preference(
                    title = stringResource(I18nR.string.hc_do_not_disturb_access),
                    onClick = {
                        context.tryStartActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                    },
                )
            }
        }
}

internal fun LazyListScope.SmsCategory(vm: CommsSettingsScreenVM) {
item {
            PreferenceCategory(title = stringResource(I18nR.string.hc_cellular_network)) {
                ListPreference(
                    title = stringResource(I18nR.string.hc_preferred_mode),
                    items = listOf(
                        stringResource(I18nR.string.au3_commsfreeze_net_auto) to "auto",
                        stringResource(I18nR.string.au3_commsfreeze_net_lte) to "lte",
                        stringResource(I18nR.string.au3_commsfreeze_net_nr_lte) to "nr_lte",
                        stringResource(I18nR.string.au3_commsfreeze_net_nr) to "nr",
                    ),
                    value = preferredNetworkMode,
                    onValueChanged = {
                        if (it != null) {
                            viewModel.setPreferredNetworkMode(it)
                            settingsScope.launch { CellularRadio.applyPreferred(context, it) }
                        }
                    },
                )
                ListPreference(
                    title = stringResource(I18nR.string.hc_control_backend),
                    items = listOf(
                        stringResource(I18nR.string.au3_commsfreeze_backend_auto) to "auto",
                        stringResource(I18nR.string.au3_commsfreeze_backend_shizuku) to "shizuku",
                        stringResource(I18nR.string.au3_commsfreeze_backend_root) to "root",
                    ),
                    value = networkBackend,
                    onValueChanged = { if (it != null) viewModel.setNetworkBackend(it) },
                )
                SwitchPreference(
                    title = stringResource(I18nR.string.hf_comms_lte_screen_off),
                    value = screenOffLte,
                    onValueChanged = { viewModel.setScreenOffLte(it) },
                )
                SwitchPreference(
                    title = stringResource(I18nR.string.hf_comms_lte_battery_saver),
                    value = batterySaverLte,
                    onValueChanged = { viewModel.setBatterySaverLte(it) },
                )
            }
        }
}

internal fun LazyListScope.BackupCategory(vm: CommsSettingsScreenVM) {
item {
            PreferenceCategory(title = stringResource(I18nR.string.hc_tools)) {
                Preference(
                    title = stringResource(I18nR.string.hc_fake_call),
                    summary = stringResource(I18nR.string.hc_schedule_a_simulated_incoming_call),
                    onClick = { backStack.add(FakeCallSettingsRoute) },
                )
                Preference(
                    title = stringResource(I18nR.string.hc_contact_groups),
                    summary = stringResource(I18nR.string.hc_system_and_google_contact_groups),
                    onClick = { backStack.add(ContactGroupsRoute) },
                )
                Preference(
                    title = stringResource(I18nR.string.hc_scheduled_sms),
                    onClick = { backStack.add(ScheduledSmsRoute) },
                )
                Preference(
                    title = stringResource(I18nR.string.hc_duplicate_contacts),
                    summary = stringResource(I18nR.string.hc_find_and_clean_contacts_that_share_a_num),
                    onClick = { backStack.add(DuplicateContactsRoute) },
                )
                Preference(
                    title = stringResource(I18nR.string.hc_import_vcard),
                    summary = stringResource(I18nR.string.hc_add_contacts_from_a_vcf_file),
                    onClick = { vcfPicker.launch("text/*") },
                )
            }
        }
}

