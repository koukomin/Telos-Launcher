package de.mm20.launcher2.ui.comms

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.mm20.launcher2.comms.privacy.HiddenContacts
import de.mm20.launcher2.comms.privacy.PrivacySession
import de.mm20.launcher2.comms.sms.SmsConversation
import de.mm20.launcher2.comms.sms.SmsThreads
import de.mm20.launcher2.preferences.comms.CommsSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import java.text.DateFormat
import java.util.Date

/**
 * The text messages of the phone: the conversations, the messages of one conversation, and a
 * reply field. Conversations with a hidden contact are only listed while the hidden contacts are
 * unlocked. Telos is not the default SMS app: the system keeps the messages, and its own
 * messaging app still shows (and notifies about) every conversation.
 */
@Composable
fun MessagesScreen() {
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
            Text("Messages", style = MaterialTheme.typography.titleMedium)
            Text(
                "Allow Telos to read and send text messages to see and answer them here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            Button(onClick = { launcher.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS)) }) { Text("Allow") }
        }
        return
    }

    var refresh by remember { mutableStateOf(0) }
    var all by remember { mutableStateOf<List<SmsConversation>?>(null) }
    LaunchedEffect(refresh) { all = withContext(Dispatchers.IO) { SmsThreads.conversations(context) } }
    var open by remember { mutableStateOf<SmsConversation?>(null) }

    val shown = all?.filter { unlocked || !HiddenContacts.matches(it.address, hidden) }
    val current = open
    if (current != null) {
        BackHandler { open = null; refresh++ }
        ThreadView(current, onBack = { open = null; refresh++ })
        return
    }
    when {
        shown == null -> Box(Modifier.fillMaxSize())
        shown.isEmpty() -> EmptyCommsTab("No conversations", "Your text messages appear here.")
        else -> LazyColumn(Modifier.fillMaxSize()) {
            items(shown, key = { it.threadId }) { c ->
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

@Composable
private fun ThreadView(conversation: SmsConversation, onBack: () -> Unit) {
    val context = LocalContext.current
    var version by remember { mutableStateOf(0) }
    var messages by remember { mutableStateOf(emptyList<de.mm20.launcher2.comms.sms.SmsMessage>()) }
    LaunchedEffect(version) { messages = withContext(Dispatchers.IO) { SmsThreads.messages(context, conversation) } }
    var text by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex) }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
            TextButton(onClick = onBack) { Text("Back") }
            Text(conversation.name ?: conversation.address, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
            items(messages, key = { it.id + it.date }) { m ->
                Box(Modifier.fillMaxWidth(), contentAlignment = if (m.outgoing) Alignment.CenterEnd else Alignment.CenterStart) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (m.outgoing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.widthIn(max = 300.dp),
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text(m.body)
                            Text(shortDate(m.date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        if (failed) Text("The message could not be sent.", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(text, { text = it }, modifier = Modifier.weight(1f), placeholder = { Text("Message") })
            TextButton(
                enabled = text.isNotBlank(),
                onClick = {
                    val ok = SmsThreads.send(context, conversation.address, text)
                    failed = !ok
                    if (ok) { text = ""; version++ }
                },
            ) { Text("Send") }
        }
    }
}

private fun shortDate(ms: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(ms))
