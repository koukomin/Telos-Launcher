package de.mm20.launcher2.ui.ermis

import android.net.Uri
import android.accounts.AccountManager
import androidx.activity.compose.LocalActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.Manifest
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.comms.ermis.*
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.text.DateFormat
import java.util.Date
import java.util.UUID

@Serializable
data object ErmisRoute : NavKey

@Composable
fun ErmisScreen() {
    val context = LocalContext.current
    val repository = remember { ErmisRepository.get(context.applicationContext) }
    val state by repository.state.collectAsState()
    val scope = rememberCoroutineScope()
    var accountId by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var starred by rememberSaveable { mutableStateOf(false) }
    var accounts by rememberSaveable { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var composing by rememberSaveable { mutableStateOf(false) }
    var reply by remember { mutableStateOf<ErmisMessage?>(null) }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var selected by remember { mutableStateOf<ErmisMessage?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    fun task(block: suspend () -> Unit) { scope.launch { try { block(); repository.state.value.error?.let { error = it } } catch (e: CancellationException) { throw e } catch (_: Exception) { error = repository.state.value.error ?: context.getString(R.string.ermis_operation_failed) } } }
    val visible = remember(state.messages, accountId, starred, query) { state.messages.filter { message ->
        (accountId == null || message.accountId == accountId) && (!starred || message.starred) &&
            (query.isBlank() || listOf(message.from, message.subject, message.preview).any { it.contains(query, true) })
    }.sortedByDescending { it.date } }
    val conversations = remember(visible) { ErmisConversations.group(visible) }
    val selectedConversation = remember(state.messages, selected?.accountId, selected?.uid) { ErmisConversations.group(state.messages).firstOrNull { thread -> thread.any { it.accountId == selected?.accountId && it.uid == selected?.uid } }.orEmpty() }
    val dateFormatter = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    PreferenceScreen(title = { Text(stringResource(R.string.ermis_title)) }, topBarActions = {
        IconButton(onClick = { composing = true }, enabled = state.accounts.isNotEmpty()) { Icon(painterResource(R.drawable.edit_24px), stringResource(R.string.ermis_compose)) }
        IconButton(onClick = { accounts = true }) { Icon(painterResource(R.drawable.settings_24px), stringResource(R.string.ermis_accounts)) }
    }) {
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query, { query = it }, label = { Text(stringResource(R.string.ermis_search)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!starred, onClick = { starred = false }, label = { Text(stringResource(R.string.ermis_inbox)) })
                    FilterChip(starred, onClick = { starred = true }, label = { Text(stringResource(R.string.ermis_starred)) })
                    TextButton(onClick = { task { repository.sync(accountId) } }, enabled = !state.loading && state.accounts.isNotEmpty()) { Text(stringResource(R.string.ermis_sync)) }
                }
                if (state.accounts.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(accountId == null, onClick = { accountId = null }, label = { Text(stringResource(R.string.ermis_all_accounts)) })
                        state.accounts.forEach { account -> FilterChip(accountId == account.id, onClick = { accountId = account.id }, label = { Text(account.email) }) }
                    }
                }
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        if (state.accounts.isEmpty()) item {
            ElevatedCard(modifier = Modifier.fillMaxWidth().padding(16.dp), onClick = { adding = true }) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.ermis_add_account), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.ermis_native_intro))
                }
            }
        }
        if (visible.isEmpty() && state.accounts.isNotEmpty() && !state.loading) item { Text(stringResource(R.string.ermis_empty), Modifier.padding(24.dp)) }
        items(conversations, key = { "${it.first().accountId}:${it.first().uid}" }) { conversation ->
            val message = conversation.first()
            de.mm20.launcher2.ui.component.LauncherCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp), shape = MaterialTheme.shapes.medium) {
                Row(Modifier.fillMaxWidth().clickable {
                    selected = message
                    task { val loaded = repository.loadMessage(message.accountId, message.uid) ?: message; if (selected?.accountId == message.accountId && selected?.uid == message.uid) selected = loaded }
                }.padding(14.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) { Text(message.from.firstOrNull()?.uppercase() ?: "@") }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(message.from, fontWeight = if (message.read) FontWeight.Normal else FontWeight.Bold)
                        Text(message.subject.ifBlank { context.getString(R.string.ermis_no_subject) }, style = MaterialTheme.typography.titleMedium, fontWeight = if (message.read) FontWeight.Normal else FontWeight.SemiBold)
                        Text(message.preview, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
                        Text(dateFormatter.format(Date(message.date)), style = MaterialTheme.typography.labelSmall)
                        if (conversation.size > 1) Text(stringResource(R.string.ermis_conversation_count, conversation.size), style = MaterialTheme.typography.labelSmall)
                    }
                    IconToggleButton(checked = message.starred, onCheckedChange = { task { repository.star(message.accountId, message.uid, it) } }) { Icon(painterResource(R.drawable.star_24px), stringResource(R.string.ermis_starred), tint = if (message.starred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline) }
                }
            }
        }
    }
    if (accounts) AlertDialog(onDismissRequest = { accounts = false }, title = { Text(stringResource(R.string.ermis_accounts)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.accounts.forEach { account ->
                Text(account.email, style = MaterialTheme.typography.titleMedium)
                Text("IMAP ${account.imapHost}:${account.imapPort}\nSMTP ${account.smtpHost}:${account.smtpPort}", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { task { repository.removeAccount(account.id); if (accountId == account.id) accountId = null } }) { Text(stringResource(R.string.ermis_remove)) }
            }
            Button(onClick = { adding = true; accounts = false }) { Text(stringResource(R.string.ermis_add_account)) }
        }
    }, confirmButton = { TextButton(onClick = { accounts = false }) { Text(stringResource(R.string.hc_close)) } })
    if (adding) AddAccountDialog(state.loading, onDismiss = { adding = false }, onAuthError = { error = context.getString(R.string.ermis_google_unavailable) }) { account, password -> task { repository.addAccount(account, password); adding = false; if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS); repository.sync(account.id) } }
    if (composing) ComposeDialog(state.accounts, accountId, reply, state.loading, onDismiss = { composing = false; reply = null }) { draft -> task { repository.send(draft); composing = false; reply = null } }
    selected?.let { message -> MessageDialog(message, conversation = selectedConversation, onSelect = { other -> selected = other; task { val loaded = repository.loadMessage(other.accountId, other.uid) ?: other; if (selected?.accountId == other.accountId && selected?.uid == other.uid) selected = loaded } }, onDismiss = { selected = null }, onReply = { reply = message; selected = null; composing = true }, onSaveNote = { task { repository.saveNote(message.accountId, message.uid, it); selected = selected?.copy(note = it) } }, onDownload = { attachment, uri -> task { repository.downloadAttachment(message.accountId, message.uid, attachment.index, uri) } }) }
    error?.let { detail -> AlertDialog(onDismissRequest = { error = null }, title = { Text(stringResource(R.string.ermis_operation_failed)) }, text = { Text(detail, Modifier.verticalScroll(rememberScrollState())) }, confirmButton = { TextButton(onClick = { error = null }) { Text(stringResource(R.string.hc_close)) } }) }
}

