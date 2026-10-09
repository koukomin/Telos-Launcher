package de.mm20.launcher2.ui.comms

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.produceState
import android.content.Intent
import android.net.Uri
import android.telephony.PhoneNumberUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.comms.PhoneNumbers
import de.mm20.launcher2.comms.intent.MessengerIntentUtils
import de.mm20.launcher2.comms.model.CallLogEntry
import de.mm20.launcher2.comms.model.DialerContact
import de.mm20.launcher2.comms.repository.CallLogRepository
import de.mm20.launcher2.comms.repository.ContactDirectoryRepository
import de.mm20.launcher2.comms.repository.SpamRepository
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.preferences.comms.CommsSettings
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.locals.LocalBackStack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Serializable
data class ContactDetailsRoute(
    val contactId: Long = -1L,
    val phoneNumber: String = "",
) : NavKey

data class ContactDetailsUi(
    val contact: DialerContact?,
    val recents: List<CallLogEntry>,
    val blocked: Boolean,
)

class ContactDetailsViewModel : ViewModel(), KoinComponent {
    private val contactDir: ContactDirectoryRepository by inject()
    private val callLog: CallLogRepository by inject()
    private val spam: SpamRepository by inject()
    private val commsSettings: CommsSettings by inject()

    val sim1Color = commsSettings.sim1Color.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "green")
    val sim2Color = commsSettings.sim2Color.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "blue")

    fun setSpeedDial(digit: Int, number: String) {
        commsSettings.setSpeedDial(digit, number)
    }

    val clirPrefix = commsSettings.clirPrefix.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(),
        "",
    )
    val clirEnabled = commsSettings.clirEnabled.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(),
        false,
    )
    val callerNotes = commsSettings.callerNotes.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(),
        emptyMap(),
    )
    val numberDefaultSim = commsSettings.numberDefaultSim.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(),
        emptyMap(),
    )
    val contactDefaultNumbers = commsSettings.contactDefaultNumbers.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(),
        emptyMap(),
    )
    val hiddenNumbers = commsSettings.hiddenNumbers.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(),
        emptyMap(),
    )
    val protectedCallNumbers = commsSettings.protectedCallNumbers.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(),
        emptyMap(),
    )

    /** Bumped after the block list changed so that the blocked state is read again */
    private val blockedTick = MutableStateFlow(0)

    private var cachedKey: Pair<Long, String>? = null
    private var cachedUi: StateFlow<ContactDetailsUi>? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeDetails(contactId: Long, phoneNumber: String): StateFlow<ContactDetailsUi> {
        val key = contactId to phoneNumber
        cachedUi?.let { if (cachedKey == key) return it }
        cachedKey = key
        val contactFlow = contactDir.observeContacts().map { list ->
            list.find { it.id == contactId }
                ?: list.find { c ->
                    phoneNumber.isNotEmpty() && c.phoneNumbers.any { PhoneNumbers.match(it, phoneNumber) }
                }
                ?: phoneNumber.takeIf { it.isNotEmpty() }?.let {
                    DialerContact(id = -1L, displayName = it, phoneNumbers = listOf(it))
                }
        }.distinctUntilChanged()

        val recentsFlow = contactFlow.flatMapLatest { contact ->
            val numbers = contact?.phoneNumbers.orEmpty()
            if (numbers.isEmpty()) flowOf(emptyList()) else callLog.observeForNumbers(numbers)
        }
        val blockedFlow = combine(contactFlow, blockedTick) { contact, _ -> contact }.flatMapLatest { contact ->
            val number = contact?.phoneNumbers?.firstOrNull().orEmpty()
            if (number.isEmpty()) flowOf(false) else flow { emit(spam.isNumberBlocked(number)) }
        }

        val ui = combine(contactFlow, recentsFlow, blockedFlow) { contact, recents, blocked ->
            ContactDetailsUi(contact, recents, blocked)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ContactDetailsUi(null, emptyList(), false),
        )
        cachedUi = ui
        return ui
    }

    fun dial(context: Context, number: String) {
        if (number.isEmpty()) return
        viewModelScope.launch {
            val simLabel = numberDefaultSim.value[number]
            val handle = de.mm20.launcher2.comms.telephony.TelosDialer.callCapableSims(context)
                .find { it.label == simLabel }?.handle
            de.mm20.launcher2.comms.privacy.CallGuard.place(context, number, handle)
        }
    }

    fun dialSip(context: Context, number: String) {
        if (number.isEmpty()) return
        viewModelScope.launch {
            val ok = de.mm20.launcher2.comms.privacy.CallGuard.placeSip(context, number)
            if (!ok) {
                android.widget.Toast.makeText(
                    context,
                    de.mm20.launcher2.comms.sip.SipDialer.lastFailure
                        .ifBlank { context.getString(R.string.au_phoneb_sip_err_not_connected) },
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    /** [onFailed] runs on the main thread when the contact could not be changed */
    fun setStarred(context: Context, contactId: Long, starred: Boolean, onFailed: () -> Unit) {
        if (contactId < 0) {
            onFailed()
            return
        }
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val values = android.content.ContentValues().apply {
                        put(android.provider.ContactsContract.Contacts.STARRED, if (starred) 1 else 0)
                    }
                    context.contentResolver.update(
                        android.content.ContentUris.withAppendedId(
                            android.provider.ContactsContract.Contacts.CONTENT_URI,
                            contactId,
                        ),
                        values,
                        null,
                        null,
                    ) > 0
                }.getOrDefault(false)
            }
            if (!ok) onFailed()
        }
    }

    fun setHidden(number: String, hidden: Boolean) {
        commsSettings.setHiddenNumber(number, hidden)
    }

    fun setCallProtected(number: String, protected: Boolean) {
        commsSettings.setProtectedCallNumber(number, protected)
    }

    fun setDefaultNumber(contactId: Long, number: String?) {
        commsSettings.setContactDefaultNumber(contactId.toString(), number)
    }

    fun share(context: Context, contact: DialerContact) {
        val vcard = contactVcard(contact.displayName, contact.phoneNumbers, contact.emails)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/x-vcard"
            putExtra(Intent.EXTRA_TEXT, vcard)
            putExtra(Intent.EXTRA_SUBJECT, contact.displayName)
        }
        context.tryStartActivity(Intent.createChooser(intent, null))
    }

    fun setNote(number: String, note: String?) {
        commsSettings.setCallerNote(number, note)
    }

    fun setDefaultSim(number: String, sim: String?) {
        commsSettings.setNumberDefaultSim(number, sim)
    }

    fun deleteHistory(numbers: List<String>) {
        viewModelScope.launch { callLog.deleteForNumbers(numbers) }
    }

    fun setBlocked(number: String, blocked: Boolean, onDone: () -> Unit) {
        viewModelScope.launch {
            spam.setBlocked(number, blocked)
            blockedTick.value += 1
            onDone()
        }
    }
}

