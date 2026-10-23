package de.mm20.launcher2.ui.notes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.text.Layout
import android.text.SpannableString
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.RelativeSizeSpan
import androidx.core.content.FileProvider
import de.mm20.launcher2.ui.common.share.ShareActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Bounded, full-content PNG pages, independent of editor viewport and system font scale. */
internal suspend fun renderNoteImages(context: Context, note: Note): List<File>? {
    val app = context.applicationContext
    return withContext(Dispatchers.IO) {
        runCatching {
            val parts = listOf(note.title, note.body).filter { it.isNotBlank() }
            require(parts.sumOf { it.length.toLong() } + (if (parts.size > 1) 2 else 0) <= 100_000)
            val text = parts.joinToString("\n\n")
            require(text.length <= 100_000 && text.isNotBlank())
            val styled = SpannableString(text)
            if (note.title.isNotBlank()) styled.setSpan(RelativeSizeSpan(1.4f), 0, note.title.length, 0)
            val paint = TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textSize = 36f; color = Color.rgb(30, 30, 30) }
            val layout = StaticLayout.Builder.obtain(styled, 0, styled.length, paint, 936)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(true).setLineSpacing(8f, 1f).build()
            File(app.cacheDir, "share").listFiles()?.filter {
                it.isDirectory && it.name.startsWith("note-") && System.currentTimeMillis() - it.lastModified() > 86_400_000
            }?.forEach { old -> old.listFiles()?.forEach { it.delete() }; old.delete() }
            val folder = File(app.cacheDir, "share/note-${java.util.UUID.randomUUID()}").apply { check(mkdirs()) }
            val pages = mutableListOf<File>()
            var line = 0
            try {
                while (line < layout.lineCount) {
                    check(pages.size < 32)
                    val top = layout.getLineTop(line)
                    var end = line + 1
                    while (end < layout.lineCount && layout.getLineBottom(end) - top <= 1456) end++
                    val bottom = layout.getLineTop(end)
                    val bitmap = Bitmap.createBitmap(1080, bottom - top + 144, Bitmap.Config.ARGB_8888)
                    try {
                        val canvas = Canvas(bitmap)
                        canvas.drawColor(NotesStore.Colors.getOrNull(note.color)?.takeIf { it != 0 } ?: Color.rgb(255, 250, 230))
                        canvas.save()
                        canvas.clipRect(72, 72, 1008, bottom - top + 72)
                        canvas.translate(72f, 72f - top)
                        layout.draw(canvas)
                        canvas.restore()
                        val file = File(folder, "note-${pages.size + 1}.png")
                        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                        pages.add(file)
                    } finally { bitmap.recycle() }
                    line = end
                }
                pages
            } catch (e: Exception) {
                folder.listFiles()?.forEach { it.delete() }; folder.delete(); throw e
            }
        }.getOrNull()
    }
}

suspend fun shareNoteImages(context: Context, note: Note): Boolean {
    val app = context.applicationContext
    val files = renderNoteImages(app, note) ?: return false
    val uris = withContext(Dispatchers.IO) {
        runCatching { files.map { FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", it) } }.getOrNull()
    } ?: return false
    return withContext(Dispatchers.Main) { ShareActions.shareUris(app, uris, "image/png", note.title) }
}
