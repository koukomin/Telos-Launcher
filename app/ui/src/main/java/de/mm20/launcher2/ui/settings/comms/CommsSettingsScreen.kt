// === TELOS_PENDING_REVIEW_START: comms_settings_engine ===
package de.mm20.launcher2.ui.settings.comms

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
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
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
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
    val autoOpenDialpad by viewModel.autoOpenDialpad.collectAsStateWithLifecycle()
    val rejectSmsTemplate by viewModel.rejectSmsTemplate.collectAsStateWithLifecycle()
    val secureCallScreen by viewModel.secureCallScreen.collectAsStateWithLifecycle()
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
    var showPinDialog by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val backStack = LocalBackStack.current
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.setAutoRecordCalls(true)
    }
    var backupPassword by remember { mutableStateOf("") }
    var pendingExportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var showExportPassword by remember { mutableStateOf(false) }
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
            Toast.makeText(context, "Could not read backup", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(context, "Could not read file", Toast.LENGTH_SHORT).show()
        } else {
            viewModel.importVcf(text) { count ->
                Toast.makeText(context, "Imported $count contacts", Toast.LENGTH_SHORT).show()
            }
        }
    }

    var showSpeedDialDialogFor by remember { mutableStateOf<Int?>(null) }
    var showRejectSms by remember { mutableStateOf(false) }
    if (showPinDialog) {
        var pin by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text("Hidden contacts PIN") },
            text = {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter { ch -> ch.isDigit() }.take(6) },
                    label = { Text("4–6 digits") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (pin.length in 4..6) {
                        viewModel.setPin(pin)
                        showPinDialog = false
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) { Text("Cancel") }
            },
        )
    }
    if (showRejectSms) {
        var input by remember { mutableStateOf(rejectSmsTemplate) }
        AlertDialog(
            onDismissRequest = { showRejectSms = false },
            title = { Text("Reject with SMS") },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("Message") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setRejectSmsTemplate(input.trim())
                    showRejectSms = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRejectSms = false }) { Text("Cancel") }
            },
        )
    }

    PreferenceScreen(title = { Text("Communications Settings") }) {
        item {
            PreferenceCategory(title = "T9 Search Settings") {
                ListPreference(
                    title = "T9 Language Alphabet",
                    items = listOf(
                        "Latin (English)" to "latin",
                        "Greek" to "greek",
                        "Cyrillic" to "cyrillic"
                    ),
                    value = t9Alphabet,
                    onValueChanged = { viewModel.setT9Alphabet(it) }
                )
            }
        }

        item {
            PreferenceCategory(title = "Incoming call") {
                ListPreference(
                    title = "Answer style",
                    items = listOf(
                        "Buttons" to "buttons",
                        "Swipe" to "swipe",
                    ),
                    value = answerStyle,
                    onValueChanged = { viewModel.setAnswerStyle(it) }
                )
                SwitchPreference(
                    title = "Secure call screen",
                    summary = "Block screenshots and hide the call screen in the recent apps overview",
                    value = secureCallScreen,
                    onValueChanged = { viewModel.setSecureCallScreen(it) }
                )
            }
        }

        item {
            PreferenceCategory(title = "Sounds & Vibrations") {
                SwitchPreference(
                    title = "Dialpad Sounds",
                    summary = "Play sound tones when using the dialpad",
                    value = dialpadSounds,
                    onValueChanged = { viewModel.setDialpadSounds(it) }
                )
                SwitchPreference(
                    title = "Dialpad Vibration",
                    summary = "Vibrate when using the dialpad",
                    value = dialpadVibration,
                    onValueChanged = { viewModel.setDialpadVibration(it) }
                )
                SwitchPreference(
                    title = "Hide dialpad letters",
                    summary = "Show only digits on the keypad",
                    value = hideLetters,
                    onValueChanged = { viewModel.setHideDialpadLetters(it) }
                )
                SwitchPreference(
                    title = "Vibrate on Answer",
                    summary = "Vibrate when an outgoing call is answered",
                    value = vibrateOnAnswer,
                    onValueChanged = { viewModel.setVibrateOnAnswer(it) }
                )
                SwitchPreference(
                    title = "Vibrate on Hangup",
                    summary = "Vibrate when a call is disconnected",
                    value = vibrateOnHangup,
                    onValueChanged = { viewModel.setVibrateOnHangup(it) }
                )
            }
        }

        item {
            PreferenceCategory(title = "Calling") {
                SwitchPreference(
                    title = "Tap to call",
                    summary = "Tapping a recent or contact places a call",
                    value = tapToCall,
                    onValueChanged = { viewModel.setTapToCall(it) },
                )
                SwitchPreference(
                    title = "Confirm before calling",
                    summary = "Show a confirmation sheet before placing a call",
                    value = confirmBeforeCall,
                    onValueChanged = { viewModel.setConfirmBeforeCall(it) },
                )
                SwitchPreference(
                    title = "Remember dialpad digits",
                    summary = "Keep the last typed number when reopening the keypad",
                    value = rememberDialpad,
                    onValueChanged = { viewModel.setRememberDialpad(it) },
                )
                SwitchPreference(
                    title = "Open dialpad on launch",
                    summary = "Show the keypad when Phone opens",
                    value = autoOpenDialpad,
                    onValueChanged = { viewModel.setAutoOpenDialpad(it) },
                )
                SwitchPreference(
                    title = "Auto redial",
                    summary = "Retry busy, unanswered, or rejected outgoing calls",
                    value = autoRedial,
                    onValueChanged = { viewModel.setAutoRedial(it) },
                )
                SwitchPreference(
                    title = "Pocket mode",
                    summary = "Ignore taps on incoming calls when the proximity sensor is covered",
                    value = pocketMode,
                    onValueChanged = { viewModel.setPocketMode(it) },
                )
                SwitchPreference(
                    title = "Proximity speaker",
                    summary = "Switch to speaker when the phone is away from your ear",
                    value = proximitySpeaker,
                    onValueChanged = { viewModel.setProximitySpeaker(it) },
                )
                SwitchPreference(
                    title = "Show numbers in recents",
                    value = showNumbersInRecents,
                    onValueChanged = { viewModel.setShowNumbersInRecents(it) },
                )
                SwitchPreference(
                    title = "Missed call popup",
                    summary = "Show a callback sheet after a missed call",
                    value = missedCallPopup,
                    onValueChanged = { viewModel.setMissedCallPopup(it) },
                )
                SwitchPreference(
                    title = "Popup after every call",
                    value = postCallPopup,
                    onValueChanged = { viewModel.setPostCallPopup(it) },
                )
                SwitchPreference(
                    title = "In-call notes",
                    summary = "Notes button and floating note when you leave the call screen",
                    value = inCallNotes,
                    onValueChanged = { viewModel.setInCallNotes(it) },
                )
                Preference(
                    title = "Reject with SMS",
                    summary = if (rejectSmsTemplate.isBlank()) "Off" else rejectSmsTemplate,
                    onClick = { showRejectSms = true },
                )
                Preference(
                    title = "Notes",
                    summary = "All contact call notes",
                    onClick = { backStack.add(CallerNotesRoute) },
                )
            }
        }

        item {
            PreferenceCategory(title = "Privacy") {
                SwitchPreference(
                    title = "Lock Phone app",
                    summary = "Require biometrics or device PIN when opening Phone",
                    value = phoneAppLock,
                    onValueChanged = { viewModel.setPhoneAppLock(it) },
                )
                ListPreference(
                    title = "Biometric before placing a call",
                    items = listOf(
                        "Off" to "none",
                        "Every call" to "all",
                        "Listed contacts only" to "listed",
                    ),
                    value = callProtectMode,
                    onValueChanged = { if (it != null) viewModel.setCallProtectMode(it) },
                )
                Preference(
                    title = "Hidden contacts PIN",
                    summary = "Dial #PIN# on the keypad. 4–6 digits.",
                    onClick = { showPinDialog = true },
                )
                if (!stealthHiderMenu || hiderUnlocked) {
                    Preference(
                        title = "Hidden contacts",
                        summary = "Numbers hidden from lists until unlocked",
                        onClick = { backStack.add(HiddenContactsRoute) },
                    )
                    SwitchPreference(
                        title = "Hide from contacts tab",
                        value = hideFromContacts,
                        onValueChanged = { viewModel.setHideFromContacts(it) },
                    )
                    SwitchPreference(
                        title = "Hide from recents",
                        value = hideFromRecents,
                        onValueChanged = { viewModel.setHideFromRecents(it) },
                    )
                    SwitchPreference(
                        title = "Mask name on incoming calls",
                        value = maskHiddenIncoming,
                        onValueChanged = { viewModel.setMaskHiddenIncoming(it) },
                    )
                    SwitchPreference(
                        title = "Hide this menu after PIN is set",
                        summary = "Stealth: Privacy hider items disappear until you dial #PIN#",
                        value = stealthHiderMenu,
                        onValueChanged = { viewModel.setStealthHiderMenu(it) },
                    )
                }
            }
        }

        item {
            PreferenceCategory(title = "Gestures") {
                SwitchPreference(
                    title = "Raise to answer",
                    summary = "Answer incoming calls by lifting the phone to your ear",
                    value = raiseToAnswer,
                    onValueChanged = { viewModel.setRaiseToAnswer(it) },
                )
                SwitchPreference(
                    title = "Flip to decline",
                    summary = "Decline by turning the phone face down",
                    value = flipToDecline,
                    onValueChanged = { viewModel.setFlipToDecline(it) },
                )
                SwitchPreference(
                    title = "Rain mode",
                    summary = "Answer with a left-right-left-right shake",
                    value = rainMode,
                    onValueChanged = { viewModel.setRainMode(it) },
                )
                SwitchPreference(
                    title = "Volume keys toggle DND",
                    summary = "Up-Up-Down-Down. Enable the accessibility service and DND access.",
                    value = volumeDnd,
                    onValueChanged = { viewModel.setVolumeDnd(it) },
                )
                SwitchPreference(
                    title = "Volume DND only on lock screen",
                    value = volumeDndLockOnly,
                    onValueChanged = { viewModel.setVolumeDndLockOnly(it) },
                )
                Preference(
                    title = "Accessibility service",
                    onClick = {
                        context.tryStartActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                )
                Preference(
                    title = "Do Not Disturb access",
                    onClick = {
                        context.tryStartActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                    },
                )
            }
        }

        item {
            PreferenceCategory(title = "Cellular network") {
                ListPreference(
                    title = "Preferred mode",
                    items = listOf(
                        "System auto" to "auto",
                        "4G / LTE only" to "lte",
                        "5G + 4G" to "nr_lte",
                        "5G only" to "nr",
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
                    title = "Control backend",
                    items = listOf(
                        "Auto (Shizuku → Root)" to "auto",
                        "Shizuku" to "shizuku",
                        "Root" to "root",
                    ),
                    value = networkBackend,
                    onValueChanged = { if (it != null) viewModel.setNetworkBackend(it) },
                )
                SwitchPreference(
                    title = "4G when screen is off",
                    value = screenOffLte,
                    onValueChanged = { viewModel.setScreenOffLte(it) },
                )
                SwitchPreference(
                    title = "4G in Battery Saver",
                    value = batterySaverLte,
                    onValueChanged = { viewModel.setBatterySaverLte(it) },
                )
            }
        }

        item {
            PreferenceCategory(title = "Tools") {
                Preference(
                    title = "Fake call",
                    summary = "Schedule a simulated incoming call",
                    onClick = { backStack.add(FakeCallSettingsRoute) },
                )
                Preference(
                    title = "Contact groups",
                    summary = "System and Google contact groups",
                    onClick = { backStack.add(ContactGroupsRoute) },
                )
                Preference(
                    title = "Scheduled SMS",
                    onClick = { backStack.add(ScheduledSmsRoute) },
                )
                Preference(
                    title = "Duplicate contacts",
                    summary = "Find and clean contacts that share a number",
                    onClick = { backStack.add(DuplicateContactsRoute) },
                )
                Preference(
                    title = "Import vCard",
                    summary = "Add contacts from a .vcf file",
                    onClick = { vcfPicker.launch("text/*") },
                )
            }
        }

        item {
            PreferenceCategory(title = "Call recording") {
                SwitchPreference(
                    title = "Auto-record calls",
                    summary = "Start recording when a call becomes active. Privileged backends capture both sides when the system allows it.",
                    value = autoRecord,
                    onValueChanged = { enabled ->
                        if (enabled) micPermission.launch(Manifest.permission.RECORD_AUDIO)
                        else viewModel.setAutoRecordCalls(false)
                    }
                )
                ListPreference(
                    title = "Delete old recordings",
                    items = listOf(
                        "Never" to 0,
                        "After 7 days" to 7,
                        "After 30 days" to 30,
                        "After 90 days" to 90,
                    ),
                    value = recordingAutoDeleteDays,
                    onValueChanged = { viewModel.setRecordingAutoDeleteDays(it) }
                )
                ListPreference(
                    title = "Recording backend",
                    items = listOf(
                        "Auto (Shizuku → Root → microphone)" to "auto",
                        "Shizuku (fallback to microphone)" to "shizuku",
                        "Root (fallback to microphone)" to "root",
                        "Microphone only" to "unprivileged",
                    ),
                    value = recordingBackend,
                    onValueChanged = { if (it != null) viewModel.setRecordingBackend(it) },
                )
                ListPreference(
                    title = "Recording quality",
                    items = listOf(
                        "Compact (24 kbps)" to "COMPACT",
                        "Balanced (48 kbps)" to "BALANCED",
                        "High (96 kbps)" to "HIGH",
                    ),
                    value = recordingQuality,
                    onValueChanged = { if (it != null) viewModel.setRecordingQuality(it) }
                )
                Preference(
                    title = "Recordings",
                    summary = "Play or delete saved call recordings",
                    onClick = { backStack.add(CallRecordingsRoute) },
                )
            }
        }

        item {
            PreferenceCategory(title = "Encrypted backup") {
                Preference(
                    title = "Export backup",
                    summary = "AES-256-GCM file of notes, block list, and phone settings",
                    onClick = { backupCreate.launch("telos-comms-backup.bin") },
                )
                Preference(
                    title = "Import backup",
                    summary = "Restore an encrypted Telos Phone backup",
                    onClick = { backupOpen.launch(arrayOf("application/octet-stream", "text/plain", "*/*")) },
                )
            }
        }

        item {
            PreferenceCategory(title = "Privacy & Spam") {
                SwitchPreference(
                    title = "Offline spam list",
                    summary = "Block numbers you have added to the Telos block list",
                    value = enableSpamBlocking,
                    onValueChanged = { viewModel.setEnableSpamBlocking(it) }
                )
                SwitchPreference(
                    title = "Block hidden numbers",
                    summary = "Reject private, restricted, and unknown caller ID",
                    value = blockHidden,
                    onValueChanged = { viewModel.setBlockHiddenNumbers(it) }
                )
                SwitchPreference(
                    title = "Block unknown callers",
                    summary = "Reject numbers that are not in your contacts",
                    value = blockUnknown,
                    onValueChanged = { viewModel.setBlockUnknownNumbers(it) }
                )
                SwitchPreference(
                    title = "Block international",
                    summary = "Reject numbers that start with + or 00",
                    value = blockInternational,
                    onValueChanged = { viewModel.setBlockInternational(it) }
                )
                SwitchPreference(
                    title = "Withhold caller ID (CLIR)",
                    summary = "Hide your number on outgoing calls",
                    value = clirEnabled,
                    onValueChanged = { viewModel.setClirEnabled(it) }
                )
                if (clirEnabled) {
                    ListPreference(
                        title = "CLIR prefix",
                        items = listOf(
                            "GSM #31#" to "#31#",
                            "US/Canada *67" to "*67",
                            "UK 141" to "141",
                            "Japan 1831" to "1831",
                            "Custom" to "custom",
                        ),
                        value = when (clirPrefix) {
                            "#31#", "*67", "141", "1831" -> clirPrefix
                            else -> "custom"
                        },
                        onValueChanged = { selected ->
                            when (selected) {
                                null -> {}
                                "custom" -> {}
                                else -> viewModel.setClirPrefix(selected)
                            }
                        }
                    )
                    var editClir by remember { mutableStateOf(false) }
                    Preference(
                        title = "Custom CLIR prefix",
                        summary = if (clirPrefix.isEmpty()) "Not set" else clirPrefix,
                        onClick = { editClir = true }
                    )
                    if (editClir) {
                        var input by remember { mutableStateOf(clirPrefix) }
                        AlertDialog(
                            onDismissRequest = { editClir = false },
                            title = { Text("Custom CLIR prefix") },
                            text = {
                                OutlinedTextField(
                                    value = input,
                                    onValueChange = { input = it },
                                    label = { Text("Prefix") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    viewModel.setClirPrefix(input.trim())
                                    editClir = false
                                }) { Text("Save") }
                            },
                            dismissButton = {
                                TextButton(onClick = { editClir = false }) { Text("Cancel") }
                            }
                        )
                    }
                }
            }
        }

        item {
            PreferenceCategory(title = "Default phone app") {
                val context = LocalContext.current
                val isDefault = remember {
                    de.mm20.launcher2.comms.telephony.TelosDialer.isDefaultDialer(context)
                }
                Preference(
                    title = "Set Telos Phone as default dialer",
                    summary = if (isDefault) "Telos Phone is the default phone app" else "Required for in-call UI, answer, and mute/speaker",
                    onClick = {
                        (context as? android.app.Activity)?.let {
                            de.mm20.launcher2.comms.telephony.TelosDialer.requestDefaultDialer(it)
                        }
                    }
                )
            }
        }

        item {
            PreferenceCategory(title = "Call Options") {
                Preference(
                    title = "Blocked Numbers",
                    summary = "Manage numbers that are blocked from calling or texting you",
                    onClick = {
                        val telecomManager = context.getSystemService<TelecomManager>()
                        telecomManager?.createManageBlockedNumbersIntent()?.let { intent ->
                            context.tryStartActivity(intent)
                        }
                    }
                )
                ListPreference(
                    title = "Default Call SIM",
                    items = listOf(
                        "Always Ask" to "ask",
                        "Last used SIM" to "last",
                        "Same SIM as last call to this number" to "log",
                        "SIM 1" to "sim1",
                        "SIM 2" to "sim2"
                    ),
                    value = defaultSim,
                    onValueChanged = { if (it != null) viewModel.setDefaultSim(it) }
                )
            }
        }

        item {
            PreferenceCategory(title = "Speed Dial Setup (Long-Press 1-9)") {
                for (digit in 1..9) {
                    val number = speedDials[digit]
                    Preference(
                        title = "Slot $digit",
                        summary = number ?: "Not assigned",
                        onClick = { showSpeedDialDialogFor = digit }
                    )
                }
            }
        }
    }

    showSpeedDialDialogFor?.let { digit ->
        var inputNumber by remember { mutableStateOf(speedDials[digit] ?: "") }
        AlertDialog(
            onDismissRequest = { showSpeedDialDialogFor = null },
            title = { Text("Set Speed Dial $digit") },
            text = {
                OutlinedTextField(
                    value = inputNumber,
                    onValueChange = { inputNumber = it },
                    label = { Text("Phone Number") },
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
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.setSpeedDial(digit, null)
                        showSpeedDialDialogFor = null
                    }
                ) {
                    Text("Clear")
                }
            }
        )
    }

    if (showExportPassword) {
        AlertDialog(
            onDismissRequest = { showExportPassword = false; pendingExportUri = null },
            title = { Text("Backup password") },
            text = {
                OutlinedTextField(
                    value = backupPassword,
                    onValueChange = { backupPassword = it },
                    label = { Text("Password") },
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
                                    Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show()
                                } else {
                                    runCatching {
                                        context.contentResolver.openOutputStream(uri)?.use {
                                            it.write(payload.toByteArray())
                                        }
                                    }
                                    Toast.makeText(context, "Backup saved", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    enabled = backupPassword.length >= 4,
                ) { Text("Export") }
            },
            dismissButton = {
                TextButton(onClick = { showExportPassword = false; pendingExportUri = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showImportPassword) {
        AlertDialog(
            onDismissRequest = { showImportPassword = false; pendingImportText = null },
            title = { Text("Restore password") },
            text = {
                OutlinedTextField(
                    value = backupPassword,
                    onValueChange = { backupPassword = it },
                    label = { Text("Password") },
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
                                    if (ok) "Backup restored" else "Wrong password or corrupt file",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    },
                    enabled = backupPassword.isNotEmpty(),
                ) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = { showImportPassword = false; pendingImportText = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}
// === TELOS_PENDING_REVIEW_END: comms_settings_engine ===
