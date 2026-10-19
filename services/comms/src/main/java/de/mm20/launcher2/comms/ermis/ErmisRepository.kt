package de.mm20.launcher2.comms.ermis

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Properties
import javax.mail.*
import javax.mail.internet.*
import javax.mail.util.ByteArrayDataSource
import javax.activation.DataHandler

@Serializable
private data class StoredMail(val accounts: List<ErmisAccount> = emptyList(), val passwords: Map<String,String> = emptyMap(),
    val messages: List<ErmisMessage> = emptyList(), val uidValidity: Map<String,Long> = emptyMap())

class ErmisRepository private constructor(private val context: Context) {
    private val mutable = MutableStateFlow(ErmisState(loading = true))
    val state: StateFlow<ErmisState> = mutable.asStateFlow()
    private val mutex = Mutex()
    private val ready = CompletableDeferred<Unit>()
    private val vault = ErmisVault(context)
    private val json = Json { ignoreUnknownKeys = true }
    private var passwords = emptyMap<String,String>()
    private var uidValidity = emptyMap<String,Long>()
    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                vault.read()?.let { json.decodeFromString<StoredMail>(it) }?.let {
                    passwords = it.passwords
                    uidValidity = it.uidValidity
                    mutable.value = ErmisState(it.accounts, it.messages)
                }
                mutable.update { it.copy(loading = false) }
            } catch (e: Exception) { mutable.update { it.copy(loading = false, error = context.getString(de.mm20.launcher2.comms.R.string.ermis_error_cache)) } }
            finally { ready.complete(Unit) }
        }
    }
    private suspend fun <T> operation(block: () -> T): T = withContext(Dispatchers.IO) {
        ready.await()
        mutex.withLock {
            mutable.update { it.copy(loading = true, error = null) }
            try { runInterruptible { block() } } catch (e: CancellationException) { throw e } catch (e: Exception) {
                mutable.update { it.copy(error = when (e) {
                    is AuthenticationFailedException -> context.getString(de.mm20.launcher2.comms.R.string.ermis_error_auth)
                    is javax.net.ssl.SSLException -> context.getString(de.mm20.launcher2.comms.R.string.ermis_error_tls)
                    else -> context.getString(de.mm20.launcher2.comms.R.string.ermis_error_generic)
                }) }; throw e
            } finally { mutable.update { it.copy(loading = false) } }
        }
    }
    private fun persist() = vault.write(json.encodeToString(StoredMail(state.value.accounts, passwords, state.value.messages.take(1000).map { it.copy(body=it.body.take(32_000)) }, uidValidity)))
    private fun account(id: String) = state.value.accounts.first { it.id == id }
    private fun session(a: ErmisAccount? = null): Session = Session.getInstance(Properties().apply {
        if (a?.googleAccount != null) {
            for (protocol in listOf("imaps", "smtp", "smtps")) {
                setProperty("mail.$protocol.auth.mechanisms", "XOAUTH2")
                setProperty("mail.$protocol.auth.login.disable", "true")
                setProperty("mail.$protocol.auth.plain.disable", "true")
            }
        }
        for (protocol in listOf("imaps", "smtp", "smtps")) {
            setProperty("mail.$protocol.connectiontimeout", "15000")
            setProperty("mail.$protocol.timeout", "20000")
            setProperty("mail.$protocol.writetimeout", "20000")
            setProperty("mail.$protocol.ssl.checkserveridentity", "true")
            setProperty("mail.$protocol.ssl.protocols", if (android.os.Build.VERSION.SDK_INT >= 29) "TLSv1.2 TLSv1.3" else "TLSv1.2")
        }
        setProperty("mail.smtp.starttls.enable", "true")
        setProperty("mail.smtp.starttls.required", "true")
        setProperty("mail.smtp.auth", "true")
        setProperty("mail.smtps.auth", "true")
        setProperty("mail.imaps.peek", "true")
    })
    private fun secret(a: ErmisAccount): String = a.googleAccount?.let { ErmisGoogleAuth.token(context, it) } ?: passwords.getValue(a.id)
    private fun store(a: ErmisAccount, password: String = secret(a)): Store {
        val result = session(a).getStore("imaps")
        try { result.connect(a.imapHost, a.imapPort, a.username, password); return result }
        catch (e: Exception) {
            if (a.googleAccount != null && e is AuthenticationFailedException) runCatching { ErmisGoogleAuth.invalidate(context, password) }
            runCatching { result.close() }; throw e
        }
    }
    private fun transport(a: ErmisAccount, password: String = secret(a)): Transport {
        val result = session(a).getTransport(if (a.smtpStartTls) "smtp" else "smtps")
        try { result.connect(a.smtpHost, a.smtpPort, a.username, password); return result }
        catch (e: Exception) {
            if (a.googleAccount != null && e is AuthenticationFailedException) runCatching { ErmisGoogleAuth.invalidate(context, password) }
            runCatching { result.close() }; throw e
        }
    }
    private fun <T> inbox(a: ErmisAccount, block: (Folder, UIDFolder) -> T): T {
        val store = store(a)
        try {
            val folder = store.getFolder("INBOX")
            folder.open(Folder.READ_WRITE)
            try { return block(folder, folder as UIDFolder) } finally { folder.close(false) }
        } finally { store.close() }
    }
    suspend fun addAccount(account: ErmisAccount, password: String) = operation {
        ErmisValidation.validate(account); require(password.isNotBlank())
        require(state.value.accounts.size < 20 || state.value.accounts.any { it.id == account.id }) { "Account limit reached" }
        store(account, password).close()
        transport(account, password).close()
        passwords = if (account.googleAccount == null) passwords + (account.id to password) else passwords - account.id
        mutable.update { it.copy(accounts = it.accounts.filterNot { a -> a.id == account.id } + account) }
        persist(); ErmisWorker.schedule(context)
    }
    suspend fun removeAccount(id: String) = operation {
        passwords = passwords - id
        uidValidity = uidValidity - id
        mutable.update { it.copy(accounts = it.accounts.filterNot { a -> a.id == id }, messages = it.messages.filterNot { m -> m.accountId == id }) }
        persist()
        if (state.value.accounts.isEmpty()) ErmisWorker.cancel(context)
    }
    suspend fun sync(accountId: String? = null) = operation {
        for (a in state.value.accounts.filter { accountId == null || it.id == accountId }) {
            inbox(a) { folder, uids ->
                if (uidValidity[a.id] != null && uidValidity[a.id] != uids.getUIDValidity()) {
                    mutable.update { it.copy(messages = it.messages.filterNot { m -> m.accountId == a.id }) }
                }
                uidValidity = uidValidity + (a.id to uids.getUIDValidity())
                val count = folder.messageCount
                val incoming = if (count == 0) emptyList() else folder.getMessages(maxOf(1,count - 149), count).map { m ->
                    val uid = uids.getUID(m)
                    val previous = state.value.messages.find { it.accountId == a.id && it.uid == uid }
                    ErmisMessage(a.id, uid, ErmisConversations.identifiers(m.getHeader("Message-ID")?.firstOrNull().orEmpty()).firstOrNull().orEmpty(),
                        m.from?.joinToString(", ") { MimeUtility.decodeText(it.toString()) }.orEmpty(), m.subject.orEmpty(),
                        previous?.preview.orEmpty(), previous?.body.orEmpty(), m.sentDate?.time ?: 0L,
                        m.isSet(Flags.Flag.SEEN), m.isSet(Flags.Flag.FLAGGED), previous?.attachments.orEmpty(), previous?.note.orEmpty(),
                        m.getHeader("In-Reply-To")?.firstOrNull().orEmpty(),
                        ErmisConversations.identifiers(m.getHeader("References")?.joinToString(" ").orEmpty()))
                }
                mutable.update { it.copy(messages = (it.messages.filterNot { m -> m.accountId == a.id } + incoming).sortedByDescending { m -> m.date }) }
            }
        }
        persist()
    }
    suspend fun loadMessage(accountId: String, uid: Long): ErmisMessage? = operation {
        inbox(account(accountId)) { _, uids ->
            require(uidValidity[accountId] == uids.getUIDValidity()) { "Mailbox changed; synchronize first" }
            val m = uids.getMessageByUID(uid) ?: return@inbox null
            val result = StringBuilder(); val attachments = mutableListOf<ErmisAttachment>()
            val mime = ErmisMime.read(m)
            result.append(mime.body)
            attachments.addAll(mime.attachments.mapIndexed { index, part -> ErmisAttachment(index, part.fileName?.let { MimeUtility.decodeText(it) } ?: "attachment", part.contentType.substringBefore(';'), part.size) })
            m.setFlag(Flags.Flag.SEEN, true)
            val old = state.value.messages.firstOrNull { it.accountId == accountId && it.uid == uid } ?: return@inbox null
            val updated = old.copy(body = result.toString(), preview = result.toString().take(180), attachments = attachments, read = true)
            val remoteNote = fetchNote(account(accountId), updated.messageId)
            val finalMessage = if (remoteNote != null) updated.copy(note=remoteNote) else updated
            replace(finalMessage); persist(); finalMessage
        }
    }
    private fun replace(message: ErmisMessage) { mutable.update { it.copy(messages = it.messages.map { m -> if (m.accountId == message.accountId && m.uid == message.uid) message else m }) } }
    suspend fun read(accountId: String, uid: Long) = operation { flag(accountId,uid,Flags.Flag.SEEN,true) }
    suspend fun star(accountId: String, uid: Long, starred: Boolean) = operation { flag(accountId,uid,Flags.Flag.FLAGGED,starred) }
    private fun flag(id: String, uid: Long, flag: Flags.Flag, value: Boolean) {
        inbox(account(id)) { _, uids ->
            require(uidValidity[id] == uids.getUIDValidity()) { "Mailbox changed; synchronize first" }
            uids.getMessageByUID(uid)?.setFlag(flag, value)
        }
        state.value.messages.find { it.accountId == id && it.uid == uid }?.let { replace(if (flag == Flags.Flag.SEEN) it.copy(read=value) else it.copy(starred=value)) }
        persist()
    }
    suspend fun send(draft: ErmisDraft) = operation {
        val a = account(draft.accountId)
        val recipients = InternetAddress.parse(draft.to, true).also { require(it.isNotEmpty()); it.forEach { address -> address.validate() } }
        require(!draft.subject.contains('\r') && !draft.subject.contains('\n'))
        val message = MimeMessage(session()).apply {
            setFrom(InternetAddress(a.email, a.name)); setRecipients(Message.RecipientType.TO, recipients)
            setSubject(draft.subject, "UTF-8"); sentDate = java.util.Date()
            draft.references.filter { !it.contains('\r') && !it.contains('\n') }.takeLast(100).takeIf { it.isNotEmpty() }?.let { setHeader("References", it.joinToString(" ")) }
            draft.inReplyTo?.takeIf { !it.contains('\r') && !it.contains('\n') }?.let { setHeader("In-Reply-To",it) }
            val multipart = MimeMultipart()
            multipart.addBodyPart(MimeBodyPart().apply { setText(draft.body,"UTF-8") })
            var total = 0
            for (uri in draft.attachments) {
                val bytes = context.contentResolver.openInputStream(uri)?.use { bounded(it, 15_000_000) } ?: error("Attachment unavailable")
                total += bytes.size; require(total <= 20_000_000) { "Attachments exceed 20 MB" }
                val name = context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { if(it.moveToFirst()) it.getString(0) else null } ?: "attachment"
                multipart.addBodyPart(MimeBodyPart().apply { dataHandler = DataHandler(ByteArrayDataSource(bytes,context.contentResolver.getType(uri) ?: "application/octet-stream")); fileName = MimeUtility.encodeText(name) })
            }
            setContent(multipart); saveChanges()
        }
        val transport = transport(a)
        try { transport.sendMessage(message,recipients) } finally { transport.close() }
        // Gmail stores its own sent copy. Other accounts receive an IMAP Sent copy after SMTP succeeds.
        if (!a.imapHost.equals("imap.gmail.com", true)) {
            try {
                val sentStore = store(a)
                try {
                    val sent = sentStore.getFolder("Sent")
                    if (!sent.exists()) sent.create(Folder.HOLDS_MESSAGES)
                    sent.appendMessages(arrayOf(message))
                } finally { sentStore.close() }
            } catch (_: Exception) {
                mutable.update { it.copy(error=context.getString(de.mm20.launcher2.comms.R.string.ermis_error_sent_copy)) }
            }
        }
        // Sending is explicit; never silently resend after a connection failure.
    }
    suspend fun saveNote(accountId: String, uid: Long, text: String) = operation {
        require(text.length <= 50_000)
        val old = state.value.messages.first { it.accountId == accountId && it.uid == uid }
        replace(old.copy(note=text)); persist()
        val a = account(accountId)
        if (a.imapHost.equals("imap.gmail.com", true) && old.messageId.isNotBlank()) {
            val store = store(a)
            try {
                val notes = store.getFolder("Ermis-Notes")
                if (!notes.exists()) notes.create(Folder.HOLDS_MESSAGES)
                notes.open(Folder.READ_WRITE)
                try {
                    val existing = notes.search(javax.mail.search.HeaderTerm("X-Ermis-Message-ID", old.messageId)).filter { it.getHeader("X-Ermis-Message-ID")?.firstOrNull() == old.messageId }
                    val note = MimeMessage(session()).apply {
                        setFrom(InternetAddress(a.email)); setSubject("Ermis note", "UTF-8")
                        setHeader("X-Ermis-Message-ID",old.messageId); setText(text,"UTF-8"); saveChanges()
                    }
                    notes.appendMessages(arrayOf(note))
                    existing.forEach { it.setFlag(Flags.Flag.DELETED,true) }
                } finally { notes.close(true) }
            } finally { store.close() }
        }
    }
    private fun fetchNote(a: ErmisAccount, messageId: String): String? {
        if (!a.imapHost.equals("imap.gmail.com", true) || messageId.isBlank()) return null
        val store = store(a)
        try {
            val notes = store.getFolder("Ermis-Notes")
            if (!notes.exists()) return null
            notes.open(Folder.READ_ONLY)
            try {
                val found = notes.search(javax.mail.search.HeaderTerm("X-Ermis-Message-ID",messageId))
                    .filter { it.getHeader("X-Ermis-Message-ID")?.firstOrNull() == messageId && !it.isSet(Flags.Flag.DELETED) }
                    .lastOrNull() ?: return null
                return String(found.inputStream.use { bounded(it,200_000) },Charsets.UTF_8)
            } finally { notes.close(false) }
        } finally { store.close() }
    }
    suspend fun downloadAttachment(accountId: String, uid: Long, index: Int, destination: Uri) = operation {
        inbox(account(accountId)) { _, uids ->
            require(uidValidity[accountId] == uids.getUIDValidity()) { "Mailbox changed; synchronize first" }
            val m = uids.getMessageByUID(uid) ?: error("Message unavailable")
            val parts = ErmisMime.read(m).attachments
            require(index in parts.indices)
            val bytes = parts[index].inputStream.use { bounded(it,15_000_000) }
            context.contentResolver.openOutputStream(destination)?.use { it.write(bytes) } ?: error("Destination unavailable")
        }
    }
    private fun bounded(input: java.io.InputStream, max: Int): ByteArray {
        val out=java.io.ByteArrayOutputStream(); val buffer=ByteArray(8192)
        while(true) { if (Thread.currentThread().isInterrupted) throw InterruptedException(); val n=input.read(buffer); if(n<0) break; require(out.size()+n<=max) { "Mail content exceeds size limit" }; out.write(buffer,0,n) }
        return out.toByteArray()
    }
    companion object {
        @Volatile private var instance: ErmisRepository? = null
        fun get(context: Context): ErmisRepository = instance ?: synchronized(this) { instance ?: ErmisRepository(context.applicationContext).also { instance=it } }
    }
}
