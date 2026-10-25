package de.mm20.launcher2.ui.common.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.comms.contactVcard
import de.mm20.launcher2.ui.comms.vcardText
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * One place for handing things to other apps. Files shared through this helper are first copied into
 * `cache/share` (covered by the app's FileProvider), so the provider never exposes a private storage path.
 * Callers must not pass anything from a locked area (vault, locked app); this helper cannot know about it.
 */
object ShareActions {

    private const val DIR = "share"
    private const val MAX_AGE_MS = 24L * 60 * 60 * 1000
    const val MIME_APK = "application/vnd.android.package-archive"
    const val MIME_VCARD = "text/x-vcard"
    private val INVALID_FILE_CHARS = Regex("[\\u0000-\\u001f:*?\"<>|]")

    private fun dir(context: Context): File = File(context.cacheDir, DIR).apply { mkdirs() }

    /** Removes what an earlier share left behind */
    private fun cleanup(context: Context) {
        val limit = System.currentTimeMillis() - MAX_AGE_MS
        runCatching {
            dir(context).listFiles()?.forEach { if (it.lastModified() < limit) it.deleteRecursively() }
        }
    }

    /** A file name without path parts or characters that file systems dislike */
    fun safeName(name: String, fallback: String = "file"): String {
        val clean = name.substringAfterLast('/').substringAfterLast('\\')
            .replace(INVALID_FILE_CHARS, "_").trim().trim('.')
            .take(100)
        return clean.ifEmpty { fallback }
    }

    fun mimeOf(name: String): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase()) ?: "application/octet-stream"

    private fun uriOf(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /**
     * Builds the chooser for already-readable URIs. File shares should normally use [shareUris],
     * which stages their contents in the app's share cache before calling this.
     */
    fun createChooserIntent(
        uris: List<Uri>,
        mime: String,
        chooserTitle: CharSequence? = null,
        subject: String? = null,
        text: String? = null,
    ): Intent {
        require(uris.isNotEmpty()) { "At least one URI is required" }
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        intent.type = mime
        if (subject != null) intent.putExtra(Intent.EXTRA_SUBJECT, subject)
        if (text != null) intent.putExtra(Intent.EXTRA_TEXT, text)
        val clip = ClipData.newRawUri(null, uris[0])
        for (i in 1 until uris.size) clip.addItem(ClipData.Item(uris[i]))
        intent.clipData = clip
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(intent, chooserTitle)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Starts the system share sheet for [uris], read permission is granted through the ClipData */
    private fun send(
        context: Context,
        uris: List<Uri>,
        mime: String,
        subject: String?,
        text: String? = null,
        chooserTitle: CharSequence? = null,
    ): Boolean {
        if (uris.isEmpty()) return false
        return runCatching {
            context.startActivity(
                createChooserIntent(uris, mime, chooserTitle, subject, text)
            )
        }.isSuccess
    }

    /** Shares a copy of [file] as [name]. Blocking (copies the file), call it off the main thread for big files. */
    fun shareFile(
        context: Context,
        file: File,
        mime: String? = null,
        name: String? = null,
        chooserTitle: CharSequence? = null,
        text: String? = null,
    ): Boolean = runCatching {
        cleanup(context)
        val shown = safeName(name ?: file.name)
        val copy = File(File(dir(context), System.nanoTime().toString()).apply { mkdirs() }, shown)
        file.copyTo(copy, overwrite = true)
        send(context, listOf(uriOf(context, copy)), mime ?: mimeOf(shown), shown, text, chooserTitle)
    }.getOrDefault(false)

    /** Shares what [uri] points to (a content URI the app may read) as [name]. Blocking. */
    fun shareFile(
        context: Context,
        uri: Uri,
        mime: String?,
        name: String,
        chooserTitle: CharSequence? = null,
        text: String? = null,
    ): Boolean = runCatching {
        cleanup(context)
        val shown = safeName(name)
        val copy = File(File(dir(context), System.nanoTime().toString()).apply { mkdirs() }, shown)
        val input = when (uri.scheme) {
            "file" -> uri.path?.let(::File)?.inputStream()
            else -> context.contentResolver.openInputStream(uri)
        } ?: throw IOException("no stream")
        input.use { i -> copy.outputStream().use { o -> i.copyTo(o) } }
        send(context, listOf(uriOf(context, copy)), mime ?: mimeOf(shown), shown, text, chooserTitle)
    }.getOrDefault(false)

    /** Copies readable content URIs into the share cache, then shares them with explicit read grants. Blocking. */
    fun shareUris(
        context: Context,
        uris: List<Uri>,
        mimeType: String? = null,
        chooserTitle: CharSequence? = null,
    ): Boolean = runCatching {
        if (uris.isEmpty()) return@runCatching false
        cleanup(context)
        val folder = File(dir(context), System.nanoTime().toString()).apply { mkdirs() }
        try {
            val used = HashSet<String>()
            val copied = uris.mapIndexed { index, uri ->
                val queriedName = runCatching {
                    context.contentResolver.query(
                        uri,
                        arrayOf(OpenableColumns.DISPLAY_NAME),
                        null,
                        null,
                        null,
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    }
                }.getOrNull()
                val sourceName = queriedName
                    ?: uri.lastPathSegment?.substringAfterLast('/')
                    ?: "file-${index + 1}"
                var name = safeName(sourceName, "file-${index + 1}")
                var suffix = 1
                while (!used.add(name)) {
                    name = "($suffix) ${safeName(sourceName, "file-${index + 1}")}"
                    suffix++
                }

                val copy = File(folder, name)
                val input = when (uri.scheme) {
                    "file" -> uri.path?.let(::File)?.inputStream()
                    else -> context.contentResolver.openInputStream(uri)
                } ?: throw IOException("no stream")
                input.use { source -> copy.outputStream().use { target -> source.copyTo(target) } }
                uriOf(context, copy) to (runCatching { context.contentResolver.getType(uri) }.getOrNull() ?: mimeOf(name))
            }
            val sharedUris = copied.map { it.first }
            val mimeTypes = copied.map { it.second }.distinct()
            send(context, sharedUris, mimeType ?: mimeTypes.singleOrNull() ?: "*/*", null, chooserTitle = chooserTitle)
        } catch (e: Exception) {
            folder.deleteRecursively()
            throw e
        }
    }.getOrDefault(false)

    fun shareText(context: Context, text: String, subject: String? = null): Boolean {
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        if (subject != null) intent.putExtra(Intent.EXTRA_SUBJECT, subject)
        return runCatching {
            context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
    }

    internal fun shareUris(context: Context, uris: List<Uri>, mime: String, subject: String?): Boolean =
        send(context, uris, mime, subject)

    /** A .vcf card (CRLF line ends, escaped values); the text is attached as well for apps that only take text */
    fun shareContactVcard(context: Context, name: String, phones: List<String>, emails: List<String>): Boolean = runCatching {
        cleanup(context)
        val card = contactVcard(name, phones, emails)
            .replaceFirst("VERSION:3.0\n", "VERSION:3.0\nN:;${vcardText(name)};;;\n")
            .replace("\r\n", "\n").replace("\n", "\r\n")
        val copy = File(File(dir(context), System.nanoTime().toString()).apply { mkdirs() }, safeName(name, "contact") + ".vcf")
        copy.writeText(card)
        send(context, listOf(uriOf(context, copy)), MIME_VCARD, name, card)
    }.getOrDefault(false)

    /** A note as plain text, or as a .md file (title as heading) */
    fun shareNote(context: Context, title: String, body: String, asMarkdownFile: Boolean): Boolean {
        if (!asMarkdownFile) return shareText(context, listOf(title, body).filter { it.isNotBlank() }.joinToString("\n\n"), title.ifBlank { null })
        return runCatching {
            cleanup(context)
            val md = (if (title.isNotBlank()) "# $title\n\n" else "") + body
            val copy = File(File(dir(context), System.nanoTime().toString()).apply { mkdirs() }, safeName(title, "note") + ".md")
            copy.writeText(md)
            send(context, listOf(uriOf(context, copy)), "text/markdown", title.ifBlank { null })
        }.getOrDefault(false)
    }

    /**
     * The APK of an installed app. A split app (several APK files) is shared as one .apks archive, which
     * contains every part; a plain app as its .apk. Blocking, call it off the main thread.
     */
    fun shareApk(context: Context, packageName: String): Boolean = runCatching {
        cleanup(context)
        val pm = context.packageManager
        val initial = pm.getPackageInfo(packageName, 0)
        val info = requireNotNull(initial.applicationInfo)
        val paths = listOf(info.publicSourceDir) + info.splitPublicSourceDirs.orEmpty().sorted()
        val base = safeName(packageName)
        val folder = File(dir(context), System.nanoTime().toString()).apply { mkdirs() }
        val copy = File(folder, base + if (paths.size > 1) ".zip" else ".apk")
        try {
            copy.outputStream().buffered().use { output ->
                de.mm20.launcher2.helper.InstalledApkExport.write(paths.map(::File), output)
            }
            val after = pm.getPackageInfo(packageName, 0)
            val current = requireNotNull(after.applicationInfo)
            require(initial.lastUpdateTime == after.lastUpdateTime &&
                paths == listOf(current.publicSourceDir) + current.splitPublicSourceDirs.orEmpty().sorted())
            send(context, listOf(uriOf(context, copy)), if (paths.size > 1) "application/zip" else MIME_APK, null)
        } catch (error: Exception) {
            copy.delete()
            throw error
        }
    }.getOrDefault(false)

    /** Shares the files of [paths] (all local or already downloaded) as one share action */
    fun shareFiles(context: Context, files: List<File>): Boolean = runCatching {
        cleanup(context)
        val folder = File(dir(context), System.nanoTime().toString()).apply { mkdirs() }
        val used = HashSet<String>()
        val uris = files.filter { it.isFile }.map { f ->
            var n = safeName(f.name)
            var i = 1
            while (!used.add(n)) n = "(${i++}) " + safeName(f.name)
            val copy = File(folder, n)
            f.copyTo(copy, overwrite = true)
            uriOf(context, copy)
        }
        val mimes = files.filter { it.isFile }.map { mimeOf(it.name) }.distinct()
        send(context, uris, mimes.singleOrNull() ?: "*/*", null)
    }.getOrDefault(false)
}

/** The "Share" entry of a long-press menu, to be placed inside a DropdownMenu */
@Composable
fun ShareMenuItem(onClick: () -> Unit, modifier: Modifier = Modifier) {
    DropdownMenuItem(
        text = { Text(stringResource(R.string.menu_share)) },
        leadingIcon = { Icon(painterResource(R.drawable.share_24px), contentDescription = null, modifier = Modifier.size(24.dp)) },
        onClick = onClick,
        modifier = modifier,
    )
}
