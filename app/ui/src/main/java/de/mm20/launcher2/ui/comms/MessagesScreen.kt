package de.mm20.launcher2.ui.comms

import de.mm20.launcher2.ui.R
import androidx.compose.ui.res.stringResource
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import de.mm20.launcher2.comms.PhoneNumbers
import de.mm20.launcher2.comms.privacy.HiddenContacts
import de.mm20.launcher2.comms.privacy.PrivacySession
import de.mm20.launcher2.comms.sms.SmsConversation
import de.mm20.launcher2.comms.sms.SmsMessage
import de.mm20.launcher2.comms.sms.SmsRole
import de.mm20.launcher2.comms.sms.SmsStore
import de.mm20.launcher2.comms.sms.SmsThreads
import de.mm20.launcher2.preferences.comms.CommsSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import java.text.DateFormat
import java.util.Date

/**
 * The text and multimedia messages of the phone: the conversations, one conversation with a reply
 * field, and new messages. Conversations with a hidden contact are only listed while the hidden
 * contacts are unlocked. When Telos is not the default SMS app the system keeps the messages, and
 * its own messaging app still shows (and notifies about) every conversation.
 */
@Composable
fun MessagesScreen(
    initialNumber: String = "",
    initialBody: String = "",
    initialAttachments: List<String> = emptyList(),
) {
    val context = LocalContext.current
    val settings: CommsSettings = koinInject()
    val hidden by settings.hiddenNumbers.collectAsStateWithLifecycle(emptyMap())
    val unlocked by PrivacySession.hiderUnlocked.collectAsStateWithLifecycle()

    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted = it[Manifest.permission.READ_SMS] == true }

    if (!granted) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(stringResource(R.string.hc_messages), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.hc_allow_telos_to_read_and_send_text_messag),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            Button(onClick = { launcher.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS)) }) { Text(stringResource(R.string.hc_allow)) }
        }
        return
    }

    var isDefault by remember { mutableStateOf(SmsRole.isDefault(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { isDefault = SmsRole.isDefault(context) }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { isDefault = SmsRole.isDefault(context) }

    var refresh by remember { mutableStateOf(0) }
    var all by remember { mutableStateOf<List<SmsConversation>?>(null) }
    LaunchedEffect(refresh, isDefault) { all = withContext(Dispatchers.IO) { SmsThreads.conversations(context) } }
    var open by remember { mutableStateOf<SmsConversation?>(null) }
    var newMessage by remember { mutableStateOf(false) }
    var body by remember { mutableStateOf(initialBody) }
    var attachments by remember { mutableStateOf(initialAttachments.map { Uri.parse(it) }) }

    // something was shared to Telos Messages: write to that number
    LaunchedEffect(initialNumber, all) {
        val list = all ?: return@LaunchedEffect
        if (initialNumber.isNotBlank() && open == null) {
            open = list.firstOrNull { c -> c.address.split(", ").any { PhoneNumbers.match(it, initialNumber) } }
                ?: SmsConversation(-1, initialNumber, null, "", 0, 0)
        }
    }

    fun isHidden(c: SmsConversation) = c.address.split(", ").any { HiddenContacts.matches(it, hidden) }
    val shown = all?.filter { unlocked || !isHidden(it) }

    val current = open
    if (current != null) {
        BackHandler { open = null; refresh++ }
        ThreadView(
            conversation = current,
            isDefault = isDefault,
            body = body,
            onBody = { body = it },
            attachments = attachments,
            onAttachments = { attachments = it },
            onBack = { open = null; refresh++ },
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        if (!isDefault) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.hc_make_telos_your_sms_app_to_receive_messa),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { runCatching { roleLauncher.launch(SmsRole.requestIntent(context)) } }) { Text(stringResource(R.string.hc_set)) }
                }
            }
        }
        TextButton(onClick = { newMessage = true }, modifier = Modifier.padding(horizontal = 8.dp)) { Text(stringResource(R.string.hc_new_message)) }
        when {
            shown == null -> Box(Modifier.fillMaxSize())
            shown.isEmpty() -> EmptyCommsTab("No conversations", "Your messages appear here.")
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(shown, key = { it.threadId.toString() + it.address }) { c ->
                    ListItem(
                        headlineContent = {
                            Text(c.name ?: c.address, fontWeight = if (c.unread > 0) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        supportingContent = { Text(c.snippet, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        trailingContent = { Text(shortDate(c.date), style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.clickable { open = c },
                    )
                }
            }
        }
    }

    if (newMessage) {
        var number by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newMessage = false },
            title = { Text(stringResource(R.string.hc_new_message)) },
            text = { OutlinedTextField(number, { number = it }, label = { Text(stringResource(R.string.hc_number)) }, singleLine = true) },
            confirmButton = {
                TextButton(enabled = number.isNotBlank(), onClick = {
                    val n = number.trim()
                    newMessage = false
                    open = all?.firstOrNull { c -> c.address.split(", ").any { PhoneNumbers.match(it, n) } }
                        ?: SmsConversation(-1, n, null, "", 0, 0)
                }) { Text(stringResource(R.string.hc_write)) }
            },
            dismissButton = { TextButton(onClick = { newMessage = false }) { Text(stringResource(R.string.hc_cancel)) } },
        )
    }
}

@Composable
private fun ThreadView(
    conversation: SmsConversation,
    isDefault: Boolean,
    body: String,
    onBody: (String) -> Unit,
    attachments: List<Uri>,
    onAttachments: (List<Uri>) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var version by remember { mutableStateOf(0) }
    var messages by remember { mutableStateOf(emptyList<SmsMessage>()) }
    LaunchedEffect(version) {
        withContext(Dispatchers.IO) { SmsStore.markThreadRead(context, conversation.threadId) }
        messages = withContext(Dispatchers.IO) { SmsThreads.messages(context, conversation) }
    }
    var failed by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { picked ->
        if (picked.isNotEmpty()) onAttachments(attachments + picked)
    }
    val addresses = conversation.address.split(", ").filter { it.isNotBlank() }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.hc_back)) }
            Text(conversation.name ?: conversation.address, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(), state = listState,
            verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(12.dp),
        ) {
            items(messages, key = { it.id + it.date }) { m ->
                Box(Modifier.fillMaxWidth(), contentAlignment = if (m.outgoing) Alignment.CenterEnd else Alignment.CenterStart) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (m.outgoing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.widthIn(max = 300.dp),
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            m.attachments.forEach { a ->
                                if (a.mimeType.startsWith("image/") || a.mimeType.startsWith("video/")) {
                                    AsyncImage(
                                        model = a.uri, contentDescription = null, contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                                    )
                                } else Text("[${a.mimeType}]", style = MaterialTheme.typography.labelSmall)
                            }
                            if (m.body.isNotBlank()) Text(m.body)
                            Text(
                                shortDate(m.date) + if (m.failed) " · not sent" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (m.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        if (failed) Text(stringResource(R.string.hc_the_message_could_not_be_sent), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
        if (attachments.isNotEmpty()) {
            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${attachments.size} attached", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                TextButton(onClick = { onAttachments(emptyList()) }) { Text(stringResource(R.string.hc_remove)) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (isDefault) TextButton(onClick = { picker.launch("image/*") }) { Text("+") }
            OutlinedTextField(body, onBody, modifier = Modifier.weight(1f), placeholder = { Text(stringResource(R.string.hc_message)) })
            TextButton(
                enabled = body.isNotBlank() || attachments.isNotEmpty(),
                onClick = {
                    val asMms = attachments.isNotEmpty() || addresses.size > 1
                    val ok = if (asMms) SmsThreads.sendMms(context, addresses, body, attachments)
                    else SmsThreads.send(context, addresses.firstOrNull().orEmpty(), body)
                    failed = !ok
                    if (ok) { onBody(""); onAttachments(emptyList()); version++ }
                },
            ) { Text(stringResource(R.string.hc_send)) }
        }
    }
}

private fun shortDate(ms: Long): String =
    if (ms <= 0) "" else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(ms))
