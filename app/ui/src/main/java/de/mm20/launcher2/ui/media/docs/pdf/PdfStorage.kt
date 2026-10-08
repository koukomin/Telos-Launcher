/*
 * Telos PDF tools.
 *
 * Adapted from PaperKnife+ (https://github.com/potatameister/PaperKnifePlus)
 * Copyright (C) potatameister and PaperKnife+ contributors
 * Copyright (C) 2026 Telos contributors
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version. It is distributed WITHOUT ANY WARRANTY; see the
 * GNU General Public License for more details (https://www.gnu.org/licenses/).
 *
 * PDF processing uses PdfBox-Android (https://github.com/TomRoush/PdfBox-Android, Apache-2.0).
 */

package de.mm20.launcher2.ui.media.docs.pdf

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

/** A result that has been written to a place the user can reach. */
class SavedFile(val name: String, val uri: Uri, val mime: String, val size: Long)

class HistoryEntry(val id: Long, val time: Long, val tool: String, val files: List<SavedFile>)

/** Writing results out of the cache, sharing them, and remembering what was saved. */
object PdfStorage {
    /** Saving to Documents/Telos or Pictures/Telos without any permission needs scoped storage. */
    val mediaStoreSupported: Boolean get() = Build.VERSION.SDK_INT >= 29

    fun mediaStoreFolder(mime: String): String =
        (if (mime.startsWith("image/")) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_DOCUMENTS) + "/Telos"

    fun saveToMediaStore(context: Context, r: ResultFile): SavedFile {
        if (!mediaStoreSupported) throw IOException("MediaStore saving needs Android 10")
        val isImage = r.mime.startsWith("image/")
        val resolver = context.contentResolver
        val collection = if (isImage) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, r.name)
            put(MediaStore.MediaColumns.MIME_TYPE, r.mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, mediaStoreFolder(r.mime))
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: throw IOException("insert failed")
        try {
            val out = resolver.openOutputStream(uri, "w") ?: throw IOException("no output stream")
            out.use { o -> r.file.inputStream().use { it.copyTo(o) } }
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        return SavedFile(r.name, uri, r.mime, r.size)
    }

    fun saveToTree(context: Context, tree: Uri, r: ResultFile): SavedFile {
        val resolver = context.contentResolver
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val doc = DocumentsContract.createDocument(resolver, parent, r.mime, r.name)
            ?: throw IOException("could not create ${r.name}")
        copyTo(context, r, doc)
        return SavedFile(r.name, doc, r.mime, r.size)
    }

    /** Writes into a document the user created with the system file dialog. */
    fun saveToDocument(context: Context, target: Uri, r: ResultFile): SavedFile {
        copyTo(context, r, target)
        return SavedFile(r.name, target, r.mime, r.size)
    }

    private fun copyTo(context: Context, r: ResultFile, target: Uri) {
        val out = context.contentResolver.openOutputStream(target, "w") ?: throw IOException("no output stream")
        out.use { o -> r.file.inputStream().use { it.copyTo(o) } }
    }

    fun cacheUri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun share(context: Context, items: List<Pair<Uri, String>>) {
        if (items.isEmpty()) return
        val intent = if (items.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = items[0].second
                putExtra(Intent.EXTRA_STREAM, items[0].first)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = if (items.all { it.second == items[0].second }) items[0].second else "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(items.map { it.first }))
            }
        }
        val clip = ClipData.newRawUri(null, items[0].first)
        for (i in 1 until items.size) clip.addItem(ClipData.Item(items[i].first))
        intent.clipData = clip
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, null))
    }

    fun open(context: Context, uri: Uri, mime: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, null))
    }

    // ---------------------------------------------------------------- history

    private fun historyFile(context: Context) = File(context.filesDir, "pdf_tools_history.json")

    @Synchronized
    fun loadHistory(context: Context): List<HistoryEntry> {
        val f = historyFile(context)
        if (!f.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val files = o.getJSONArray("files")
                HistoryEntry(
                    o.getLong("id"), o.getLong("time"), o.getString("tool"),
                    (0 until files.length()).map { j ->
                        val fo = files.getJSONObject(j)
                        SavedFile(fo.getString("name"), Uri.parse(fo.getString("uri")), fo.getString("mime"), fo.getLong("size"))
                    },
                )
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    private fun writeHistory(context: Context, entries: List<HistoryEntry>) {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(JSONObject().apply {
                put("id", e.id)
                put("time", e.time)
                put("tool", e.tool)
                put("files", JSONArray().apply {
                    e.files.forEach { s ->
                        put(JSONObject().apply {
                            put("name", s.name)
                            put("uri", s.uri.toString())
                            put("mime", s.mime)
                            put("size", s.size)
                        })
                    }
                })
            })
        }
        runCatching { historyFile(context).writeText(arr.toString()) }
    }

    @Synchronized
    fun addHistory(context: Context, tool: PdfTool?, files: List<SavedFile>) {
        if (files.isEmpty()) return
        val entry = HistoryEntry(System.currentTimeMillis(), System.currentTimeMillis(), tool?.key ?: "", files)
        writeHistory(context, (listOf(entry) + loadHistory(context)).take(200))
    }

    @Synchronized
    fun removeHistory(context: Context, id: Long) {
        writeHistory(context, loadHistory(context).filter { it.id != id })
    }

    @Synchronized
    fun clearHistory(context: Context) {
        runCatching { historyFile(context).delete() }
    }
}
