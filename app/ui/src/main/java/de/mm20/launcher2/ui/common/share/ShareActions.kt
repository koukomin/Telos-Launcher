package de.mm20.launcher2.ui.common.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
 * One place for handing things to other apps. Everything that is shared as a file is first copied into
 * `cache/share` (covered by the app's FileProvider), so the provider never exposes a private storage path.
 * Callers must not pass anything from a locked area (vault, locked app); this helper cannot know about it.
 */
object ShareActions {

    private const val DIR = "share"
    private const val MAX_AGE_MS = 24L * 60 * 60 * 1000
    const val MIME_APK = "application/vnd.android.package-archive"
    const val MIME_VCARD = "text/x-vcard"

    private fun dir(context: Context): File = File(context.cacheDir, DIR).apply { mkdirs() }

    /** Removes what an earlier share left behind */
    private fun cleanup(context: Context) {
        val limit = System.currentTimeMillis() - MAX_AGE_MS
        runCatching { dir(context).listFiles()?.forEach { if (it.lastModified() < limit) it.delete() } }
    }

    /** A file name without path parts or characters that file systems dislike */
    fun safeName(name: String, fallback: String = "file"): String {
        val clean = name.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[\\u0000-\\u001f:*?\"<>|]"), "_").trim().trim('.')
            .take(100)
        return clean.ifEmpty { fallback }
    }

    fun mimeOf(name: String): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase()) ?: "application/octet-stream"

    private fun uriOf(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /** Starts the system share sheet for [uris], read permission is granted through the ClipData */
    private fun send(context: Context, uris: List<Uri>, mime: String, subject: String?, text: String? = null): Boolean {
        if (uris.isEmpty()) return false
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
        return runCatching {
            context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
    }

    /** Shares a copy of [file] as [name]. Blocking (copies the file), call it off the main thread for big files. */
    fun shareFile(context: Context, file: File, mime: String? = null, name: String? = null): Boolean = runCatching {
        cleanup(context)
        val shown = safeName(name ?: file.name)
        val copy = File(File(dir(context), System.nanoTime().toString()).apply { mkdirs() }, shown)
        file.copyTo(copy, overwrite = true)
        send(context, listOf(uriOf(context, copy)), mime ?: mimeOf(shown), shown)
    }.getOrDefault(false)

    /** Shares what [uri] points to (a content URI the app may read) as [name]. Blocking. */
    fun shareFile(context: Context, uri: Uri, mime: String?, name: String): Boolean = runCatching {
        cleanup(context)
        val shown = safeName(name)
        val copy = File(File(dir(context), System.nanoTime().toString()).apply { mkdirs() }, shown)
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("no stream")
        input.use { i -> copy.outputStream().use { o -> i.copyTo(o) } }
        send(context, listOf(uriOf(context, copy)), mime ?: mimeOf(shown), shown)
    }.getOrDefault(false)

    fun shareText(context: Context, text: String, subject: String? = null): Boolean {
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        if (subject != null) intent.putExtra(Intent.EXTRA_SUBJECT, subject)
        return runCatching {
            context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
    }

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
        if (!asMarkdownFile) return shareText(context, body.ifBlank { title }, title.ifBlank { null })
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
        val info = pm.getApplicationInfo(packageName, 0)
        val version = runCatching { pm.getPackageInfo(packageName, 0).versionName }.getOrNull()
        val base = "${safeName(packageName)}${if (version != null) "-" + safeName(version) else ""}"
        val folder = File(dir(context), System.nanoTime().toString()).apply { mkdirs() }
        val parts = listOf(info.publicSourceDir) + (info.splitPublicSourceDirs?.toList() ?: emptyList())
        val files = parts.filterNotNull().map { File(it) }.filter { it.isFile && it.canRead() }
        if (files.isEmpty()) return@runCatching false
        if (files.size == 1) {
            val copy = File(folder, "$base.apk")
            files[0].copyTo(copy, overwrite = true)
            send(context, listOf(uriOf(context, copy)), MIME_APK, null)
        } else {
            val copy = File(folder, "$base.apks")
            ZipOutputStream(copy.outputStream().buffered()).use { zip ->
                files.forEach { f ->
                    zip.putNextEntry(ZipEntry(f.name))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            send(context, listOf(uriOf(context, copy)), "application/zip", null)
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