@Composable
private fun AddAccountDialog(busy: Boolean, onDismiss: () -> Unit, onAuthError: () -> Unit, onConnect: (ErmisAccount, String) -> Unit) {
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    var authenticating by remember { mutableStateOf(false) }
    val googleChooser = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val email = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
        if (result.resultCode == android.app.Activity.RESULT_OK && email != null && activity != null) scope.launch {
            authenticating = true
            try {
                val token = ErmisGoogleAuth.consent(activity, email)
                onConnect(ErmisAccount(UUID.randomUUID().toString(), email, email, "imap.gmail.com", 993, "smtp.gmail.com", googleAccount = email), token)
            } catch (e: CancellationException) { throw e } catch (_: Exception) { onAuthError() }
            finally { authenticating = false }
        }
    }
    var email by rememberSaveable { mutableStateOf("") }; var password by remember { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }; var imap by rememberSaveable { mutableStateOf("") }; var smtp by rememberSaveable { mutableStateOf("") }
    var imapPort by rememberSaveable { mutableStateOf("993") }; var smtpPort by rememberSaveable { mutableStateOf("465") }; var startTls by rememberSaveable { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.ermis_add_account)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.ermis_tls_help))
            OutlinedButton(enabled = !busy && !authenticating && activity != null, onClick = {
                runCatching { googleChooser.launch(AccountManager.newChooseAccountIntent(null, null, arrayOf("com.google"), null, null, null, null)) }.onFailure { onAuthError() }
            }) { Text(stringResource(R.string.ermis_google_sign_in)) }
            Text(stringResource(R.string.ermis_google_help), style = MaterialTheme.typography.bodySmall)
            if (authenticating) LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedTextField(email, { email = it; if (it.endsWith("@gmail.com", true)) { imap = "imap.gmail.com"; smtp = "smtp.gmail.com" } }, label = { Text(stringResource(R.string.ermis_email)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(username, { username = it }, label = { Text(stringResource(R.string.ermis_username)) }, supportingText = { Text(stringResource(R.string.ermis_username_help)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(password, { password = it }, label = { Text(stringResource(R.string.ermis_password)) }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(imap, { imap = it }, label = { Text(stringResource(R.string.ermis_imap_host)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(imapPort, { imapPort = it.filter(Char::isDigit) }, label = { Text(stringResource(R.string.ermis_imap_port)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(smtp, { smtp = it }, label = { Text(stringResource(R.string.ermis_smtp_host)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(smtpPort, { smtpPort = it.filter(Char::isDigit) }, label = { Text(stringResource(R.string.ermis_smtp_port)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(startTls, { startTls = it; smtpPort = if (it) "587" else "465" }); Text(stringResource(R.string.ermis_starttls), Modifier.weight(1f)) }
        }
    }, confirmButton = { TextButton(enabled = !busy && !authenticating && email.contains('@') && password.isNotEmpty() && imap.isNotBlank() && smtp.isNotBlank() && (imapPort.toIntOrNull() ?: 0) in 1..65535 && (smtpPort.toIntOrNull() ?: 0) in 1..65535, onClick = {
        onConnect(ErmisAccount(UUID.randomUUID().toString(), email.trim(), email.trim(), imap.trim(), imapPort.toInt(), smtp.trim(), smtpPort.toInt(), startTls, username.trim().ifBlank { email.trim() }), password)
    }) { Text(stringResource(R.string.ermis_connect)) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.hc_cancel)) } })
}

@Composable
private fun ComposeDialog(accounts: List<ErmisAccount>, initialAccount: String?, reply: ErmisMessage?, busy: Boolean, onDismiss: () -> Unit, onSend: (ErmisDraft) -> Unit) {
    val context = LocalContext.current
    var account by rememberSaveable { mutableStateOf(reply?.accountId ?: initialAccount ?: accounts.firstOrNull()?.id) }
    var to by rememberSaveable { mutableStateOf(reply?.from.orEmpty()) }; var subject by rememberSaveable { mutableStateOf(reply?.subject?.let { if (it.startsWith("Re:", true)) it else "Re: $it" }.orEmpty()) }; var body by rememberSaveable { mutableStateOf("") }
    var attachments by rememberSaveable(stateSaver = listSaver<List<Uri>, String>(
        save = { uris -> uris.map { it.toString() } }, restore = { uris -> uris.map(Uri::parse) },
    )) { mutableStateOf<List<Uri>>(emptyList()) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> attachments = (attachments + uris).distinct() }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.ermis_compose)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FlowRow { accounts.forEach { a -> FilterChip(account == a.id, onClick = { account = a.id }, label = { Text(a.email) }) } }
            OutlinedTextField(to, { to = it }, label = { Text(stringResource(R.string.ermis_to)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(subject, { subject = it }, label = { Text(stringResource(R.string.ermis_subject)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(body, { body = it }, label = { Text(stringResource(R.string.ermis_body)) }, minLines = 5, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = { picker.launch(arrayOf("*/*")) }) { Icon(painterResource(R.drawable.attach_file_24px), null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.ermis_attach)) }
            Text(stringResource(R.string.ermis_saf_help), style = MaterialTheme.typography.bodySmall)
            attachments.forEach { uri ->
                val name by produceState(uri.lastPathSegment.orEmpty(), uri) { value = withContext(Dispatchers.IO) { runCatching { context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } }.getOrNull() ?: uri.lastPathSegment.orEmpty() } }
                Row(verticalAlignment = Alignment.CenterVertically) { Text(name, Modifier.weight(1f)); IconButton(onClick = { attachments = attachments - uri }) { Icon(painterResource(R.drawable.close_24px), stringResource(R.string.ermis_remove)) } }
            }
        }
    }, confirmButton = { TextButton(enabled = !busy && account != null && to.contains('@'), onClick = { onSend(ErmisDraft(account!!, to.trim(), subject, body, attachments, reply?.messageId, (reply?.references.orEmpty() + listOfNotNull(reply?.messageId)).distinct())) }) { Text(stringResource(R.string.ermis_send)) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.hc_cancel)) } })
}

@Composable
private fun MessageDialog(message: ErmisMessage, conversation: List<ErmisMessage>, onSelect: (ErmisMessage) -> Unit, onDismiss: () -> Unit, onReply: () -> Unit, onSaveNote: (String) -> Unit, onDownload: (ErmisAttachment, Uri) -> Unit) {
    var note by remember(message.accountId, message.uid, message.note) { mutableStateOf(message.note) }
    var attachment by remember { mutableStateOf<ErmisAttachment?>(null) }
    val download = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> if (uri != null) attachment?.let { onDownload(it, uri) } }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(message.subject.ifBlank { stringResource(R.string.ermis_no_subject) }) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (conversation.size > 1) {
                Text(stringResource(R.string.ermis_conversation_count, conversation.size), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    conversation.forEach { other -> FilterChip(selected = other.uid == message.uid, onClick = { onSelect(other) }, label = { Text(other.from) }) }
                }
            }
            Text(message.from, style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onReply) { Text(stringResource(R.string.ermis_reply)) }
            SelectionContainer { Text(message.body.ifBlank { message.preview }) }
            message.attachments.forEach { item -> OutlinedButton(onClick = { attachment = item; download.launch(item.name) }) { Text(item.name) } }
            OutlinedTextField(note, { note = it }, label = { Text(stringResource(R.string.ermis_note)) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            TextButton(onClick = { onSaveNote(note) }) { Text(stringResource(R.string.ermis_note_save)) }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.hc_close)) } })
}
