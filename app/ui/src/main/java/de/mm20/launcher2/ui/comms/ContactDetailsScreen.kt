package de.mm20.launcher2.ui.comms

import android.content.Context
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
        val blockedFlow = contactFlow.flatMapLatest { contact ->
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

    fun setStarred(context: Context, contactId: Long, starred: Boolean) {
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
            )
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
            cachedKey = null
            cachedUi = null
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

    val contact = ui.contact
    val blocked = blockedOverride ?: ui.blocked
    val primary = contact?.phoneNumbers?.firstOrNull().orEmpty()
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
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { backStack.removeLastOrNull() }) {
                    Icon(
                        painterResource(R.drawable.arrow_back_24px),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.weight(1f))
                val starred = starOverride ?: (contact?.starred == true)
                IconButton(
                    onClick = {
                        contact?.let {
                            starOverride = !starred
                            viewModel.setStarred(context, it.id, !starred)
                        }
                    },
                    enabled = contact != null,
                ) {
                    Icon(
                        painterResource(
                            if (starred) R.drawable.star_24px_filled else R.drawable.star_24px_outlined
                        ),
                        contentDescription = null,
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
                                text = "Mobile",
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
                                    Text(if (isDefault) "Default number" else "Set as default")
                                }
                            }
                        }
                        IconButton(onClick = { viewModel.dial(context, number) }) {
                            Icon(
                                painterResource(R.drawable.rd_ic_phone_green_vector),
                                contentDescription = null,
                                tint = RdGreenCall,
                            )
                        }
                    }
                }
            }

            if (primary.isNotEmpty()) {
                item {
                    val note = notes[primary]
                    CommsDetailCard(onClick = { editingNote = true }) {
                        Text(
                            text = if (note.isNullOrBlank()) "Add notes" else note,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (note.isNullOrBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                item {
                    val sims = remember { de.mm20.launcher2.comms.telephony.TelosDialer.callCapableSims(context) }
                    if (sims.size >= 2) {
                        CommsDetailCard {
                            Text("Always use this SIM", style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = numberSims[primary] == null,
                                    onClick = { viewModel.setDefaultSim(primary, null) },
                                    label = { Text("Ask") },
                                )
                                sims.forEach { sim ->
                                    FilterChip(
                                        selected = numberSims[primary] == sim.label,
                                        onClick = { viewModel.setDefaultSim(primary, sim.label) },
                                        label = { Text(sim.label.take(8)) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (primary.isNotEmpty()) {
                item {
                    CommsDetailCard {
                        Text(
                            text = stringResource(R.string.comms_integrations),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        MessengerChip(stringResource(R.string.action_whatsapp), R.drawable.rd_ic_whatsapp_vector) {
                            context.tryStartActivity(MessengerIntentUtils.whatsApp(primary))
                        }
                        MessengerChip(stringResource(R.string.action_telegram), R.drawable.rd_ic_telegram_vector) {
                            context.tryStartActivity(MessengerIntentUtils.telegram(primary))
                        }
                        MessengerChip(stringResource(R.string.action_signal), R.drawable.rd_ic_signal_vector) {
                            context.tryStartActivity(MessengerIntentUtils.signal(primary))
                        }
                        MessengerChip(stringResource(R.string.action_viber), R.drawable.rd_ic_viber_vector) {
                            context.tryStartActivity(MessengerIntentUtils.viber(primary))
                        }
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
                    val hidden = de.mm20.launcher2.comms.privacy.HiddenContacts.matches(primary, hiddenNumbers)
                    CommsDetailCard(onClick = { viewModel.setHidden(primary, !hidden) }) {
                        Text(
                            text = if (hidden) "Unhide contact" else "Hide contact",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                item {
                    val guarded = de.mm20.launcher2.comms.privacy.HiddenContacts.matches(primary, protectedNumbers)
                    CommsDetailCard(onClick = { viewModel.setCallProtected(primary, !guarded) }) {
                        Text(
                            text = if (guarded) "Don't require biometric to call" else "Require biometric to call",
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

    if (editingNote && contact != null && primary.isNotEmpty()) {
        var draft by remember(primary) { mutableStateOf(notes[primary].orEmpty()) }
        AlertDialog(
            onDismissRequest = { editingNote = false },
            title = { Text("Notes") },
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
private fun MessengerChip(label: String, icon: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = Color.Unspecified,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
