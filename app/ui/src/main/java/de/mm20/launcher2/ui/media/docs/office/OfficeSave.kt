package de.mm20.launcher2.ui.media.docs.office

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

internal enum class WriteResult { Ok, NotWritable, Partial }

/** Writing back to the sender's Uri. A file is always written completely to the cache first, so a failed save never cuts the original. */
internal object DocFiles {
    fun dir(context: Context, sub: String) = File(context.cacheDir, sub).apply { mkdirs() }

    fun sweep(context: Context) {
        val now = System.currentTimeMillis()
        dir(context, "doc_save").listFiles()?.filter { it.lastModified() < now - 6 * 60 * 60_000L }?.forEach { it.delete() }
        dir(context, "doc_undo").listFiles()?.filter { it.lastModified() < now - 7 * 24 * 60 * 60_000L }?.forEach { it.delete() }
    }

    fun tempFile(context: Context, name: String) = File(dir(context, "doc_save"), "${System.nanoTime()}_" + name.replace('/', '_'))

    fun undoFile(context: Context, uri: Uri) = File(dir(context, "doc_undo"), uri.toString().hashCode().toUInt().toString(16) + ".bak")

    fun copyToUri(context: Context, source: File, uri: Uri): WriteResult {
        val out = try {
            context.contentResolver.openOutputStream(uri, "wt")
        } catch (e: Exception) { null } ?: return WriteResult.NotWritable
        return try {
            out.use { o -> source.inputStream().use { it.copyTo(o) } }
            WriteResult.Ok
        } catch (e: Exception) { WriteResult.Partial }
    }

    fun mimeFor(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "odt" -> "application/vnd.oasis.opendocument.text"
        "ods" -> "application/vnd.oasis.opendocument.spreadsheet"
        "odp" -> "application/vnd.oasis.opendocument.presentation"
        "txt" -> "text/plain"
        else -> "application/octet-stream"
    }
}

internal sealed interface SaveOutcome {
    data object Saved : SaveOutcome
    /** The sender does not allow writing, [file] has to go to a new location chosen by the person */
    class NeedSaveAs(val file: File) : SaveOutcome
    data object Failed : SaveOutcome
}

/** State of one open Office document: edits that are not applied yet, the saved state and the undo of the last save. */
internal class OfficeController(private val context: Context, val uri: Uri, val name: String, initial: OfficeDoc) {
    var doc by mutableStateOf(initial)
        private set
    var version by mutableIntStateOf(0)
        private set
    var editing by mutableStateOf(false)
    var hasUndo by mutableStateOf(DocFiles.undoFile(context, uri).exists())
        private set

    val pendingParas = HashMap<Int, String>()
    val pendingBoxes = HashMap<Pair<Int, Int>, String>()

    val canEdit: Boolean get() = !doc.readOnly
    val isDirty: Boolean get() = doc.dirty || pendingParas.isNotEmpty() || pendingBoxes.isNotEmpty()

    fun commit() {
        if (pendingParas.isEmpty() && pendingBoxes.isEmpty()) return
        pendingParas.forEach { (id, t) -> doc.setParagraphText(id, t) }
        pendingBoxes.forEach { (k, t) -> doc.setBoxText(k.first, k.second, t) }
        pendingParas.clear(); pendingBoxes.clear()
        version++
    }

    fun mutate(block: OfficeDoc.() -> Unit) {
        commit()
        doc.block()
        version++
    }

    private fun swap(newDoc: OfficeDoc) {
        val old = doc
        pendingParas.clear(); pendingBoxes.clear()
        doc = newDoc
        version++
        old.close()
    }

    /** Throws away all edits that were not saved. */
    fun discard() {
        runCatching { swap(OfficeDocs.open(doc.file, name)) }
        editing = false
    }

    /** Writes the edited package to a temp file and checks that it can be read again. */
    suspend fun build(): File? = withContext(Dispatchers.IO) {
        runCatching {
            val tmp = DocFiles.tempFile(context, name)
            doc.save(tmp)
            OfficeDocs.open(tmp, name).also { d -> d.blocks(); d.sheets(); d.slides() }.close()
            tmp
        }.getOrNull()
    }

    private fun backup() {
        runCatching { doc.file.copyTo2(DocFiles.undoFile(context, uri)) }
        hasUndo = DocFiles.undoFile(context, uri).exists()
    }