@Composable
fun ContactDetailsScreen(contactId: Long, phoneNumber: String = "") {
    val viewModel: ContactDetailsViewModel = viewModel()
    val ui by viewModel.observeDetails(contactId, phoneNumber).collectAsStateWithLifecycle()
    val context = LocalContext.current
    val backStack = LocalBackStack.current
    val notes by viewModel.callerNotes.collectAsStateWithLifecycle()
    val numberSims by viewModel.numberDefaultSim.collectAsStateWithLifecycle()
    val defaultNumbers by viewModel.contactDefaultNumbers.collectAsStateWithLifecycle()
    val hiddenNumbers by viewModel.hiddenNumbers.collectAsStateWithLifecycle()
    val protectedNumbers by viewModel.protectedCallNumbers.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var blockedOverride by remember { mutableStateOf<Boolean?>(null) }
    var editingNote by remember { mutableStateOf(false) }
    var starOverride by remember { mutableStateOf<Boolean?>(null) }
    var showReminder by remember { mutableStateOf(false) }
    var showQr by remember { mutableStateOf(false) }
    var showSpeedDial by remember { mutableStateOf(false) }

    val contact = ui.contact
    val blocked = blockedOverride ?: ui.blocked
    val primary = contact?.phoneNumbers?.firstOrNull().orEmpty()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val ringtoneLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == android.app.Activity.RESULT_OK && data != null) {
            val picked = androidx.core.content.IntentCompat.getParcelableExtra(
                data,
                android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI,
                Uri::class.java,
            )
            contact?.let {
                scope.launch {
                    if (!ContactActions.setRingtone(context, it.id, picked)) {
                        android.widget.Toast.makeText(
                            context, R.string.au_phonea_action_failed, android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            }
        }
    }
    val hasEmail = !contact?.emails.isNullOrEmpty()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { backStack.removeLastOrNull() }) {
                    Icon(
                        painterResource(R.drawable.arrow_back_24px),
                        contentDescription = stringResource(R.string.hc_back),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.weight(1f))
                val starred = starOverride ?: (contact?.starred == true)
                IconButton(
                    onClick = {
                        contact?.let {
                            starOverride = !starred
                            viewModel.setStarred(context, it.id, !starred) {
                                starOverride = null
                                android.widget.Toast.makeText(
                                    context, R.string.au_phonea_action_failed, android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    },
                    enabled = contact != null && contact.id >= 0,
                ) {
                    Icon(
                        painterResource(
                            if (starred) R.drawable.star_24px_filled else R.drawable.star_24px_outlined
                        ),
                        contentDescription = stringResource(R.string.favorites),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(
                    onClick = { contact?.let { viewModel.share(context, it) } },
                    enabled = contact != null,
                ) {
                    Icon(
                        painterResource(R.drawable.share_24px),
                        contentDescription = stringResource(R.string.search_action_share),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                IconButton(
                    onClick = { confirmDelete = true },
                    enabled = contact != null && ui.recents.isNotEmpty(),
                ) {
                    Icon(
                        painterResource(R.drawable.delete_24px),
                        contentDescription = stringResource(R.string.comms_clear_history_confirm),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        },
    ) { padding ->
        if (contact == null) {
            EmptyCommsTab(
                title = stringResource(R.string.contact_details_title),
                message = stringResource(R.string.contacts_empty_msg),
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CommsAvatar(
                        name = contact.displayName,
                        photoUri = contact.photoUri,
                        size = 88.dp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = contact.displayName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (!contact.company.isNullOrBlank()) {
                        Text(
                            text = contact.company.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!contact.jobTitle.isNullOrBlank()) {
                        Text(
                            text = contact.jobTitle.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (contact.birthdayMillis != null) {
                        Text(
                            text = java.text.SimpleDateFormat("d MMMM", java.util.Locale.getDefault())
                                .format(java.util.Date(contact.birthdayMillis!!)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CommsActionCard(
                            icon = R.drawable.rd_ic_messages,
                            label = stringResource(R.string.search_action_message).uppercase(),
                            enabled = primary.isNotEmpty(),
                            onClick = { context.tryStartActivity(MessengerIntentUtils.sms(primary)) },
                            modifier = Modifier.weight(1f),
                        )
                        CommsActionCard(
                            icon = R.drawable.rd_ic_phone_green_vector,
                            label = stringResource(R.string.search_action_call).uppercase(),
                            enabled = primary.isNotEmpty(),
                            onClick = { viewModel.dial(context, primary) },
                            modifier = Modifier.weight(1f),
                        )
                        CommsActionCard(
                            icon = R.drawable.videocam_24px,
                            label = stringResource(R.string.comms_action_video).uppercase(),
                            enabled = primary.isNotEmpty(),
                            onClick = { context.tryStartActivity(MessengerIntentUtils.whatsApp(primary)) },
                            modifier = Modifier.weight(1f),
                        )
                        CommsActionCard(
                            icon = R.drawable.mail_24px,
                            label = stringResource(R.string.search_action_email).uppercase(),
                            enabled = hasEmail,
                            onClick = {
                                contact.emails.firstOrNull()?.let {
                                    context.tryStartActivity(MessengerIntentUtils.email(it))
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            item {
                CommsDetailCard {
                    if (ui.recents.isEmpty()) {
                        Text(
                            text = stringResource(R.string.comms_no_previous_calls),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            fontWeight = FontWeight.Medium,
                        )
                    } else {
                        ui.recents.take(8).forEach { CommsHistoryRow(it) }
                    }
                }
            }

            items(contact.phoneNumbers) { number ->
                val isDefault = defaultNumbers[contact.id.toString()] == number ||
                    (defaultNumbers[contact.id.toString()] == null && number == primary)
                CommsDetailCard(onClick = { viewModel.dial(context, number) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.hc_mobile),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = number,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            if (contact.phoneNumbers.size > 1) {
                                TextButton(onClick = { viewModel.setDefaultNumber(contact.id, number) }) {
                                    Text(stringResource(if (isDefault) R.string.au_phonea_default_number else R.string.au_phonea_set_default))
                                }
                            }
                        }
                        IconButton(onClick = { viewModel.dial(context, number) }) {
                            Icon(
                                painterResource(R.drawable.rd_ic_phone_green_vector),
                                contentDescription = stringResource(R.string.search_action_call),
                                tint = RdGreenCall,
                            )
                        }
                    }
                }
            }

            if (primary.isNotEmpty()) {
                // Messenger apps: chat, plus voice and video calls where the app adds them to the contact
                item {
                    val actions by produceState(initialValue = emptyMap<String, DataAction?>(), contact.id) {
                        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            mapOf(
                                "wa_call" to ContactActions.find(context, contact.id, ContactActions.whatsAppCall),
                                "wa_video" to ContactActions.find(context, contact.id, ContactActions.whatsAppVideo),
                                "tg_call" to ContactActions.find(context, contact.id, ContactActions.telegramCall),
                                "sg_call" to ContactActions.find(context, contact.id, ContactActions.signalCall),
                                "vb_call" to ContactActions.find(context, contact.id, ContactActions.viberCall),
                            )
                        }
                    }
                    fun launcher(action: DataAction?): (() -> Unit)? =
                        if (action == null) null else ({ context.tryStartActivity(action.intent()); Unit })
                    CommsDetailCard {
                        Text(
                            text = stringResource(R.string.comms_integrations),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        MessengerRow(
                            label = stringResource(R.string.action_whatsapp),
                            icon = R.drawable.rd_ic_whatsapp_vector,
                            onChat = { context.tryStartActivity(MessengerIntentUtils.whatsApp(primary)) },
                            onCall = launcher(actions["wa_call"]),
                            onVideo = launcher(actions["wa_video"]),
                        )
                        MessengerRow(
                            label = stringResource(R.string.action_telegram),
                            icon = R.drawable.rd_ic_telegram_vector,
                            onChat = { context.tryStartActivity(MessengerIntentUtils.telegram(primary)) },
                            onCall = launcher(actions["tg_call"]),
                            onVideo = null,
                        )
                        MessengerRow(
                            label = stringResource(R.string.action_signal),
                            icon = R.drawable.rd_ic_signal_vector,
                            onChat = { context.tryStartActivity(MessengerIntentUtils.signal(primary)) },
                            onCall = launcher(actions["sg_call"]),
                            onVideo = null,
                        )
                        MessengerRow(
                            label = stringResource(R.string.action_viber),
                            icon = R.drawable.rd_ic_viber_vector,
                            onChat = { context.tryStartActivity(MessengerIntentUtils.viber(primary)) },
                            onCall = launcher(actions["vb_call"]),
                            onVideo = null,
                        )
                    }
                }
            }

            items(contact.emails) { email ->
                CommsDetailCard(
                    onClick = { context.tryStartActivity(MessengerIntentUtils.email(email)) },
                ) {
                    Text(
                        text = email,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (primary.isNotEmpty()) {
                item {
                    val sims = remember { de.mm20.launcher2.comms.telephony.TelosDialer.callCapableSims(context) }
                    if (sims.size >= 2) {
                        val sim1Key by viewModel.sim1Color.collectAsStateWithLifecycle()
                        val sim2Key by viewModel.sim2Color.collectAsStateWithLifecycle()
                        CommsDetailCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(R.string.hc_always_use_this_sim_for_this_number),
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                sims.take(2).forEachIndexed { index, sim ->
                                    val selected = numberSims[primary] == sim.label
                                    val accent = simAccentColor(if (index == 1) sim2Key else sim1Key)
                                    Box(
                                        modifier = Modifier
                                            .padding(start = 8.dp)
                                            .size(40.dp)
                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                            .background(
                                                if (selected) accent
                                                else MaterialTheme.colorScheme.surfaceContainerHighest
                                            )
                                            .clickable {
                                                viewModel.setDefaultSim(primary, if (selected) null else sim.label)
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = (index + 1).toString(),
                                            fontWeight = FontWeight.Bold,
                                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    val sipSettings: de.mm20.launcher2.preferences.comms.CommsSettings = org.koin.compose.koinInject()
                    val sipEnabled by sipSettings.sipEnabled.collectAsStateWithLifecycle(false)
                    val sipMode by sipSettings.sipOutgoing.collectAsStateWithLifecycle("choose")
                    val sipRegistration by de.mm20.launcher2.comms.sip.SipEngine.registration.collectAsStateWithLifecycle()
                    if (sipEnabled && sipMode != "off" &&
                        sipRegistration == de.mm20.launcher2.comms.sip.SipRegistration.Registered
                    ) {
                        CommsDetailCard(onClick = { viewModel.dialSip(context, primary) }) {
                            Text(
                                text = stringResource(R.string.hc_call_over_sip),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                item {
                    val note = notes[primary]
                    CommsDetailCard(onClick = { editingNote = true }) {
                        Text(
                            text = if (note.isNullOrBlank()) stringResource(R.string.au_phonea_add_notes) else note,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (note.isNullOrBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                item {
                    CommsDetailCard {
                        Text(
                            text = stringResource(R.string.hc_more),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                androidx.compose.material3.AssistChip(
                                    onClick = {
                                        val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                                        clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("number", primary))
                                        android.widget.Toast.makeText(context, context.getString(R.string.hc_copied), android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    label = { Text(stringResource(R.string.hc_copy_number)) },
                                )
                            }
                            item {
                                androidx.compose.material3.AssistChip(
                                    onClick = { showReminder = true },
                                    label = { Text(stringResource(R.string.hc_remind_me)) },
                                )
                            }
                            item {
                                androidx.compose.material3.AssistChip(
                                    onClick = { showQr = true },
                                    label = { Text(stringResource(R.string.hc_qr_code)) },
                                )
                            }
                            item {
                                androidx.compose.material3.AssistChip(
                                    onClick = { showSpeedDial = true },
                                    label = { Text(stringResource(R.string.hc_speed_dial)) },
                                )
                            }
                            if (contact.id >= 0) item {
                                androidx.compose.material3.AssistChip(
                                    onClick = {
                                        ringtoneLauncher.launch(
                                            Intent(android.media.RingtoneManager.ACTION_RINGTONE_PICKER)
                                                .putExtra(
                                                    android.media.RingtoneManager.EXTRA_RINGTONE_TYPE,
                                                    android.media.RingtoneManager.TYPE_RINGTONE,
                                                )
                                                .putExtra(
                                                    android.media.RingtoneManager.EXTRA_RINGTONE_TITLE,
                                                    contact.displayName,
                                                )
                                        )
                                    },
                                    label = { Text(stringResource(R.string.hc_ringtone)) },
                                )
                            }
                        }
                    }
                }
                item {
                    val hidden = de.mm20.launcher2.comms.privacy.HiddenContacts.matches(primary, hiddenNumbers)
                    CommsDetailCard(onClick = { viewModel.setHidden(primary, !hidden) }) {
                        Text(
                            text = stringResource(if (hidden) R.string.au_phonea_unhide_contact else R.string.au_phonea_hide_contact),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                item {
                    val guarded = de.mm20.launcher2.comms.privacy.HiddenContacts.matches(primary, protectedNumbers)
                    CommsDetailCard(onClick = { viewModel.setCallProtected(primary, !guarded) }) {
                        Text(
                            text = stringResource(if (guarded) R.string.au_phonea_unprotect_call else R.string.au_phonea_protect_call),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                item {
                    CommsDetailCard(
                        onClick = {
                            val next = !blocked
                            viewModel.setBlocked(primary, next) { blockedOverride = next }
                        },
                    ) {
                        Text(
                            text = stringResource(
                                if (blocked) R.string.comms_unblock_number else R.string.comms_block_number
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (blocked) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                    }
                }
            }
        }
    }

    if (showReminder && contact != null) {
        AlertDialog(
            onDismissRequest = { showReminder = false },
            title = { Text(stringResource(R.string.hc_remind_me_to_call_back)) },
            text = {
                Column {
                    listOf(5, 15, 30, 60, 180).forEach { minutes ->
                        TextButton(onClick = {
                            de.mm20.launcher2.comms.reminder.CallbackReminder.schedule(
                                context, primary, contact.displayName, minutes,
                            )
                            showReminder = false
                        }) {
                            Text(
                                if (minutes < 60) stringResource(R.string.au_phonea_in_minutes, minutes)
                                else stringResource(R.string.au_phonea_in_hours, minutes / 60)
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showReminder = false }) { Text(stringResource(R.string.hc_cancel)) } },
        )
    }

    if (showQr && contact != null) {
        AlertDialog(
            onDismissRequest = { showQr = false },
            title = { Text(contact.displayName) },
            text = {
                ContactQrImage(
                    payload = contactVcard(contact.displayName, contact.phoneNumbers, contact.emails),
                    modifier = Modifier.size(240.dp),
                )
            },
            confirmButton = { TextButton(onClick = { showQr = false }) { Text(stringResource(R.string.hc_close)) } },
        )
    }

    if (showSpeedDial && contact != null) {
        AlertDialog(
            onDismissRequest = { showSpeedDial = false },
            title = { Text(stringResource(R.string.hc_assign_to_speed_dial)) },
            text = {
                Column {
                    Text(stringResource(R.string.hc_long_press_digit_to_call, contact.displayName))
                    (2..9).chunked(4).forEach { digits ->
                        Row(
                            modifier = Modifier.padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            digits.forEach { digit ->
                                TextButton(
                                    onClick = {
                                        viewModel.setSpeedDial(digit, primary)
                                        showSpeedDial = false
                                    },
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                                    modifier = Modifier.size(48.dp),
                                ) { Text(digit.toString()) }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showSpeedDial = false }) { Text(stringResource(R.string.hc_cancel)) } },
        )
    }

    if (editingNote && contact != null && primary.isNotEmpty()) {
        var draft by remember(primary) { mutableStateOf(notes[primary].orEmpty()) }
        AlertDialog(
            onDismissRequest = { editingNote = false },
            title = { Text(stringResource(R.string.hc_notes)) },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setNote(primary, draft)
                    editingNote = false
                }) { Text(stringResource(R.string.action_done)) }
            },
            dismissButton = {
                TextButton(onClick = { editingNote = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    if (confirmDelete && contact != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.comms_clear_history)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteHistory(contact.phoneNumbers)
                    confirmDelete = false
                }) { Text(stringResource(R.string.comms_clear_history_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun MessengerRow(
    label: String,
    icon: Int,
    onChat: () -> Unit,
    onCall: (() -> Unit)?,
    onVideo: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = Color.Unspecified,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        if (onVideo != null) MessengerButton(R.drawable.videocam_24px, stringResource(R.string.au_phonea_video_call), onVideo)
        if (onCall != null) MessengerButton(R.drawable.call_24px, stringResource(R.string.search_action_call), onCall)
        MessengerButton(R.drawable.rd_ic_messages, stringResource(R.string.search_action_message), onChat)
    }
}

@Composable
private fun MessengerButton(icon: Int, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(start = 8.dp)
            .size(40.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = description,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}
