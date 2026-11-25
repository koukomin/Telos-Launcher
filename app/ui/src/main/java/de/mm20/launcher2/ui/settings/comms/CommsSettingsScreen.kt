package de.mm20.launcher2.ui.settings.comms

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Row
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
import de.mm20.launcher2.ui.component.preferences.TextPreference
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
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
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
    val showRecentsFavorites by viewModel.showRecentsFavorites.collectAsStateWithLifecycle()
    val hideLetters by viewModel.hideDialpadLetters.collectAsStateWithLifecycle()
    val allowDialerLandscape by viewModel.allowDialerLandscape.collectAsStateWithLifecycle()
    val blockHidden by viewModel.blockHiddenNumbers.collectAsStateWithLifecycle()
    val blockUnknown by viewModel.blockUnknownNumbers.collectAsStateWithLifecycle()
    val blockInternational by viewModel.blockInternational.collectAsStateWithLifecycle()
    val blockOutgoing by viewModel.blockOutgoingCalls.collectAsStateWithLifecycle()
    val clirEnabled by viewModel.clirEnabled.collectAsStateWithLifecycle()
    val autoRecord by viewModel.autoRecordCalls.collectAsStateWithLifecycle()
    val lockCallRecordings by viewModel.lockCallRecordings.collectAsStateWithLifecycle()
    val hideCallRecordingsFromMedia by viewModel.hideCallRecordingsFromMedia.collectAsStateWithLifecycle()
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
    val simNames by viewModel.simNames.collectAsStateWithLifecycle()
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
            Toast.makeText(context, context.getString(I18nR.string.hc_could_not_read_backup), Toast.LENGTH_SHORT).show()
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
            Toast.makeText(context, context.getString(I18nR.string.hc_could_not_read_file), Toast.LENGTH_SHORT).show()
        } else {
            viewModel.importVcf(text) { count ->
                Toast.makeText(context, context.getString(I18nR.string.hc_imported_contacts, count), Toast.LENGTH_SHORT).show()
            }
        }
    }

    var showSpeedDialDialogFor by rememberSaveable { mutableStateOf<Int?>(null) }
    var showRejectSms by rememberSaveable { mutableStateOf(false) }
    if (showPinDialog) {
        var pin by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.hc_hidden_contacts_pin))  } },
            text = {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter { ch -> ch.isDigit() }.take(6) },
                    label = { Text(stringResource(I18nR.string.hf_comms_pin_digits)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (pin.length in 4..6) {
                        viewModel.setPin(pin)
                        showPinDialog = false
                    }
                }) { Text(stringResource(I18nR.string.hc_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) { Text(stringResource(I18nR.string.hc_cancel)) }
            },
        )
    }
    if (showRejectSms) {
        var input by rememberSaveable { mutableStateOf(rejectSmsTemplate) }
        AlertDialog(
            onDismissRequest = { showRejectSms = false },
            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.hc_reject_with_sms))  } },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text(stringResource(I18nR.string.hc_message)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setRejectSmsTemplate(input.trim())
                    showRejectSms = false
                }) { Text(stringResource(I18nR.string.hc_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showRejectSms = false }) { Text(stringResource(I18nR.string.hc_cancel)) }
            },
        )
    }

    PreferenceScreen(title = { Text(stringResource(I18nR.string.hc_settings)) }) {
                PhoneCallsCategory(viewModel)

                DialerCategory(viewModel)

                IncomingCallsCategory(viewModel)

                CallRecordingCategory(viewModel)

        item {
            SipSettings()
        }

        item {
            RemotePhonebookSettings()
        }

                PrivacyCategory(viewModel)

                CallQualityCategory(viewModel)

                SmsCategory(viewModel)

                BackupCategory(viewModel)

        item {
            PreferenceCategory(title = stringResource(I18nR.string.comms_messages_settings)) {
                val signature by viewModel.smsSignature.collectAsStateWithLifecycle()
                val unreadTop by viewModel.unreadOnTop.collectAsStateWithLifecycle()
                val autoDays by viewModel.smsAutoDeleteDays.collectAsStateWithLifecycle()
                val blockAlpha by viewModel.blockAlphanumeric.collectAsStateWithLifecycle()
                val popupReply by viewModel.smsPopupReply.collectAsStateWithLifecycle()
                var sigDraft by remember { mutableStateOf(signature) }
                LaunchedEffect(signature) { sigDraft = signature }
                OutlinedTextField(
                    value = sigDraft,
                    onValueChange = { sigDraft = it; viewModel.setSmsSignature(it) },
                    label = { Text(stringResource(I18nR.string.comms_sms_signature)) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
                SwitchPreference(
                    title = stringResource(I18nR.string.comms_unread_on_top),
                    value = unreadTop,
                    onValueChanged = { viewModel.setUnreadOnTop(it) },
                )
                SwitchPreference(
                    title = stringResource(I18nR.string.comms_block_alphanumeric),
                    value = blockAlpha,
                    onValueChanged = { viewModel.setBlockAlphanumeric(it) },
                )
                SwitchPreference(
                    title = stringResource(I18nR.string.comms_popup_reply),
                    value = popupReply,
                    onValueChanged = { viewModel.setSmsPopupReply(it) },
                )
                val e2e by viewModel.smsE2eEnabled.collectAsStateWithLifecycle()
                val blockRegex by viewModel.smsBlockRegex.collectAsStateWithLifecycle()
                var regexDraft by remember { mutableStateOf(blockRegex) }
                LaunchedEffect(blockRegex) { regexDraft = blockRegex }
                OutlinedTextField(
                    value = regexDraft,
                    onValueChange = { regexDraft = it; viewModel.setSmsBlockRegex(it) },
                    label = { Text(stringResource(I18nR.string.comms_block_regex)) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
                SwitchPreference(
                    title = stringResource(I18nR.string.comms_e2e_enable),
                    summary = stringResource(I18nR.string.comms_e2e_enable_summary),
                    value = e2e,
                    onValueChanged = { viewModel.setSmsE2eEnabled(it) },
                )
                var e2ePin by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = e2ePin,
                    onValueChange = { e2ePin = it },
                    label = { Text(stringResource(I18nR.string.comms_e2e_pin)) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    singleLine = true,
                )
                Row(Modifier.padding(horizontal = 16.dp)) {
                    TextButton(onClick = {
                        if (e2ePin.isNotBlank()) de.mm20.launcher2.comms.sms.EncryptedSms.setPin(context, e2ePin)
                    }) { Text(stringResource(I18nR.string.comms_e2e_set_pin)) }
                    TextButton(onClick = {
                        de.mm20.launcher2.comms.sms.EncryptedSms.unlock(context, e2ePin)
                    }) { Text(stringResource(I18nR.string.comms_e2e_unlock)) }
                }
                val e2eExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
                    if (uri == null) return@rememberLauncherForActivityResult
                    val dump = de.mm20.launcher2.comms.sms.EncryptedSms.exportKeys(context)
                    val enc = de.mm20.launcher2.comms.backup.CommsBackupManager.encrypt(dump, e2ePin.ifBlank { "telos" })
                    runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(enc.toByteArray()) } }
                }
                val e2eImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri == null) return@rememberLauncherForActivityResult
                    val text = runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull() ?: return@rememberLauncherForActivityResult
                    val plain = runCatching { de.mm20.launcher2.comms.backup.CommsBackupManager.decrypt(text, e2ePin.ifBlank { "telos" }) }.getOrNull() ?: return@rememberLauncherForActivityResult
                    de.mm20.launcher2.comms.sms.EncryptedSms.importKeys(context, plain)
                }
                Preference(title = stringResource(I18nR.string.comms_e2e_export_keys), onClick = { e2eExport.launch("telos-e2e.txt") })
                Preference(title = stringResource(I18nR.string.comms_e2e_import_keys), onClick = { e2eImport.launch(arrayOf("text/plain", "*/*")) })
                ListPreference(
                    title = stringResource(I18nR.string.comms_sms_auto_delete),
                    value = autoDays,
                    items = listOf(0, 7, 30, 90, 365).map { (if (it == 0) "0" else it.toString()) to it },
                    onValueChanged = { viewModel.setSmsAutoDeleteDays(it) },
                )
                val smsExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
                    if (uri == null) return@rememberLauncherForActivityResult
                    settingsScope.launch {
                        val json = withContext(kotlinx.coroutines.Dispatchers.IO) {
                            de.mm20.launcher2.comms.sms.SmsHistory.exportJson(context)
                        }
                        runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } }
                    }
                }
                val smsImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri == null) return@rememberLauncherForActivityResult
                    settingsScope.launch {
                        val json = runCatching {
                            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        }.getOrNull() ?: return@launch
                        withContext(kotlinx.coroutines.Dispatchers.IO) {
                            de.mm20.launcher2.comms.sms.SmsHistory.importJson(context, json)
                        }
                    }
                }
                Preference(title = stringResource(I18nR.string.comms_export_sms), onClick = { smsExport.launch("telos-sms.json") })
                Preference(title = stringResource(I18nR.string.comms_import_sms), onClick = { smsImport.launch(arrayOf("application/json", "text/*")) })
            }
        }

        item {
            PreferenceCategory(title = stringResource(I18nR.string.hc_call_recording)) {
                SwitchPreference(
                    title = stringResource(I18nR.string.hc_auto_record_calls),
                    summary = stringResource(I18nR.string.hc_start_recording_when_a_call_becomes_acti),
                    value = autoRecord,
                    onValueChanged = { enabled ->
                        if (enabled) micPermission.launch(Manifest.permission.RECORD_AUDIO)
                        else viewModel.setAutoRecordCalls(false)
                    }
                )
                ListPreference(
                    title = stringResource(I18nR.string.hc_delete_old_recordings),
                    items = listOf(
                        stringResource(I18nR.string.au3_commsfreeze_never) to 0,
                        stringResource(I18nR.string.au3_commsfreeze_after_days, 7) to 7,
                        stringResource(I18nR.string.au3_commsfreeze_after_days, 30) to 30,
                        stringResource(I18nR.string.au3_commsfreeze_after_days, 90) to 90,
                    ),
                    value = recordingAutoDeleteDays,
                    onValueChanged = { viewModel.setRecordingAutoDeleteDays(it) }
                )
                ListPreference(
                    title = stringResource(I18nR.string.hc_recording_backend),
                    items = listOf(
                        stringResource(I18nR.string.au3_commsfreeze_rec_auto) to "auto",
                        stringResource(I18nR.string.au3_commsfreeze_rec_shizuku) to "shizuku",
                        stringResource(I18nR.string.au3_commsfreeze_rec_root) to "root",
                        stringResource(I18nR.string.au3_commsfreeze_rec_mic) to "unprivileged",
                    ),
                    value = recordingBackend,
                    onValueChanged = { if (it != null) viewModel.setRecordingBackend(it) },
                )
                ListPreference(
                    title = stringResource(I18nR.string.hc_recording_quality),
                    items = listOf(
                        stringResource(I18nR.string.au3_commsfreeze_quality_compact) to "COMPACT",
                        stringResource(I18nR.string.au3_commsfreeze_quality_balanced) to "BALANCED",
                        stringResource(I18nR.string.au3_commsfreeze_quality_high) to "HIGH",
                    ),
                    value = recordingQuality,
                    onValueChanged = { if (it != null) viewModel.setRecordingQuality(it) }
                )
                SwitchPreference(
                    title = stringResource(I18nR.string.lock_call_recordings),
                    summary = stringResource(I18nR.string.lock_call_recordings_summary),
                    value = lockCallRecordings,
                    onValueChanged = { viewModel.setLockCallRecordings(it) },
                )
                SwitchPreference(
                    title = stringResource(I18nR.string.call_recordings_hide_from_media),
                    summary = stringResource(I18nR.string.call_recordings_hide_from_media_summary),
                    value = hideCallRecordingsFromMedia,
                    onValueChanged = { viewModel.setHideCallRecordingsFromMedia(it) },
                )
                Preference(
                    title = stringResource(I18nR.string.call_recordings_encryption),
                    summary = stringResource(I18nR.string.call_recordings_encryption_summary),
                    onClick = { },
                )
                Preference(
                    title = stringResource(I18nR.string.hc_recordings),
                    summary = stringResource(I18nR.string.hc_play_or_delete_saved_call_recordings),
                    onClick = { backStack.add(CallRecordingsRoute) },
                )
            }
        }

        item {
            PreferenceCategory(title = stringResource(I18nR.string.hc_encrypted_backup)) {
                Preference(
                    title = stringResource(I18nR.string.hc_export_backup),
                    summary = stringResource(I18nR.string.hc_aes_256_gcm_file_of_notes_block_list_and),
                    onClick = { backupCreate.launch("telos-comms-backup.bin") },
                )
                Preference(
                    title = stringResource(I18nR.string.hc_import_backup),
                    summary = stringResource(I18nR.string.hc_restore_an_encrypted_telos_phone_backup),
                    onClick = { backupOpen.launch(arrayOf("application/octet-stream", "text/plain", "*/*")) },
                )
            }
        }

        item {
            PreferenceCategory(title = stringResource(I18nR.string.hc_privacy_spam)) {
                SwitchPreference(
                    title = stringResource(I18nR.string.hc_offline_spam_list),
                    summary = stringResource(I18nR.string.hc_block_numbers_you_have_added_to_the_telo),
                    value = enableSpamBlocking,
                    onValueChanged = { viewModel.setEnableSpamBlocking(it) }
                )
                SwitchPreference(
                    title = stringResource(I18nR.string.hc_block_hidden_numbers),
                    summary = stringResource(I18nR.string.hc_reject_private_restricted_and_unknown_ca),
                    value = blockHidden,
                    onValueChanged = { viewModel.setBlockHiddenNumbers(it) }
                )
                SwitchPreference(
                    title = stringResource(I18nR.string.hc_block_unknown_callers),
                    summary = stringResource(I18nR.string.hc_reject_numbers_that_are_not_in_your_cont),
                    value = blockUnknown,
                    onValueChanged = { viewModel.setBlockUnknownNumbers(it) }
                )
                SwitchPreference(
                    title = stringResource(I18nR.string.call_block_outgoing),
                    summary = stringResource(I18nR.string.call_block_outgoing_sum),
                    value = blockOutgoing,
                    onValueChanged = { viewModel.setBlockOutgoingCalls(it) }
                )
                SwitchPreference(
                    title = stringResource(I18nR.string.hc_block_international),
                    summary = stringResource(I18nR.string.au2_callsec_block_international_summary),
                    value = blockInternational,
                    onValueChanged = { viewModel.setBlockInternational(it) }
                )
                if (blockInternational) {
                    val homeCountry by viewModel.homeCountry.collectAsStateWithLifecycle()
                    val countries = remember {
                        listOf(
                            context.getString(I18nR.string.au2_callsec_home_country_auto) to ""
                        ) + de.mm20.launcher2.comms.telephony.HomeCountry.regions().map { it.second to it.first }
                    }
                    ListPreference(
                        title = stringResource(I18nR.string.au2_callsec_home_country),
                        items = countries,
                        value = homeCountry,
                        onValueChanged = { viewModel.setHomeCountry(it) },
                    )
                }
                SwitchPreference(
                    title = stringResource(I18nR.string.hc_withhold_caller_id_clir),
                    summary = stringResource(I18nR.string.hc_hide_your_number_on_outgoing_calls),
                    value = clirEnabled,
                    onValueChanged = { viewModel.setClirEnabled(it) }
                )
                if (clirEnabled) {
                    var editClir by rememberSaveable { mutableStateOf(false) }
                    ListPreference(
                        title = stringResource(I18nR.string.hc_clir_prefix),
                        items = listOf(
                            stringResource(I18nR.string.au3_commsfreeze_clir_gsm) to "#31#",
                            stringResource(I18nR.string.au3_commsfreeze_clir_us) to "*67",
                            stringResource(I18nR.string.au3_commsfreeze_clir_uk) to "141",
                            stringResource(I18nR.string.au3_commsfreeze_clir_jp) to "1831",
                            stringResource(I18nR.string.au3_commsfreeze_clir_custom) to "custom",
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
                        title = stringResource(I18nR.string.hc_custom_clir_prefix),
                        summary = clirPrefix.ifBlank { "#31#" },
                        onClick = { editClir = true }
                    )
                    if (editClir) {
                        var input by rememberSaveable { mutableStateOf(clirPrefix) }
                        AlertDialog(
                            onDismissRequest = { editClir = false },
                            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.hc_custom_clir_prefix))  } },
                            text = {
                                OutlinedTextField(
                                    value = input,
                                    onValueChange = { input = it },
                                    label = { Text(stringResource(I18nR.string.hc_prefix)) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    viewModel.setClirPrefix(input.trim())
                                    editClir = false
                                }) { Text(stringResource(I18nR.string.hc_save)) }
                            },
                            dismissButton = {
                                TextButton(onClick = { editClir = false }) { Text(stringResource(I18nR.string.hc_cancel)) }
                            }
                        )
                    }
                }
            }
        }

        item {
            PreferenceCategory(title = stringResource(I18nR.string.caller_privacy_title)) {
                if (de.mm20.launcher2.comms.telephony.TelosDialer.canRequestCallScreening(context)) {
                    var screeningHeld by remember { mutableStateOf(false) }
                    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
                        screeningHeld = de.mm20.launcher2.comms.telephony.TelosDialer.isCallScreeningApp(context)
                        onPauseOrDispose { }
                    }
                    Preference(
                        title = stringResource(I18nR.string.caller_privacy_title),
                        summary = stringResource(if (screeningHeld) I18nR.string.freeze_permission_granted else I18nR.string.freeze_permission_not_granted),
                        onClick = { (context as? android.app.Activity)?.let {
                            de.mm20.launcher2.comms.telephony.TelosDialer.requestCallScreening(it)
                        } },
                    )
                }
                Preference(
                    title = stringResource(I18nR.string.net_settings_battery),
                    onClick = {
                        context.startActivity(android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    },
                )
            }
        }

        item {
            PreferenceCategory(title = stringResource(I18nR.string.hc_default_phone_app)) {
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
                    title = stringResource(I18nR.string.hc_set_telos_phone_as_default_dialer),
                    summary = stringResource(if (isDefault) I18nR.string.au3_commsfreeze_dialer_is_default else I18nR.string.au3_commsfreeze_dialer_required),
                    onClick = {
                        (context as? android.app.Activity)?.let {
                            de.mm20.launcher2.comms.telephony.TelosDialer.requestDefaultDialer(it)
                        }
                    }
                )
            }
        }

        item {
            PreferenceCategory(title = stringResource(I18nR.string.hc_call_options)) {
                Preference(
                    title = stringResource(I18nR.string.hc_blocked_numbers),
                    summary = stringResource(I18nR.string.hc_manage_numbers_that_are_blocked_from_cal),
                    onClick = {
                        val telecomManager = context.getSystemService<TelecomManager>()
                        telecomManager?.createManageBlockedNumbersIntent()?.let { intent ->
                            context.tryStartActivity(intent)
                        }
                    }
                )
                ListPreference(
                    title = stringResource(I18nR.string.hc_default_call_sim),
                    items = listOf(
                        stringResource(I18nR.string.au3_commsfreeze_sim_ask) to "ask",
                        stringResource(I18nR.string.au3_commsfreeze_sim_last) to "last",
                        stringResource(I18nR.string.au3_commsfreeze_sim_log) to "log",
                        stringResource(I18nR.string.au3_commsfreeze_sim_1) to "sim1",
                        stringResource(I18nR.string.au3_commsfreeze_sim_2) to "sim2"
                    ),
                    value = defaultSim,
                    onValueChanged = { if (it != null) viewModel.setDefaultSim(it) }
                )
            }
        }

        item {
            PreferenceCategory(title = stringResource(I18nR.string.hc_speed_dial_setup_long_press_1_9)) {
                for (digit in 1..9) {
                    val number = speedDials[digit]
                    Preference(
                        title = stringResource(I18nR.string.hc_slot_digit, digit),
                        summary = number ?: stringResource(I18nR.string.au3_commsfreeze_not_assigned),
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
            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.hc_set_speed_dial_digit, digit))  } },
            text = {
                OutlinedTextField(
                    value = inputNumber,
                    onValueChange = { inputNumber = it },
                    label = { Text(stringResource(I18nR.string.hc_phone_number)) },
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
                    Text(stringResource(I18nR.string.hc_save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.setSpeedDial(digit, null)
                        showSpeedDialDialogFor = null
                    }
                ) {
                    Text(stringResource(I18nR.string.hc_clear))
                }
            }
        )
    }

    if (showExportPassword) {
        AlertDialog(
            onDismissRequest = { showExportPassword = false; pendingExportUri = null },
            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.hc_backup_password))  } },
            text = {
                OutlinedTextField(
                    value = backupPassword,
                    onValueChange = { backupPassword = it },
                    label = { Text(stringResource(I18nR.string.hc_password)) },
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
                                    Toast.makeText(context, context.getString(I18nR.string.hc_export_failed), Toast.LENGTH_SHORT).show()
                                } else {
                                    runCatching {
                                        context.contentResolver.openOutputStream(uri)?.use {
                                            it.write(payload.toByteArray())
                                        }
                                    }
                                    Toast.makeText(context, context.getString(I18nR.string.hc_backup_saved), Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    enabled = backupPassword.length >= 4,
                ) { Text(stringResource(I18nR.string.hc_export)) }
            },
            dismissButton = {
                TextButton(onClick = { showExportPassword = false; pendingExportUri = null }) {
                    Text(stringResource(I18nR.string.hc_cancel))
                }
            },
        )
    }

    if (showImportPassword) {
        AlertDialog(
            onDismissRequest = { showImportPassword = false; pendingImportText = null },
            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.hc_restore_password))  } },
            text = {
                OutlinedTextField(
                    value = backupPassword,
                    onValueChange = { backupPassword = it },
                    label = { Text(stringResource(I18nR.string.hc_password)) },
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
                                        if (ok) I18nR.string.au3_commsfreeze_backup_restored
                                        else I18nR.string.au3_commsfreeze_backup_failed
                                    ),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    },
                    enabled = backupPassword.isNotEmpty(),
                ) { Text(stringResource(I18nR.string.hc_restore)) }
            },
            dismissButton = {
                TextButton(onClick = { showImportPassword = false; pendingImportText = null }) {
                    Text(stringResource(I18nR.string.hc_cancel))
                }
            },
        )
    }
}