    suspend fun save(): SaveOutcome {
        val tmp = build() ?: return SaveOutcome.Failed
        return withContext(Dispatchers.IO) {
            backup()
            when (DocFiles.copyToUri(context, tmp, uri)) {
                WriteResult.Ok -> { runCatching { swap(OfficeDocs.open(tmp, name)) }; editing = false; SaveOutcome.Saved }
                WriteResult.NotWritable -> SaveOutcome.NeedSaveAs(tmp)
                WriteResult.Partial -> {
                    // put the previous version back
                    DocFiles.copyToUri(context, DocFiles.undoFile(context, uri), uri)
                    SaveOutcome.NeedSaveAs(tmp)
                }
            }
        }
    }

    suspend fun finishSaveAs(tmp: File, target: Uri): Boolean = withContext(Dispatchers.IO) {
        val ok = DocFiles.copyToUri(context, tmp, target) == WriteResult.Ok
        if (ok) { runCatching { swap(OfficeDocs.open(tmp, name)) }; editing = false }
        ok
    }

    suspend fun undoLastSave(): Boolean = withContext(Dispatchers.IO) {
        val bak = DocFiles.undoFile(context, uri)
        if (!bak.exists()) return@withContext false
        val ok = DocFiles.copyToUri(context, bak, uri) == WriteResult.Ok
        if (ok) {
            runCatching {
                val copy = DocFiles.tempFile(context, name)
                bak.copyTo2(copy)
                swap(OfficeDocs.open(copy, name))
            }
            bak.delete()
            hasUndo = false
            editing = false
        }
        ok
    }

    /** Converts a legacy file to a new OOXML file in the cache; the original is not touched. */
    suspend fun convertLegacy(): Pair<File, String>? = withContext(Dispatchers.IO) {
        val legacy = doc as? LegacyDoc ?: return@withContext null
        runCatching {
            val newName = name.substringBeforeLast('.') + "." + legacy.targetExt
            val tmp = DocFiles.tempFile(context, newName)
            legacy.convertTo(tmp)
            OfficeDocs.open(tmp, newName).also { d -> d.blocks(); d.sheets(); d.slides() }.close()
            tmp to newName
        }.getOrNull()
    }

    fun close() = doc.close()
}

/** Big text files are not loaded: line starts are indexed and lines are read from the file when they are shown. */
internal class BigTextFile(val file: File) {
    private val raf = RandomAccessFile(file, "r")
    private var offsets = LongArray(4096)
    var lines = 0
        private set
    val length: Long = file.length()

    fun index() {
        var count = 1
        offsets[0] = 0
        var base = 0L
        val buf = ByteArray(256 * 1024)
        raf.seek(0)
        while (true) {
            val n = raf.read(buf)
            if (n < 0) break
            for (i in 0 until n) if (buf[i] == '\n'.code.toByte()) {
                if (count >= offsets.size) {
                    if (count >= 20_000_000) { lines = count; return }
                    offsets = offsets.copyOf(count * 2)
                }
                offsets[count++] = base + i + 1
            }
            base += n
        }
        lines = if (offsets[count - 1] >= length && count > 1) count - 1 else count
    }

    @Synchronized
    fun line(i: Int): String {
        if (i < 0 || i >= lines) return ""
        val start = offsets[i]
        val end = if (i + 1 < lines) offsets[i + 1] else length
        val len = (end - start).coerceAtMost(3000).toInt()
        if (len <= 0) return ""
        val b = ByteArray(len)
        raf.seek(start)
        raf.readFully(b)
        val s = String(b, Charsets.UTF_8).trimEnd('\n', '\r')
        return if (end - start > 3000) "$s…" else s
    }

    /** The beginning of the file as text, cut at a line end so that no character is split. */
    fun head(maxBytes: Int): String {
        val n = minOf(length, maxBytes.toLong()).toInt()
        val b = ByteArray(n)
        synchronized(this) { raf.seek(0); raf.readFully(b) }
        var cut = n
        if (length > n) { val nl = b.lastIndexOf('\n'.code.toByte()); if (nl > 0) cut = nl + 1 }
        return String(b, 0, cut, Charsets.UTF_8)
    }

    fun close() { runCatching { raf.close() } }
}
