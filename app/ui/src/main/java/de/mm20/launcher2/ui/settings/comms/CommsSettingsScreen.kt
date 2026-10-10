// === TELOS_PENDING_REVIEW_START: comms_settings_engine ===
package de.mm20.launcher2.ui.settings.comms

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import android.telecom.TelecomManager
import androidx.core.content.getSystemService
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ktx.tryStartActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.Preference
import android.Manifest
import de.mm20.launcher2.ui.comms.CallRecordingsRoute
import de.mm20.launcher2.ui.comms.CallerNotesRoute
import de.mm20.launcher2.ui.comms.ContactGroupsRoute
import de.mm20.launcher2.ui.comms.HiddenContactsRoute
import de.mm20.launcher2.ui.comms.ScheduledSmsRoute
import de.mm20.launcher2.comms.radio.CellularRadio
import android.provider.Settings as AndroidSettings
import android.content.Intent
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import de.mm20.launcher2.ui.comms.DuplicateContactsRoute
import de.mm20.launcher2.ui.comms.FakeCallSettingsRoute
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.locals.LocalBackStack
import kotlinx.serialization.Serializable

@Serializable
data object CommsSettingsRoute : NavKey

@Composable
fun CommsSettingsScreen() {
    val viewModel: CommsSettingsScreenVM = viewModel()
    val speedDials by viewModel.speedDials.collectAsStateWithLifecycle()
    val t9Alphabet by viewModel.t9Alphabet.collectAsStateWithLifecycle()
    val defaultSim by viewModel.defaultSim.collectAsStateWithLifecycle()
    val dialpadSounds by viewModel.dialpadSounds.collectAsStateWithLifecycle()
    val dialpadVibration by viewModel.dialpadVibration.collectAsStateWithLifecycle()
    val vibrateOnAnswer by viewModel.vibrateOnAnswer.collectAsStateWithLifecycle()
    val vibrateOnHangup by viewModel.vibrateOnHangup.collectAsStateWithLifecycle()
    val clirPrefix by viewModel.clirPrefix.collectAsStateWithLifecycle()
    val enableSpamBlocking by viewModel.enableSpamBlocking.collectAsStateWithLifecycle()
    val hideLetters by viewModel.hideDialpadLetters.collectAsStateWithLifecycle()
    val blockHidden by viewModel.blockHiddenNumbers.collectAsStateWithLifecycle()
    val blockUnknown by viewModel.blockUnknownNumbers.collectAsStateWithLifecycle()
    val blockInternational by viewModel.blockInternational.collectAsStateWithLifecycle()
    val clirEnabled by viewModel.clirEnabled.collectAsStateWithLifecycle()
    val autoRecord by viewModel.autoRecordCalls.collectAsStateWithLifecycle()
    val recordingQuality by viewModel.recordingQuality.collectAsStateWithLifecycle()
    val recordingBackend by viewModel.recordingBackend.collectAsStateWithLifecycle()
    val rememberDialpad by viewModel.rememberDialpad.collectAsStateWithLifecycle()
    val confirmBeforeCall by viewModel.confirmBeforeCall.collectAsStateWithLifecycle()
    val tapToCall by viewModel.tapToCall.collectAsStateWithLifecycle()
    val autoRedial by viewModel.autoRedial.collectAsStateWithLifecycle()
    val autoRedialAttempts by viewModel.autoRedialAttempts.collectAsStateWithLifecycle()
    val autoRedialDelaySec by viewModel.autoRedialDelaySec.collectAsStateWithLifecycle()
    val autoOpenDialpad by viewModel.autoOpenDialpad.collectAsStateWithLifecycle()
    val rejectSmsTemplate by viewModel.rejectSmsTemplate.collectAsStateWithLifecycle()
    val secureCallScreen by viewModel.secureCallScreen.collectAsStateWithLifecycle()
    val sim1Color by viewModel.sim1Color.collectAsStateWithLifecycle()
    val sim2Color by viewModel.sim2Color.collectAsStateWithLifecycle()
    val answerStyle by viewModel.answerStyle.collectAsStateWithLifecycle()
    val recordingAutoDeleteDays by viewModel.recordingAutoDeleteDays.collectAsStateWithLifecycle()
    val pocketMode by viewModel.pocketMode.collectAsStateWithLifecycle()
    val proximitySpeaker by viewModel.proximitySpeaker.collectAsStateWithLifecycle()
    val showNumbersInRecents by viewModel.showNumbersInRecents.collectAsStateWithLifecycle()
    val missedCallPopup by viewModel.missedCallPopup.collectAsStateWithLifecycle()
    val postCallPopup by viewModel.postCallPopup.collectAsStateWithLifecycle()
    val inCallNotes by viewModel.inCallNotes.collectAsStateWithLifecycle()
    val hideFromContacts by viewModel.hideFromContacts.collectAsStateWithLifecycle()
    val hideFromRecents by viewModel.hideFromRecents.collectAsStateWithLifecycle()
    val maskHiddenIncoming by viewModel.maskHiddenIncoming.collectAsStateWithLifecycle()
    val stealthHiderMenu by viewModel.stealthHiderMenu.collectAsStateWithLifecycle()
    val phoneAppLock by viewModel.phoneAppLock.collectAsStateWithLifecycle()
    val callProtectMode by viewModel.callProtectMode.collectAsStateWithLifecycle()
    val raiseToAnswer by viewModel.raiseToAnswer.collectAsStateWithLifecycle()
    val flipToDecline by viewModel.flipToDecline.collectAsStateWithLifecycle()
    val rainMode by viewModel.rainMode.collectAsStateWithLifecycle()
    val volumeDnd by viewModel.volumeDnd.collectAsStateWithLifecycle()
    val volumeDndLockOnly by viewModel.volumeDndLockOnly.collectAsStateWithLifecycle()
    val preferredNetworkMode by viewModel.preferredNetworkMode.collectAsStateWithLifecycle()
    val networkBackend by viewModel.networkBackend.collectAsStateWithLifecycle()
    val screenOffLte by viewModel.screenOffLte.collectAsStateWithLifecycle()
    val batterySaverLte by viewModel.batterySaverLte.collectAsStateWithLifecycle()
    val settingsScope = rememberCoroutineScope()
    val hiderUnlocked by de.mm20.launcher2.comms.privacy.PrivacySession.hiderUnlocked.collectAsStateWithLifecycle()
    var showPinDialog by rememberSaveable { mutableStateOf(false) }
    
    val context = LocalContext.current
    val backStack = LocalBackStack.current
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.setAutoRecordCalls(true)
    }
    var backupPassword by remember { mutableStateOf("") }
    var pendingExportUri by rememberSaveable { mutableStateOf<android.net.Uri?>(null) }
    var showExportPassword by rememberSaveable { mutableStateOf(false) }
    var showImportPassword by remember { mutableStateOf(false) }
    var pendingImportText by remember { mutableStateOf<String?>(null) }
    val backupCreate = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            pendingExportUri = uri
            showExportPassword = true
        }
    }
    val backupOpen = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        pendingImportText = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        if (pendingImportText.isNullOrBlank()) {
            Toast.makeText(context, context.getString(R.string.hc_could_not_read_backup), Toast.LENGTH_SHORT).show()
        } else {
            showImportPassword = true
        }
    }
    val vcfPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        if (text.isNullOrBlank()) {
            Toast.makeText(context, context.getString(R.string.hc_could_not_read_file), Toast.LENGTH_SHORT).show()
        } else {
            viewModel.importVcf(text) { count ->
                Toast.makeText(context, context.getString(R.string.hc_imported_contacts, count), Toast.LENGTH_SHORT).show()
            }
        }
    }

    var showSpeedDialDialogFor by rememberSaveable { mutableStateOf<Int?>(null) }
    var showRejectSms by rememberSaveable { mutableStateOf(false) }
    if (showPinDialog) {
        var pin by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text(stringResource(R.string.hc_hidden_contacts_pin)) },
            text = {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter { ch -> ch.isDigit() }.take(6) },
                    label = { Text(stringResource(R.string.hf_comms_pin_digits)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (pin.length in 4..6) {
                        viewModel.setPin(pin)
                        showPinDialog = false
                    }
                }) { Text(stringResource(R.string.hc_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) { Text(stringResource(R.string.hc_cancel)) }
            },
        )
    }
    if (showRejectSms) {
        var input by rememberSaveable { mutableStateOf(rejectSmsTemplate) }
        AlertDialog(
            onDismissRequest = { showRejectSms = false },
            title = { Text(stringResource(R.string.hc_reject_with_sms)) },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text(stringResource(R.string.hc_message)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setRejectSmsTemplate(input.trim())
                    showRejectSms = false
                }) { Text(stringResource(R.string.hc_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showRejectSms = false }) { Text(stringResource(R.string.hc_cancel)) }
            },
        )
    }

    PreferenceScreen(title = { Text(stringResource(R.string.hc_settings)) }) {
        item {
            PreferenceCategory(title = stringResource(R.string.hc_t9_search_settings)) {
                ListPreference(
                    title = stringResource(R.string.hc_t9_language_alphabet),
                    items = listOf(
                        stringResource(R.string.au3_commsfreeze_t9_latin) to "latin",
                        stringResource(R.string.au3_commsfreeze_t9_greek) to "greek",
                        stringResource(R.string.au3_commsfreeze_t9_cyrillic) to "cyrillic"
                    ),
                    value = t9Alphabet,
                    onValueChanged = { viewModel.setT9Alphabet(it) }
                )
            }
        }

        item {
            PreferenceCategory(title = stringResource(R.string.hc_incoming_call)) {
                ListPreference(
                    title = stringResource(R.string.hc_answer_style),
                    items = listOf(
                        stringResource(R.string.au3_commsfreeze_answer_buttons) to "buttons",
                        stringResource(R.string.au3_commsfreeze_answer_swipe) to "swipe",
                    ),
                    value = answerStyle,
                    onValueChanged = { viewModel.setAnswerStyle(it) }
                )
                ListPreference(
                    title = stringResource(R.string.hc_sim_1_color),
                    items = listOf(
                        stringResource(R.string.au3_commsfreeze_color_green) to "green", stringResource(R.string.au3_commsfreeze_color_blue) to "blue", stringResource(R.string.au3_commsfreeze_color_orange) to "orange", stringResource(R.string.au3_commsfreeze_color_red) to "red",
                        stringResource(R.string.au3_commsfreeze_color_purple) to "purple", stringResource(R.string.au3_commsfreeze_color_pink) to "pink", stringResource(R.string.au3_commsfreeze_color_teal) to "teal",
                    ),
                    value = sim1Color,
                    onValueChanged = { viewModel.setSim1Color(it) }
                )
                ListPreference(
                    title = stringResource(R.string.hc_sim_2_color),
                    items = listOf(
                        stringResource(R.string.au3_commsfreeze_color_green) to "green", stringResource(R.string.au3_commsfreeze_color_blue) to "blue", stringResource(R.string.au3_commsfreeze_color_orange) to "orange", stringResource(R.string.au3_commsfreeze_color_red) to "red",
                        stringResource(R.string.au3_commsfreeze_color_purple) to "purple", stringResource(R.string.au3_commsfreeze_color_pink) to "pink", stringResource(R.string.au3_commsfreeze_color_teal) to "teal",
                    ),
                    value = sim2Color,
                    onValueChanged = { viewModel.setSim2Color(it) }
                )
                SwitchPreference(
                    title = stringResource(R.string.hc_secure_call_screen),
                    summary = stringResource(R.string.hc_block_screenshots_and_hide_the_call_scre),
                    value = secureCallScreen,
                    onValueChanged = { viewModel.setSecureCallScreen(it) }
                )
            }
        }

        item {
            PreferenceCategory(title = stringResource(R.string.hc_sounds_vibrations)) {
                SwitchPreference(
                    title = stringResource(R.string.hc_dialpad_sounds),
                    summary = stringResource(R.string.hc_play_sound_tones_when_using_the_dialpad),
                    value = dialpadSounds,
                    onValueChanged = { viewModel.setDialpadSounds(it) }
                )
                SwitchPreference(
                    title = stringResource(R.string.hc_dialpad_vibration),
                    summary = stringResource(R.string.hc_vibrate_when_using_the_dialpad),
                    value = dialpadVibration,
                    onValueChanged = { viewModel.setDialpadVibration(it) }
                )
                SwitchPreference(
                    title = stringResource(R.string.hc_hide_dialpad_letters),
                    summary = stringResource(R.string.hc_show_only_digits_on_the_keypad),
                    value = hideLetters,
                    onValueChanged = { viewModel.setHideDialpadLetters(it) }
                )
                SwitchPreference(
                    title = stringResource(R.string.hc_vibrate_on_answer),
                    summary = stringResource(R.string.hc_vibrate_when_an_outgoing_call_is_answere),
                    value = vibrateOnAnswer,
                    onValueChanged = { viewModel.setVibrateOnAnswer(it) }
                )
                SwitchPreference(
                    title = stringResource(R.string.hc_vibrate_on_hangup),
                    summary = stringResource(R.string.hc_vibrate_when_a_call_is_disconnected),
                    value = vibrateOnHangup,
                    onValueChanged = { viewModel.setVibrateOnHangup(it) }
                )
            }
        }

        item {
            PreferenceCategory(title = stringResource(R.string.hc_calling)) {
                SwitchPreference(
                    title = stringResource(R.string.hc_tap_to_call),
                    summary = stringResource(R.string.hc_tapping_a_recent_or_contact_places_a_cal),
                    value = tapToCall,
                    onValueChanged = { viewModel.setTapToCall(it) },
                )
                SwitchPreference(
                    title = stringResource(R.string.hc_confirm_before_calling),
                    summary = stringResource(R.string.hc_show_a_confirmation_sheet_before_placing),
                    value = confirmBeforeCall,
                    onValueChanged = { viewModel.setConfirmBeforeCall(it) },
                )
                SwitchPreference(
                    title = stringResource(R.string.hc_remember_dialpad_digits),
                    summary = stringResource(R.string.hc_keep_the_last_typed_number_when_reopenin),
                    value = rememberDialpad,
                    onValueChanged = { viewModel.setRememberDialpad(it) },
                )
                SwitchPreference(
                    title = stringResource(R.string.hc_open_dialpad_on_launch),
                    summary = stringResource(R.string.hc_show_the_keypad_when_phone_opens),
                    value = autoOpenDialpad,
                    onValueChanged = { viewModel.setAutoOpenDialpad(it) },
                )
                SwitchPreference(
                    title = stringResource(R.string.hc_auto_redial),
                    summary = stringResource(R.string.hc_retry_busy_unanswered_or_rejected_outgoi),
                    value = autoRedial,
                    onValueChanged = { viewModel.setAutoRedial(it) },
                )
                if (autoRedial) {
                    de.mm20.launcher2.ui.component.preferences.SliderPreference(
                        title = stringResource(R.string.au4_settingsnew1_redial_attempts),
                        value = autoRedialAttempts.coerceIn(1, 10),
                        min = 1,
                        max = 10,
                        step = 1,
                        onValueChanged = { viewModel.setAutoRedialAttempts(it) },
                        label = { Text(it.toString()) },
                    )
                    de.mm20.launcher2.ui.component.preferences.SliderPreference(
                        title = stringResource(R.string.au4_settingsnew1_redial_delay),
                        value = autoRedialDelaySec.coerceIn(3, 60),
                        min = 3,
                        max = 60,
                        step = 1,
                        onValueChanged = { viewModel.setAutoRedialDelaySec(it) },
                        label = { Text(stringResource(R.string.au4_settingsnew1_seconds, it)) },
                    )
                }
                SwitchPreference(
                    title = stringResource(R.string.hc_pocket_mode),
                    summary = stringResource(R.string.hc_ignore_taps_on_incoming_calls_when_the_p),
                    value = pocketMode,
                    onValueChanged = { viewModel.setPocketMode(it) },
                )
                SwitchPreference(
                    title = stringResource(R.string.hc_proximity_speaker),
                    summary = stringResource(R.string.hc_switch_to_speaker_when_the_phone_is_away),
                    value = proximitySpeaker,
                    onValueChanged = { viewModel.setProximitySpeaker(it) },
                )
                SwitchPreference(
                    title = stringResource(R.string.hc_show_numbers_in_recents),
                    value = showNumbersInRecents,
                    onValueChanged = { viewModel.setShowNumbersInRecents(it) },
                )
                SwitchPreference(
                    title = stringResource(R.string.hc_missed_call_popup),
                    summary = stringResource(R.string.hc_show_a_callback_sheet_after_a_missed_cal),
                    value = missedCallPopup,
                    onValueChanged = { viewModel.setMissedCallPopup(it) },
                )
                SwitchPreference(
                    title = stringResource(R.string.hc_popup_after_every_call),
                    value = postCallPopup,
                    onValueChanged = { viewModel.setPostCallPopup(it) },
                )
                SwitchPreference(
                    title = stringResource(R.string.hc_in_call_notes),
                    summary = stringResource(R.string.hc_notes_button_and_floating_note_when_you),
                    value = inCallNotes,
                    onValueChanged = { viewModel.setInCallNotes(it) },
                )
                Preference(
                    title = stringResource(R.string.hc_reject_with_sms),
                    summary = if (rejectSmsTemplate.isBlank()) stringResource(R.string.au3_commsfreeze_off) else rejectSmsTemplate,
                    onClick = { showRejectSms = true },
                )
                Preference(
                    title = stringResource(R.string.hc_notes),
                    summary = stringResource(R.string.hc_all_contact_call_notes),
                    onClick = { backStack.add(CallerNotesRoute) },
                )
            }
        }

        item {
            SipSettings()
        }

        item {
            RemotePhonebookSettings()
        }

        item {
            PreferenceCategory(title = stringResource(R.string.hc_privacy)) {
                SwitchPreference(
                    title = stringResource(R.string.hc_lock_phone_app),
                    summary = stringResource(R.string.hc_require_biometrics_or_device_pin_when_op),
                    value = phoneAppLock,
                    onValueChanged = { viewModel.setPhoneAppLock(it) },
                )
                ListPreference(
                    title = stringResource(R.string.hc_biometric_before_placing_a_call),
                    items = listOf(
                        stringResource(R.string.au3_commsfreeze_off) to "none",
                        stringResource(R.string.au3_commsfreeze_protect_all) to "all",
                        stringResource(R.string.au3_commsfreeze_protect_listed) to "listed",
                    ),
                    value = callProtectMode,
                    onValueChanged = { if (it != null) viewModel.setCallProtectMode(it) },
                )
                Preference(
                    title = stringResource(R.string.hc_hidden_contacts_pin),
                    summary = stringResource(R.string.hc_dial_pin_on_the_keypad_4_6_digits),
                    onClick = { showPinDialog = true },
                )
                if (!stealthHiderMenu || hiderUnlocked) {
                    Preference(
                        title = stringResource(R.string.hc_hidden_contacts),
                        summary = stringResource(R.string.hc_numbers_hidden_from_lists_until_unlocked),
                        onClick = { backStack.add(HiddenContactsRoute) },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.hc_hide_from_contacts_tab),
                        value = hideFromContacts,
                        onValueChanged = { viewModel.setHideFromContacts(it) },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.hc_hide_from_recents),
                        value = hideFromRecents,
                        onValueChanged = { viewModel.setHideFromRecents(it) },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.hc_mask_name_on_incoming_calls),
                        value = maskHiddenIncoming,
                        onValueChanged = { viewModel.setMaskHiddenIncoming(it) },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.hc_hide_this_menu_after_pin_is_set),
                        summary = stringResource(R.string.hc_stealth_privacy_hider_items_disappear_un),
                        value = stealthHiderMenu,
                        onValueChanged = { viewModel.setStealthHiderMenu(it) },
                    )
                }
            }
        }

        item {
            PreferenceCategory(title = stringResource(R.string.hc_gestures)) {
                SwitchPreference(
                    title = stringResource(R.string.hc_raise_to_answer),
                    summary = stringResource(R.string.hc_answer_incoming_calls_by_lifting_the_pho),
                    value = raiseToAnswer,
                    onValueChanged = { viewModel.setRaiseToAnswer(it) },
                )
                SwitchPreference(
                    title = stringResource(R.string.hc_flip_to_decline),
                    summary = stringResource(R.string.hc_decline_by_turning_the_phone_face_down),
                    value = flipToDecline,
                    onValueChanged = { viewModel.setFlipToDecline(it) },
                )
                SwitchPreference(
                    title = stringResource(R.string.hc_rain_mode),
                    summary = stringResource(R.string.hc_answer_with_a_left_right_left_right_shak),
                    value = rainMode,
                    onValueChanged = { viewModel.setRainMode(it) },
                )
                SwitchPreference(
                    title = stringResource(R.string.hc_volume_keys_toggle_dnd),
                    summary = stringResource(R.string.hc_up_up_down_down_enable_the_accessibility),
                    value = volumeDnd,
                    onValueChanged = { viewModel.setVolumeDnd(it) },
                )
                SwitchPreference(
                    title = stringResource(R.string.hc_volume_dnd_only_on_lock_screen),
                    enabled = volumeDnd,
                    value = volumeDndLockOnly,
                    onValueChanged = { viewModel.setVolumeDndLockOnly(it) },
                )
                Preference(
                    title = stringResource(R.string.hc_accessibility_service),
                    onClick = {
                        context.tryStartActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                )
                Preference(
                    title = stringResource(R.string.hc_do_not_disturb_access),
                    onClick = {
                        context.tryStartActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                    },
                )
            }
        }

        item {
            PreferenceCategory(title = stringResource(R.string.hc_cellular_network)) {
                ListPreference(
                    title = stringResource(R.string.hc_preferred_mode),
                    items = listOf(
                        stringResource(R.string.au3_commsfreeze_net_auto) to "auto",
                        stringResource(R.string.au3_commsfreeze_net_lte) to "lte",
                        stringResource(R.string.au3_commsfreeze_net_nr_lte) to "nr_lte",
                        stringResource(R.string.au3_commsfreeze_net_nr) to "nr",
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
                    title = stringResource(R.string.hc_control_backend),
                    items = listOf(
                        stringResource(R.string.au3_commsfreeze_backend_auto) to "auto",
                        stringResource(R.string.au3_commsfreeze_backend_shizuku) to "shizuku",
                        stringResource(R.string.au3_commsfreeze_backend_root) to "root",
                    ),
                    value = networkBackend,
                    onValueChanged = { if (it != null) viewModel.setNetworkBackend(it) },
                )
                SwitchPreference(
                    title = stringResource(R.string.hf_comms_lte_screen_off),
                    value = screenOffLte,
                    onValueChanged = { viewModel.setScreenOffLte(it) },
                )
                SwitchPreference(
                    title = stringResource(R.string.hf_comms_lte_battery_saver),
                    value = batterySaverLte,
                    onValueChanged = { viewModel.setBatterySaverLte(it) },
                )
            }
        }

        item {
            PreferenceCategory(title = stringResource(R.string.hc_tools)) {
                Preference(
                    title = stringResource(R.string.hc_fake_call),
                    summary = stringResource(R.string.hc_schedule_a_simulated_incoming_call),
                    onClick = { backStack.add(FakeCallSettingsRoute) },
                )
                Preference(
                    title = stringResource(R.string.hc_contact_groups),
                    summary = stringResource(R.string.hc_system_and_google_contact_groups),
                    onClick = { backStack.add(ContactGroupsRoute) },
                )
                Preference(
                    title = stringResource(R.string.hc_scheduled_sms),
                    onClick = { backStack.add(ScheduledSmsRoute) },
                )
                Preference(
                    title = stringResource(R.string.hc_duplicate_contacts),
                    summary = stringResource(R.string.hc_find_and_clean_contacts_that_share_a_num),
                    onClick = { backStack.add(DuplicateContactsRoute) },
                )
                Preference(
                    title = stringResource(R.string.hc_import_vcard),
                    summary = stringResource(R.string.hc_add_contacts_from_a_vcf_file),
                    onClick = { vcfPicker.launch("text/*") },
                )
            }
        }

        item {
            PreferenceCategory(title = stringResource(R.string.hc_call_recording)) {
                SwitchPreference(
                    title = stringResource(R.string.hc_auto_record_calls),
                    summary = stringResource(R.string.hc_start_recording_when_a_call_becomes_acti),
                    value = autoRecord,
                    onValueChanged = { enabled ->
                        if (enabled) micPermission.launch(Manifest.permission.RECORD_AUDIO)
                        else viewModel.setAutoRecordCalls(false)
                    }
                )
                ListPreference(
                    title = stringResource(R.string.hc_delete_old_recordings),
                    items = listOf(
                        stringResource(R.string.au3_commsfreeze_never) to 0,
                        stringResource(R.string.au3_commsfreeze_after_days, 7) to 7,
                        stringResource(R.string.au3_commsfreeze_after_days, 30) to 30,
                        stringResource(R.string.au3_commsfreeze_after_days, 90) to 90,
                    ),
                    value = recordingAutoDeleteDays,
                    onValueChanged = { viewModel.setRecordingAutoDeleteDays(it) }
                )
                ListPreference(
                    title = stringResource(R.string.hc_recording_backend),
                    items = listOf(
                        stringResource(R.string.au3_commsfreeze_rec_auto) to "auto",
                        stringResource(R.string.au3_commsfreeze_rec_shizuku) to "shizuku",
                        stringResource(R.string.au3_commsfreeze_rec_root) to "root",
                        stringResource(R.string.au3_commsfreeze_rec_mic) to "unprivileged",
                    ),
                    value = recordingBackend,
                    onValueChanged = { if (it != null) viewModel.setRecordingBackend(it) },
                )
                ListPreference(
                    title = stringResource(R.string.hc_recording_quality),
                    items = listOf(
                        stringResource(R.string.au3_commsfreeze_quality_compact) to "COMPACT",
                        stringResource(R.string.au3_commsfreeze_quality_balanced) to "BALANCED",
                        stringResource(R.string.au3_commsfreeze_quality_high) to "HIGH",
                    ),
                    value = recordingQuality,
                    onValueChanged = { if (it != null) viewModel.setRecordingQuality(it) }
                )
                Preference(
                    title = stringResource(R.string.hc_recordings),
                    summary = stringResource(R.string.hc_play_or_delete_saved_call_recordings),
                    onClick = { backStack.add(CallRecordingsRoute) },
                )
            }
        }

        item {
            PreferenceCategory(title = stringResource(R.string.hc_encrypted_backup)) {
                Preference(
                    title = stringResource(R.string.hc_export_backup),
                    summary = stringResource(R.string.hc_aes_256_gcm_file_of_notes_block_list_and),
                    onClick = { backupCreate.launch("telos-comms-backup.bin") },
                )
                Preference(
                    title = stringResource(R.string.hc_import_backup),
                    summary = stringResource(R.string.hc_restore_an_encrypted_telos_phone_backup),
                    onClick = { backupOpen.launch(arrayOf("application/octet-stream", "text/plain", "*/*")) },
                )
            }
        }

        item {
            PreferenceCategory(title = stringResource(R.string.hc_privacy_spam)) {
                SwitchPreference(
                    title = stringResource(R.string.hc_offline_spam_list),
                    summary = stringResource(R.string.hc_block_numbers_you_have_added_to_the_telo),
                    value = enableSpamBlocking,
                    onValueChanged = { viewModel.setEnableSpamBlocking(it) }
                )
                SwitchPreference(
                    title = stringResource(R.string.hc_block_hidden_numbers),
                    summary = stringResource(R.string.hc_reject_private_restricted_and_unknown_ca),
                    value = blockHidden,
                    onValueChanged = { viewModel.setBlockHiddenNumbers(it) }
                )
                SwitchPreference(
                    title = stringResource(R.string.hc_block_unknown_callers),
                    summary = stringResource(R.string.hc_reject_numbers_that_are_not_in_your_cont),
                    value = blockUnknown,
                    onValueChanged = { viewModel.setBlockUnknownNumbers(it) }
                )
                SwitchPreference(
                    title = stringResource(R.string.hc_block_international),
                    summary = stringResource(R.string.au2_callsec_block_international_summary),
                    value = blockInternational,
                    onValueChanged = { viewModel.setBlockInternational(it) }
                )
                if (blockInternational) {
                    val homeCountry by viewModel.homeCountry.collectAsStateWithLifecycle()
                    val countries = remember {
                        listOf(
                            context.getString(R.string.au2_callsec_home_country_auto) to ""
                        ) + de.mm20.launcher2.comms.telephony.HomeCountry.regions().map { it.second to it.first }
                    }
                    ListPreference(
                        title = stringResource(R.string.au2_callsec_home_country),
                        items = countries,
                        value = homeCountry,
                        onValueChanged = { viewModel.setHomeCountry(it) },
                    )
                }
                SwitchPreference(
                    title = stringResource(R.string.hc_withhold_caller_id_clir),
                    summary = stringResource(R.string.hc_hide_your_number_on_outgoing_calls),
                    value = clirEnabled,
                    onValueChanged = { viewModel.setClirEnabled(it) }
                )
                if (clirEnabled) {
                    var editClir by rememberSaveable { mutableStateOf(false) }
                    ListPreference(
                        title = stringResource(R.string.hc_clir_prefix),
                        items = listOf(
                            stringResource(R.string.au3_commsfreeze_clir_gsm) to "#31#",
                            stringResource(R.string.au3_commsfreeze_clir_us) to "*67",
                            stringResource(R.string.au3_commsfreeze_clir_uk) to "141",
                            stringResource(R.string.au3_commsfreeze_clir_jp) to "1831",
                            stringResource(R.string.au3_commsfreeze_clir_custom) to "custom",
                        ),
                        // an empty prefix is sent as the GSM default #31#
                        value = when (val effective = clirPrefix.ifBlank { "#31#" }) {
                            "#31#", "*67", "141", "1831" -> effective
                            else -> "custom"
                        },
                        onValueChanged = { selected ->
                            when (selected) {
                                null -> {}
                                "custom" -> { editClir = true }
                                else -> viewModel.setClirPrefix(selected)
                            }
                        }
                    )
                    Preference(
                        title = stringResource(R.string.hc_custom_clir_prefix),
                        summary = clirPrefix.ifBlank { "#31#" },
                        onClick = { editClir = true }
                    )
                    if (editClir) {
                        var input by rememberSaveable { mutableStateOf(clirPrefix) }
                        AlertDialog(
                            onDismissRequest = { editClir = false },
                            title = { Text(stringResource(R.string.hc_custom_clir_prefix)) },
                            text = {
                                OutlinedTextField(
                                    value = input,
                                    onValueChange = { input = it },
                                    label = { Text(stringResource(R.string.hc_prefix)) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    viewModel.setClirPrefix(input.trim())
                                    editClir = false
                                }) { Text(stringResource(R.string.hc_save)) }
                            },
                            dismissButton = {
                                TextButton(onClick = { editClir = false }) { Text(stringResource(R.string.hc_cancel)) }
                            }
                        )
                    }
                }
            }
        }

        item {
            PreferenceCategory(title = stringResource(R.string.hc_default_phone_app)) {
                val context = LocalContext.current
                var isDefault by remember {
                    mutableStateOf(de.mm20.launcher2.comms.telephony.TelosDialer.isDefaultDialer(context))
                }
                // the role dialog is a separate screen: re-check when coming back
                androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
                    isDefault = de.mm20.launcher2.comms.telephony.TelosDialer.isDefaultDialer(context)
                    onPauseOrDispose { }
                }
                Preference(
                    title = stringResource(R.string.hc_set_telos_phone_as_default_dialer),
                    summary = stringResource(if (isDefault) R.string.au3_commsfreeze_dialer_is_default else R.string.au3_commsfreeze_dialer_required),
                    onClick = {
                        (context as? android.app.Activity)?.let {
                            de.mm20.launcher2.comms.telephony.TelosDialer.requestDefaultDialer(it)
                        }
                    }
                )
            }
        }

        item {
            PreferenceCategory(title = stringResource(R.string.hc_call_options)) {
                Preference(
                    title = stringResource(R.string.hc_blocked_numbers),
                    summary = stringResource(R.string.hc_manage_numbers_that_are_blocked_from_cal),
                    onClick = {
                        val telecomManager = context.getSystemService<TelecomManager>()
                        telecomManager?.createManageBlockedNumbersIntent()?.let { intent ->
                            context.tryStartActivity(intent)
                        }
                    }
                )
                ListPreference(
                    title = stringResource(R.string.hc_default_call_sim),
                    items = listOf(
                        stringResource(R.string.au3_commsfreeze_sim_ask) to "ask",
                        stringResource(R.string.au3_commsfreeze_sim_last) to "last",
                        stringResource(R.string.au3_commsfreeze_sim_log) to "log",
                        stringResource(R.string.au3_commsfreeze_sim_1) to "sim1",
                        stringResource(R.string.au3_commsfreeze_sim_2) to "sim2"
                    ),
                    value = defaultSim,
                    onValueChanged = { if (it != null) viewModel.setDefaultSim(it) }
                )
            }
        }

        item {
            PreferenceCategory(title = stringResource(R.string.hc_speed_dial_setup_long_press_1_9)) {
                for (digit in 1..9) {
                    val number = speedDials[digit]
                    Preference(
                        title = stringResource(R.string.hc_slot_digit, digit),
                        summary = number ?: stringResource(R.string.au3_commsfreeze_not_assigned),
                        onClick = { showSpeedDialDialogFor = digit }
                    )
                }
            }
        }
    }

    showSpeedDialDialogFor?.let { digit ->
        var inputNumber by rememberSaveable(digit) { mutableStateOf(speedDials[digit] ?: "") }
        AlertDialog(
            onDismissRequest = { showSpeedDialDialogFor = null },
            title = { Text(stringResource(R.string.hc_set_speed_dial_digit, digit)) },
            text = {
                OutlinedTextField(
                    value = inputNumber,
                    onValueChange = { inputNumber = it },
                    label = { Text(stringResource(R.string.hc_phone_number)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setSpeedDial(digit, inputNumber.takeIf { it.isNotBlank() })
                        showSpeedDialDialogFor = null
                    }
                ) {
                    Text(stringResource(R.string.hc_save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.setSpeedDial(digit, null)
                        showSpeedDialDialogFor = null
                    }
                ) {
                    Text(stringResource(R.string.hc_clear))
                }
            }
        )
    }

    if (showExportPassword) {
        AlertDialog(
            onDismissRequest = { showExportPassword = false; pendingExportUri = null },
            title = { Text(stringResource(R.string.hc_backup_password)) },
            text = {
                OutlinedTextField(
                    value = backupPassword,
                    onValueChange = { backupPassword = it },
                    label = { Text(stringResource(R.string.hc_password)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val uri = pendingExportUri
                        val pw = backupPassword
                        showExportPassword = false
                        pendingExportUri = null
                        backupPassword = ""
                        if (uri != null && pw.length >= 4) {
                            viewModel.exportBackup(pw) { payload ->
                                if (payload == null) {
                                    Toast.makeText(context, context.getString(R.string.hc_export_failed), Toast.LENGTH_SHORT).show()
                                } else {
                                    runCatching {
                                        context.contentResolver.openOutputStream(uri)?.use {
                                            it.write(payload.toByteArray())
                                        }
                                    }
                                    Toast.makeText(context, context.getString(R.string.hc_backup_saved), Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    enabled = backupPassword.length >= 4,
                ) { Text(stringResource(R.string.hc_export)) }
            },
            dismissButton = {
                TextButton(onClick = { showExportPassword = false; pendingExportUri = null }) {
                    Text(stringResource(R.string.hc_cancel))
                }
            },
        )
    }

    if (showImportPassword) {
        AlertDialog(
            onDismissRequest = { showImportPassword = false; pendingImportText = null },
            title = { Text(stringResource(R.string.hc_restore_password)) },
            text = {
                OutlinedTextField(
                    value = backupPassword,
                    onValueChange = { backupPassword = it },
                    label = { Text(stringResource(R.string.hc_password)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val payload = pendingImportText
                        val pw = backupPassword
                        showImportPassword = false
                        pendingImportText = null
                        backupPassword = ""
                        if (payload != null) {
                            viewModel.importBackup(payload, pw) { ok ->
                                Toast.makeText(
                                    context,
                                    context.getString(
                                        if (ok) R.string.au3_commsfreeze_backup_restored
                                        else R.string.au3_commsfreeze_backup_failed
                                    ),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    },
                    enabled = backupPassword.isNotEmpty(),
                ) { Text(stringResource(R.string.hc_restore)) }
            },
            dismissButton = {
                TextButton(onClick = { showImportPassword = false; pendingImportText = null }) {
                    Text(stringResource(R.string.hc_cancel))
                }
            },
        )
    }
}
// === TELOS_PENDING_REVIEW_END: comms_settings_engine ===
