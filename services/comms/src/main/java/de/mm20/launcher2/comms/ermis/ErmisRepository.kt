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
import javax.mail.*
import javax.mail.internet.*
import javax.mail.util.ByteArrayDataSource
import javax.activation.DataHandler
import com.sun.mail.imap.IMAPFolder
import com.sun.mail.imap.IMAPStore
import javax.mail.search.BodyTerm
import javax.mail.search.FromStringTerm
import javax.mail.search.OrTerm
import javax.mail.search.SubjectTerm

@Serializable
private data class StoredMail(val accounts: List<ErmisAccount> = emptyList(), val passwords: Map<String,String> = emptyMap(),
    val messages: List<ErmisMessage> = emptyList(), val uidValidity: Map<String,Long> = emptyMap(),
    val drafts: List<ErmisSavedDraft> = emptyList(), val folders: List<ErmisFolder> = emptyList())

class ErmisRepository private constructor(private val context: Context) {
    private val mutable = MutableStateFlow(ErmisState(loading = true))
    val state: StateFlow<ErmisState> = mutable.asStateFlow()
    private val mutex = Mutex()
    private val ready = CompletableDeferred<Unit>()
    private val vault = ErmisVault(context)
    private val json = de.mm20.launcher2.serialization.Json.Lenient
    private var passwords = emptyMap<String,String>()
    private var uidValidity = emptyMap<String,Long>()
    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                vault.read()?.let { json.decodeFromString<StoredMail>(it) }?.let {
                    passwords = it.passwords
                    uidValidity = it.uidValidity
                    mutable.value = ErmisState(it.accounts, ErmisCachePolicy.bound(it.messages), drafts = it.drafts, folders = it.folders)
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
                val causes = ErmisMailFailure.causes(e)
                val detail = context.getString(when (ErmisMailFailure.kind(causes)) {
                    ErmisFailureKind.GOOGLE_ACCESS -> de.mm20.launcher2.comms.R.string.ermis_google_unavailable
                    ErmisFailureKind.AUTHENTICATION -> de.mm20.launcher2.comms.R.string.ermis_error_auth
                    ErmisFailureKind.TLS -> de.mm20.launcher2.comms.R.string.ermis_error_tls
                    ErmisFailureKind.OTHER -> de.mm20.launcher2.comms.R.string.ermis_error_generic
                })
                val endpoint = causes.filterIsInstance<ErmisConnectionException>().firstOrNull()?.endpoint
                mutable.update { it.copy(error = if (endpoint == null) detail else "$endpoint\n$detail") }; throw e
            } finally { mutable.update { it.copy(loading = false) } }
        }
    }
    private fun persist(snapshot: ErmisState = state.value) = vault.write(json.encodeToString(StoredMail(snapshot.accounts, passwords, snapshot.messages.take(1000).map { it.copy(body=it.body.take(32_000), html="") }, uidValidity, snapshot.drafts, snapshot.folders)))
    private fun account(id: String) = state.value.accounts.first { it.id == id }
    private fun session(a: ErmisAccount? = null): Session = ErmisMailSession.create(
        oauth = a?.googleAccount != null, tls13 = android.os.Build.VERSION.SDK_INT >= 29)
    private fun secret(a: ErmisAccount): String = a.googleAccount?.let { ErmisGoogleAuth.token(context, it) } ?: passwords.getValue(a.id)
    private fun <T> authenticate(a: ErmisAccount, password: String, connect: (String) -> T): T =
        ErmisAuthRetry.connect(password, a.googleAccount != null,
            invalidate = { ErmisGoogleAuth.invalidate(context, it) }, refresh = { secret(a) }, connect = connect)
    private fun store(a: ErmisAccount, password: String? = null): Store = connection("IMAP ${a.imapHost}:${a.imapPort}") {
        authenticate(a, password ?: secret(a)) { token ->
            val result = ErmisMailSession.store(session(a))
            try { result.connect(a.imapHost, a.imapPort, a.username, token); result }
            catch (e: Exception) { runCatching { result.close() }; throw e }
        }
    }
    private fun transport(a: ErmisAccount, password: String? = null): Transport = connection("SMTP ${a.smtpHost}:${a.smtpPort}") {
        authenticate(a, password ?: secret(a)) { token ->
            val result = ErmisMailSession.transport(session(a), a.smtpStartTls)
            try { result.connect(a.smtpHost, a.smtpPort, a.username, token); result }
            catch (e: Exception) { runCatching { result.close() }; throw e }
        }
    }
    private fun <T> connection(endpoint: String, block: () -> T): T = try { block() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { throw ErmisConnectionException(endpoint, e) }
    private fun <T> mailbox(a: ErmisAccount, name: String, mode: Int = Folder.READ_WRITE, block: (Folder, UIDFolder) -> T): T {
        val store = store(a)
        try {
            val mailbox = store.getFolder(ErmisMailboxPolicy.folder(name))
            require(mailbox.exists() && mailbox.type and Folder.HOLDS_MESSAGES != 0)
            mailbox.open(mode)
            try { return block(mailbox, mailbox as UIDFolder) } finally { mailbox.close(false) }
        } finally { store.close() }
    }
    private fun checkValidity(accountId: String, folder: String, uids: UIDFolder, replaceChanged: Boolean) {
        val key = ErmisMailboxPolicy.validityKey(accountId, folder)
        val current = uids.getUIDValidity()
        val previous = uidValidity[key]
        if (!replaceChanged) require(previous == current) { "Mailbox changed; synchronize first" }
        else if (previous != null && previous != current) {
            mutable.update { state -> state.copy(messages = state.messages.filterNot { ErmisMailboxPolicy.matches(it, accountId, folder) }) }
        }
        uidValidity = uidValidity + (key to current)
    }
    private fun headerAddresses(values: Array<Address>?): List<String> = values.orEmpty().take(100).map { address ->
        (if (address is InternetAddress) address.toUnicodeString() else MimeUtility.decodeText(address.toString()))
            .replace('\r', ' ').replace('\n', ' ').take(16_000)
    }
    private fun summaries(a: ErmisAccount, folder: String, mailbox: Folder, uids: UIDFolder, messages: Array<Message>): List<ErmisMessage> {
        if (messages.isEmpty()) return emptyList()
        mailbox.fetch(messages, FetchProfile().apply {
            add(FetchProfile.Item.ENVELOPE); add(FetchProfile.Item.FLAGS); add(UIDFolder.FetchProfileItem.UID)
            add("Message-ID"); add("In-Reply-To"); add("References")
        })
        return messages.filterNot { it.isExpunged || it.isSet(Flags.Flag.DELETED) }.map { m ->
            val uid = uids.getUID(m)
            require(uid > 0)
            val previous = state.value.messages.find { ErmisMailboxPolicy.matches(it, a.id, folder, uid) }
            ErmisMessage(a.id, uid, ErmisConversations.identifiers(m.getHeader("Message-ID")?.firstOrNull().orEmpty()).firstOrNull().orEmpty(),
                headerAddresses(m.from).joinToString(", ").take(16_000), m.subject.orEmpty().take(16_000),
                previous?.preview.orEmpty(), previous?.body.orEmpty(), m.sentDate?.time ?: 0L,
                m.isSet(Flags.Flag.SEEN), m.isSet(Flags.Flag.FLAGGED), previous?.attachments.orEmpty(), previous?.note.orEmpty(),
                m.getHeader("In-Reply-To")?.firstOrNull().orEmpty(),
                ErmisConversations.identifiers(m.getHeader("References")?.joinToString(" ").orEmpty()), folder,
                headerAddresses(m.getRecipients(Message.RecipientType.TO)), headerAddresses(m.getRecipients(Message.RecipientType.CC)),
                headerAddresses(m.replyTo).joinToString(", ").take(16_000), previous?.html.orEmpty())
        }
    }
    suspend fun loadFolders(accountId: String): List<ErmisFolder> = operation {
        val store = store(account(accountId))
        try {
            // JavaMail materializes LIST before this check; reject unusually large server catalogs.
            val listed = store.defaultFolder.list("*")
            require(listed.size <= 1000)
            val selectable = listed.filter { it.type and Folder.HOLDS_MESSAGES != 0 }
            require(selectable.size <= 200)
            val folders = selectable.map { f ->
                val fullName = ErmisMailboxPolicy.folder(f.fullName)
                ErmisFolder(accountId, fullName, f.name.take(1024), ErmisMailboxPolicy.role(fullName, f.name,
                    (f as? IMAPFolder)?.attributes?.take(32).orEmpty()))
            }.distinctBy { it.id }.sortedWith(compareBy<ErmisFolder> { it.role != ErmisFolderRole.INBOX }.thenBy { it.name.lowercase(java.util.Locale.ROOT) })
            mutable.update { it.copy(folders = it.folders.filterNot { f -> f.accountId == accountId } + folders) }
            persist()
            folders
        } finally { store.close() }
    }
    /** Create only an explicitly named custom mailbox; never replace an existing server folder. */
    suspend fun createFolder(accountId: String, name: String) = operation {
        val canonical = ErmisMailboxPolicy.customFolder(name)
        require(state.value.folders.count { it.accountId == accountId } < 200)
        val store = store(account(accountId))
        try {
            val target = store.getFolder(canonical)
            require(!target.exists())
            check(target.create(Folder.HOLDS_MESSAGES))
        } finally { store.close() }
    }
    /** Special-use folders and mailbox hierarchies are deliberately not renamed. */
    suspend fun renameFolder(accountId: String, name: String, targetName: String) = operation {
        val sourceName = ErmisMailboxPolicy.customFolder(name)
        require(sourceName == name) // Never normalize an existing server path into a different folder.
        val canonical = ErmisMailboxPolicy.customFolder(targetName)
        require(sourceName != canonical)
        val store = store(account(accountId))
        try {
            val source = store.getFolder(sourceName)
            require(source.exists() && source.type and Folder.HOLDS_MESSAGES != 0)
            require(ErmisMailboxPolicy.role(source.fullName, source.name,
                (source as? IMAPFolder)?.attributes.orEmpty().toList()) == ErmisFolderRole.OTHER)
            require(source.list("*").isEmpty())
            val target = store.getFolder(canonical)
            require(!target.exists())
            check(source.renameTo(target))
            // Retain source UIDs only in forward references, which independently verify UIDVALIDITY.
            // The renamed mailbox must be synchronized before its cached rows can be used again.
            uidValidity = uidValidity - ErmisMailboxPolicy.validityKey(accountId, sourceName) - ErmisMailboxPolicy.validityKey(accountId, canonical)
            mutable.update { state -> state.copy(
                messages = state.messages.filterNot { ErmisMailboxPolicy.matches(it, accountId, sourceName) || ErmisMailboxPolicy.matches(it, accountId, canonical) },
                folders = state.folders.filterNot { it.accountId == accountId && it.id == sourceName },
                drafts = state.drafts.map { draft ->
                    val forward = draft.forwardSource
                    if (forward != null && forward.accountId == accountId && forward.folder == sourceName)
                        draft.copy(forwardSource = forward.copy(folder = canonical)) else draft
                }) }
            persist()
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
        uidValidity = uidValidity.filterKeys { it != id && !it.startsWith("$id|") }
        mutable.update { it.copy(accounts = it.accounts.filterNot { a -> a.id == id }, messages = it.messages.filterNot { m -> m.accountId == id }, drafts = it.drafts.filterNot { d -> d.accountId == id || d.forwardSource?.accountId == id }, folders = it.folders.filterNot { f -> f.accountId == id }) }
        persist()
        if (state.value.accounts.isEmpty()) ErmisWorker.cancel(context)
    }
    suspend fun sync(accountId: String? = null, folder: String = "INBOX") = operation {
        val canonical = ErmisMailboxPolicy.folder(folder)
        require(accountId != null || canonical == "INBOX")
        for (a in state.value.accounts.filter { accountId == null || it.id == accountId }) {
            mailbox(a, canonical, Folder.READ_ONLY) { mailbox, uids ->
                checkValidity(a.id, canonical, uids, replaceChanged = true)
                val count = mailbox.messageCount
                val incoming = if (count == 0) emptyList() else summaries(a, canonical, mailbox, uids, mailbox.getMessages(maxOf(1, count - 149), count))
                mutable.update { state ->
                    val retained = state.messages.filterNot { ErmisMailboxPolicy.matches(it, a.id, canonical) }
                    val merged = if (accountId == null) (retained + incoming).sortedByDescending { it.date }.take(1000)
                        else (incoming + retained).take(1000).sortedByDescending { it.date }
                    state.copy(messages = ErmisCachePolicy.bound(merged))
                }
            }
        }
        persist()
    }
    /** Server-side sender/subject/body search, bounded to the most recent 5000 messages and 100 results. */
    suspend fun serverSearch(accountId: String, folder: String, query: String): List<ErmisMessage> = operation {
        require(query.isNotBlank() && query.length <= 256 && query.none { it == '\r' || it == '\n' || it == '\u0000' })
        val a = account(accountId)
        val canonical = ErmisMailboxPolicy.folder(folder)
        val result = mailbox(a, canonical, Folder.READ_ONLY) { mailbox, uids ->
            checkValidity(accountId, canonical, uids, replaceChanged = true)
            val count = mailbox.messageCount
            if (count == 0) emptyList() else {
                val candidates = mailbox.getMessages(maxOf(1, count - ErmisMailboxPolicy.SERVER_SEARCH_WINDOW + 1), count)
                val found = mailbox.search(OrTerm(arrayOf(SubjectTerm(query.trim()), FromStringTerm(query.trim()), BodyTerm(query.trim()))), candidates)
                    .sortedByDescending { it.messageNumber }.take(ErmisMailboxPolicy.SERVER_SEARCH_RESULTS).toTypedArray()
                summaries(a, canonical, mailbox, uids, found)
            }
        }
        mutable.update { state -> state.copy(messages = ErmisCachePolicy.bound((result + state.messages)
            .distinctBy { Triple(it.accountId, it.folder, it.uid) }.take(1000).sortedByDescending { it.date })) }
        persist()
        result
    }
    suspend fun loadMessage(accountId: String, uid: Long, folder: String = "INBOX"): ErmisMessage? = operation {
        mailbox(account(accountId), folder) { _, uids ->
            checkValidity(accountId, folder, uids, replaceChanged = false)
            val m = uids.getMessageByUID(uid) ?: return@mailbox null
            val result = StringBuilder(); val attachments = mutableListOf<ErmisAttachment>()
            val mime = ErmisMime.read(m)
            result.append(mime.body)
            attachments.addAll(mime.attachments.mapIndexed { index, part -> ErmisAttachment(index, part.fileName?.let { MimeUtility.decodeText(it) } ?: "attachment", part.contentType.substringBefore(';'), part.size) })
            m.setFlag(Flags.Flag.SEEN, true)
            val old = state.value.messages.firstOrNull { ErmisMailboxPolicy.matches(it, accountId, folder, uid) } ?: return@mailbox null
            val updated = old.copy(body = result.toString(), preview = result.toString().take(180), attachments = attachments, read = true,
                to = headerAddresses(m.getRecipients(Message.RecipientType.TO)), cc = headerAddresses(m.getRecipients(Message.RecipientType.CC)),
                replyTo = headerAddresses(m.replyTo).joinToString(", ").take(16_000), html = mime.html)
            val remoteNote = fetchNote(account(accountId), updated.messageId)
            val finalMessage = if (remoteNote != null) updated.copy(note=remoteNote) else updated
            replace(finalMessage); persist(); finalMessage
        }
    }
    private fun replace(message: ErmisMessage) { mutable.update { state -> state.copy(messages = ErmisCachePolicy.bound(
        state.messages.map { if (ErmisMailboxPolicy.matches(it, message.accountId, message.folder, message.uid)) message else it }, message)) } }
    suspend fun read(accountId: String, uid: Long, folder: String = "INBOX") = setRead(accountId, uid, true, folder)
    suspend fun setRead(accountId: String, uid: Long, read: Boolean, folder: String = "INBOX") = operation { flag(accountId, uid, folder, Flags.Flag.SEEN, read) }
    suspend fun star(accountId: String, uid: Long, starred: Boolean, folder: String = "INBOX") = operation { flag(accountId, uid, folder, Flags.Flag.FLAGGED, starred) }
    private fun flag(id: String, uid: Long, folder: String, flag: Flags.Flag, value: Boolean) {
        mailbox(account(id), folder) { _, uids ->
            checkValidity(id, folder, uids, replaceChanged = false)
            val message = uids.getMessageByUID(uid) ?: error("Message unavailable")
            message.setFlag(flag, value)
        }
        state.value.messages.find { ErmisMailboxPolicy.matches(it, id, folder, uid) }?.let { replace(if (flag == Flags.Flag.SEEN) it.copy(read=value) else it.copy(starred=value)) }
        persist()
    }
    suspend fun moveMessage(accountId: String, uid: Long, sourceFolder: String, targetFolder: String) = operation {
        val source = ErmisMailboxPolicy.folder(sourceFolder)
        val target = ErmisMailboxPolicy.folder(targetFolder)
        require(source != target)
        mailbox(account(accountId), source) { mailbox, uids ->
            checkValidity(accountId, source, uids, replaceChanged = false)
            val message = uids.getMessageByUID(uid) ?: error("Message unavailable")
            val imap = mailbox as? IMAPFolder ?: error("IMAP mailbox required")
            val store = imap.store as? IMAPStore ?: error("IMAP store required")
            val destination = store.getFolder(target)
            require(destination.exists() && destination.type and Folder.HOLDS_MESSAGES != 0)
            val messages = arrayOf(message)
            ErmisMovePolicy.execute(store.hasCapability("MOVE"), store.hasCapability("UIDPLUS"),
                move = {
                    if (store.hasCapability("UIDPLUS")) imap.moveUIDMessages(messages, destination)
                    else imap.moveMessages(messages, destination)
                },
                copy = { imap.copyMessages(messages, destination) },
                markDeleted = { message.setFlag(Flags.Flag.DELETED, true) },
                expungeSelected = { imap.expunge(messages) })
        }
        mutable.update { state -> state.copy(messages = state.messages.filterNot { ErmisMailboxPolicy.matches(it, accountId, source, uid) }) }
        persist()
    }
    suspend fun forwardSource(accountId: String, folder: String, uid: Long): ErmisForwardSource = operation {
        val canonical = ErmisMailboxPolicy.folder(folder)
        mailbox(account(accountId), canonical, Folder.READ_ONLY) { _, uids ->
            checkValidity(accountId, canonical, uids, replaceChanged = false)
            require(uids.getMessageByUID(uid) != null) { "Message unavailable" }
            ErmisForwardSource(accountId, canonical, uid, uids.getUIDValidity()).also(ErmisForwardPolicy::validate)
        }
    }
    private fun forwardBytes(source: ErmisForwardSource): ByteArray {
        ErmisForwardPolicy.validate(source)
        return mailbox(account(source.accountId), source.folder, Folder.READ_ONLY) { _, uids ->
            ErmisForwardPolicy.requireSameMailbox(source, uids.getUIDValidity())
            val original = uids.getMessageByUID(source.uid) as? MimeMessage ?: error("Message unavailable")
            val output = ErmisBoundedOutputStream(15_000_000)
            original.writeTo(output, arrayOf("Bcc", "Resent-Bcc"))
            output.toByteArray()
        }
    }
    suspend fun saveDraft(id: String, draft: ErmisDraft): ErmisSavedDraft = operation {
        account(draft.accountId)
        draft.forwardSource?.let { ErmisForwardPolicy.validate(it); account(it.accountId) }
        val saved = ErmisSavedDraft(id, draft.accountId, draft.to, draft.subject, draft.body,
            draft.attachments.map { it.toString() }, draft.inReplyTo, draft.references, draft.cc, draft.bcc,
            System.currentTimeMillis(), draft.forwardSource)
        ErmisDraftPolicy.validate(saved)
        // Persisted drafts must remain readable after a process restart. Never drop inaccessible attachments.
        val permissions = context.contentResolver.persistedUriPermissions
        require(draft.attachments.all { uri -> permissions.any { it.uri == uri && it.isReadPermission } }) {
            "Attachment needs persistent document permission"
        }
        val next = state.value.copy(drafts = ErmisDraftPolicy.upsert(state.value.drafts, saved))
        persist(next)
        mutable.value = next
        saved
    }
    suspend fun deleteDraft(id: String) = operation {
        val next = state.value.copy(drafts = state.value.drafts.filterNot { it.id == id })
        persist(next)
        mutable.value = next
    }
    suspend fun send(draft: ErmisDraft) = operation {
        val a = account(draft.accountId)
        val recipients = ErmisRecipients.parse(draft.to, draft.cc, draft.bcc)
        ErmisRecipients.validateHeaders(draft.subject, draft.inReplyTo, draft.references)
        require(draft.body.toByteArray(Charsets.UTF_8).size <= 200_000 && draft.attachments.size + (if (draft.forwardSource != null) 1 else 0) <= 20)
        val forwarded = draft.forwardSource?.let(::forwardBytes)
        val message = MimeMessage(session()).apply {
            setFrom(InternetAddress(a.email, a.name)); recipients.apply(this)
            setSubject(draft.subject, "UTF-8"); sentDate = java.util.Date()
            draft.references.filter { !it.contains('\r') && !it.contains('\n') }.takeLast(100).takeIf { it.isNotEmpty() }?.let { setHeader("References", it.joinToString(" ")) }
            draft.inReplyTo?.takeIf { !it.contains('\r') && !it.contains('\n') }?.let { setHeader("In-Reply-To",it) }
            val multipart = MimeMultipart()
            multipart.addBodyPart(MimeBodyPart().apply { setText(draft.body,"UTF-8") })
            var total = forwarded?.size ?: 0
            if (forwarded != null) multipart.addBodyPart(MimeBodyPart().apply {
                dataHandler = DataHandler(ByteArrayDataSource(forwarded, "message/rfc822"))
                fileName = "forwarded-message.eml"
                disposition = Part.ATTACHMENT
            })
            for (uri in draft.attachments) {
                val bytes = context.contentResolver.openInputStream(uri)?.use { bounded(it, 15_000_000) } ?: error("Attachment unavailable")
                total += bytes.size; require(total <= 20_000_000) { "Attachments exceed 20 MB" }
                val name = context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { if(it.moveToFirst()) it.getString(0) else null } ?: "attachment"
                multipart.addBodyPart(MimeBodyPart().apply { dataHandler = DataHandler(ByteArrayDataSource(bytes,context.contentResolver.getType(uri) ?: "application/octet-stream")); fileName = MimeUtility.encodeText(name) })
            }
            setContent(multipart); saveChanges()
        }
        val transport = transport(a)
        ErmisSmtpCompletion.send({ transport.sendMessage(message,recipients.envelope) }, { transport.close() })
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
    suspend fun saveNote(accountId: String, uid: Long, text: String, folder: String = "INBOX") = operation {
        require(text.length <= 50_000)
        val old = state.value.messages.first { ErmisMailboxPolicy.matches(it, accountId, folder, uid) }
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
                    if (existing.isNotEmpty() && notes is IMAPFolder && (store as? IMAPStore)?.hasCapability("UIDPLUS") == true)
                        notes.expunge(existing.toTypedArray())
                } finally { notes.close(false) }
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
    suspend fun downloadAttachment(accountId: String, uid: Long, index: Int, destination: Uri, folder: String = "INBOX") = operation {
        mailbox(account(accountId), folder) { _, uids ->
            checkValidity(accountId, folder, uids, replaceChanged = false)
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
