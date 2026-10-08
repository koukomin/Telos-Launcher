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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix as GfxMatrix
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.exifinterface.media.ExifInterface
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitWidthDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.DateFormat
import java.util.Calendar
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

typealias Progress = (done: Int, total: Int) -> Unit

sealed interface LoadResult {
    class Ok(val source: PdfSource) : LoadResult
    class NeedsPassword(val raw: File, val name: String) : LoadResult
    class Failed(val reason: String) : LoadResult
}

class PageGroup(val suffix: String, val pages: List<Int>)

enum class CompressLevel(val jpegQuality: Float, val downscale: Float) {
    LOW(0.9f, 1.0f), MEDIUM(0.7f, 0.8f), HIGH(0.45f, 0.6f)
}

enum class NumberPosition { TOP_LEFT, TOP_CENTER, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT }

enum class PageSizeMode { FIT_IMAGE, A4, LETTER }

enum class ImageFormat(val ext: String, val mime: String) {
    PNG("png", "image/png"), JPEG("jpg", "image/jpeg"), WEBP("webp", "image/webp")
}

class Placement(
    /** 0-based page, or -1 for every page. */
    val page: Int,
    /** Centre of the stamp as a fraction of the displayed page, from the top left corner. */
    val x: Float,
    val y: Float,
    /** Width as a fraction of the displayed page width. */
    val width: Float,
    /** Clockwise degrees. */
    val rotation: Float,
    val bitmap: Bitmap,
)

class Bookmark(val title: String, val page: Int, val level: Int)

class PdfMeta(
    val title: String,
    val author: String,
    val subject: String,
    val keywords: String,
    val creator: String,
    val producer: String,
    val created: String,
    val modified: String,
    val version: Float,
    val pages: Int,
)

class ImageInfo(val index: Int, val page: Int, val width: Int, val height: Int, val suffix: String)

class CompareResult(val pagesA: Int, val pagesB: Int, val differing: List<Int>)

/** All PDF operations. Everything runs on [Dispatchers.IO], is cancellable between pages and writes into the cache. */
object PdfEngine {
    private val ids = AtomicLong(1)
    fun nextId(): Long = ids.getAndIncrement()

    fun workDir(context: Context): File = File(context.cacheDir, "pdftools").also { it.mkdirs() }
    private fun outDir(context: Context): File = File(workDir(context), "out").also { it.mkdirs() }

    /** Deletes everything this feature left in the cache. */
    fun clearCache(context: Context) {
        runCatching { workDir(context).deleteRecursively() }
    }

    private fun memory(context: Context): MemoryUsageSetting =
        MemoryUsageSetting.setupMixed(24L * 1024 * 1024).setTempDir(context.cacheDir)

    fun openDoc(context: Context, file: File, password: String = ""): PDDocument {
        PdfTools.ensureInit(context)
        return PDDocument.load(file, password, memory(context))
    }

    fun safeName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().ifEmpty { "file" }

    fun baseName(name: String): String = safeName(name).substringBeforeLast('.', safeName(name))

    private fun newResult(context: Context, name: String, mime: String): ResultFile {
        val ext = name.substringAfterLast('.', "bin")
        val f = File.createTempFile("r_", ".$ext", outDir(context))
        return ResultFile(f, safeName(name), mime)
    }

    private fun save(doc: PDDocument, to: File) {
        BufferedOutputStream(FileOutputStream(to)).use { out ->
            doc.save(out)
            out.flush()
        }
    }

    // ---------------------------------------------------------------- loading

    suspend fun loadSource(
        context: Context,
        uri: Uri,
        name: String,
        password: String? = null,
        rawFile: File? = null,
        allowBroken: Boolean = false,
    ): LoadResult = withContext(Dispatchers.IO) {
        PdfTools.ensureInit(context)
        val raw = rawFile ?: File.createTempFile("in_", ".pdf", workDir(context)).also { f ->
            try {
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw java.io.FileNotFoundException(uri.toString())
                input.use { i -> FileOutputStream(f).use { o -> i.copyTo(o) } }
            } catch (e: Exception) {
                f.delete()
                return@withContext LoadResult.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
        val displayName = if (name.contains('.')) name else "$name.pdf"
        try {
            openDoc(context, raw, password ?: "").use { doc ->
                val pages = doc.numberOfPages
                val encrypted = doc.isEncrypted
                val finalFile = if (encrypted) {
                    doc.isAllSecurityToBeRemoved = true
                    val dec = File.createTempFile("dec_", ".pdf", workDir(context))
                    save(doc, dec)
                    dec
                } else raw
                if (finalFile !== raw) raw.delete()
                LoadResult.Ok(PdfSource(nextId(), displayName, finalFile, pages, finalFile.length(), encrypted))
            }
        } catch (e: InvalidPasswordException) {
            LoadResult.NeedsPassword(raw, displayName)
        } catch (e: Exception) {
            if (allowBroken) {
                LoadResult.Ok(PdfSource(nextId(), displayName, raw, 0, raw.length(), broken = true))
            } else {
                raw.delete()
                LoadResult.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun sourceFromResult(context: Context, r: ResultFile): PdfSource? = runCatching {
        val copy = File.createTempFile("in_", ".pdf", workDir(context))
        r.file.copyTo(copy, overwrite = true)
        val pages = openDoc(context, copy).use { it.numberOfPages }
        PdfSource(nextId(), r.name, copy, pages, copy.length())
    }.getOrNull()

    // ---------------------------------------------------------------- organise

    suspend fun merge(context: Context, sources: List<PdfSource>, outName: String, progress: Progress): List<ResultFile> =
        withContext(Dispatchers.IO) {
            PdfTools.ensureInit(context)
            val result = newResult(context, outName, "application/pdf")
            progress(0, sources.size)
            val merger = PDFMergerUtility()
            sources.forEach { merger.addSource(it.file) }
            merger.destinationFileName = result.file.absolutePath
            merger.mergeDocuments(memory(context))
            progress(sources.size, sources.size)
            listOf(result)
        }

    suspend fun extractPages(
        context: Context,
        src: PdfSource,
        groups: List<PageGroup>,
        progress: Progress,
    ): List<ResultFile> = withContext(Dispatchers.IO) {
        val base = baseName(src.name)
        val results = ArrayList<ResultFile>()
        openDoc(context, src.file).use { doc ->
            groups.forEachIndexed { gi, group ->
                currentCoroutineContext().ensureActive()
                progress(gi, groups.size)
                val out = newResult(context, "${base}_${group.suffix}.pdf", "application/pdf")
                PDDocument().use { nd ->
                    group.pages.forEach { nd.importPage(doc.getPage(it)) }
                    save(nd, out.file)
                }
                results.add(out)
            }
        }
        progress(groups.size, groups.size)
        results
    }

    /** Keeps exactly the pages in [order], in that order. Used for rearranging and deleting. */
    suspend fun reorder(context: Context, src: PdfSource, order: List<Int>, suffix: String, progress: Progress): List<ResultFile> =
        withContext(Dispatchers.IO) {
            val out = newResult(context, "${baseName(src.name)}_$suffix.pdf", "application/pdf")
            openDoc(context, src.file).use { doc ->
                val n = doc.numberOfPages
                val pages = (0 until n).map { doc.getPage(it) }
                for (i in n - 1 downTo 0) doc.removePage(i)
                order.forEachIndexed { i, idx ->
                    currentCoroutineContext().ensureActive()
                    doc.addPage(pages[idx])
                    progress(i, order.size)
                }
                save(doc, out.file)
            }
            progress(order.size, order.size)
            listOf(out)
        }

    suspend fun rotate(context: Context, src: PdfSource, rotations: Map<Int, Int>, progress: Progress): List<ResultFile> =
        withContext(Dispatchers.IO) {
            val out = newResult(context, "${baseName(src.name)}_rotated.pdf", "application/pdf")
            openDoc(context, src.file).use { doc ->
                val n = doc.numberOfPages
                for (i in 0 until n) {
                    currentCoroutineContext().ensureActive()
                    val deg = rotations[i] ?: 0
                    if (deg != 0) {
                        val page = doc.getPage(i)
                        page.rotation = (((page.rotation + deg) % 360) + 360) % 360
                    }
                    progress(i, n)
                }
                save(doc, out.file)
            }
            progress(1, 1)
            listOf(out)
        }

    // ---------------------------------------------------------------- bookmarks

    suspend fun readBookmarks(context: Context, src: PdfSource): List<Bookmark> = withContext(Dispatchers.IO) {
        val list = ArrayList<Bookmark>()
        openDoc(context, src.file).use { doc ->
            val outline = doc.documentCatalog.documentOutline ?: return@use
            fun walk(node: PDOutlineNode, level: Int) {
                var item: PDOutlineItem? = node.firstChild
                while (item != null) {
                    val page = runCatching { item!!.findDestinationPage(doc) }.getOrNull()
                    val idx = if (page != null) doc.pages.indexOf(page) else -1
                    list.add(Bookmark(item.title ?: "", max(idx, 0), level))
                    walk(item, level + 1)
                    item = item.nextSibling
                }
            }
            walk(outline, 0)
        }
        list
    }

    suspend fun writeBookmarks(context: Context, src: PdfSource, bookmarks: List<Bookmark>): List<ResultFile> =
        withContext(Dispatchers.IO) {
            val out = newResult(context, "${baseName(src.name)}_bookmarks.pdf", "application/pdf")
            openDoc(context, src.file).use { doc ->
                if (bookmarks.isEmpty()) {
                    doc.documentCatalog.documentOutline = null
                } else {
                    val outline = PDDocumentOutline()
                    val stack = ArrayList<PDOutlineNode>()
                    stack.add(outline)
                    for (b in bookmarks) {
                        val level = b.level.coerceIn(0, stack.size - 1)
                        while (stack.size > level + 1) stack.removeAt(stack.lastIndex)
                        val item = PDOutlineItem()
                        item.title = b.title
                        val dest = PDPageFitWidthDestination()
                        dest.page = doc.getPage(b.page.coerceIn(0, doc.numberOfPages - 1))
                        item.destination = dest
                        stack[stack.lastIndex].addLast(item)
                        stack.add(item)
                    }
                    doc.documentCatalog.documentOutline = outline
                }
                save(doc, out.file)
            }
            listOf(out)
        }

    // ---------------------------------------------------------------- security

    suspend fun protect(
        context: Context,
        src: PdfSource,
        userPassword: String,
        ownerPassword: String,
        allowPrint: Boolean,
        allowCopy: Boolean,
        allowModify: Boolean,
    ): List<ResultFile> = withContext(Dispatchers.IO) {
        val out = newResult(context, "${baseName(src.name)}_protected.pdf", "application/pdf")
        openDoc(context, src.file).use { doc ->
            val ap = AccessPermission()
            ap.setCanPrint(allowPrint)
            ap.setCanPrintDegraded(allowPrint)
            ap.setCanExtractContent(allowCopy)
            ap.setCanExtractForAccessibility(true)
            ap.setCanModify(allowModify)
            ap.setCanModifyAnnotations(allowModify)
            ap.setCanFillInForm(allowModify)
            ap.setCanAssembleDocument(allowModify)
            val owner = ownerPassword.ifEmpty { userPassword }
            val policy = StandardProtectionPolicy(owner, userPassword, ap)
            policy.encryptionKeyLength = 128
            doc.protect(policy)
            save(doc, out.file)
        }
        listOf(out)
    }

    /** The working copy of a protected file is already decrypted, so unlocking is saving that copy. */
    suspend fun unlock(context: Context, src: PdfSource): List<ResultFile> = withContext(Dispatchers.IO) {
        val out = newResult(context, "${baseName(src.name)}_unlocked.pdf", "application/pdf")
        src.file.copyTo(out.file, overwrite = true)
        listOf(out)
    }

    // ---------------------------------------------------------------- optimise

    suspend fun compress(context: Context, src: PdfSource, level: CompressLevel, progress: Progress): List<ResultFile> =
        withContext(Dispatchers.IO) {
            val out = newResult(context, "${baseName(src.name)}_compressed.pdf", "application/pdf")
            openDoc(context, src.file).use { doc ->
                val done: MutableSet<Any> = Collections.newSetFromMap(IdentityHashMap())
                val total = doc.numberOfPages

                fun processResources(resources: PDResources?, depth: Int) {
                    if (resources == null || depth > 6) return
                    for (name in resources.xObjectNames.toList()) {
                        try {
                            val x = resources.getXObject(name)
                            if (x is PDImageXObject) {
                                if (!done.add(x.cosObject)) continue
                                if (x.isStencil || x.softMask != null) continue
                                if (x.width.toLong() * x.height > 40_000_000L) continue
                                var bmp = x.image ?: continue
                                if (level.downscale < 1f) {
                                    val w = (bmp.width * level.downscale).toInt().coerceAtLeast(1)
                                    val h = (bmp.height * level.downscale).toInt().coerceAtLeast(1)
                                    val scaled = Bitmap.createScaledBitmap(bmp, w, h, true)
                                    if (scaled !== bmp) bmp.recycle()
                                    bmp = scaled
                                }
                                val flat = flattenOnWhite(bmp)
                                if (flat !== bmp) bmp.recycle()
                                val newImage = JPEGFactory.createFromImage(doc, flat, level.jpegQuality)
                                resources.put(name, newImage)
                                flat.recycle()
                            } else if (x is PDFormXObject) {
                                if (done.add(x.cosObject)) processResources(x.resources, depth + 1)
                            }
                        } catch (e: Exception) {
                            // leave this image as it is
                        } catch (e: OutOfMemoryError) {
                            // leave this image as it is
                        }
                    }
                }

                for (i in 0 until total) {
                    currentCoroutineContext().ensureActive()
                    progress(i, total)
                    processResources(doc.getPage(i).resources, 0)
                }
                save(doc, out.file)
            }
            progress(1, 1)
            listOf(out)
        }

    /**
     * Rebuilds the document from rendered pages, in colour or grayscale. The text is not selectable
     * afterwards. Needs a file the system PDF renderer can open.
     */
    suspend fun rasterize(
        context: Context,
        src: PdfSource,
        gray: Boolean,
        scale: Float,
        jpegQuality: Float,
        suffix: String,
        progress: Progress,
    ): List<ResultFile> = withContext(Dispatchers.IO) {
        PdfTools.ensureInit(context)
        val out = newResult(context, "${baseName(src.name)}_$suffix.pdf", "application/pdf")
        ParcelFileDescriptor.open(src.file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            val renderer = PdfRenderer(fd)
            try {
                PDDocument().use { target ->
                    val total = renderer.pageCount
                    for (i in 0 until total) {
                        currentCoroutineContext().ensureActive()
                        progress(i, total)
                        renderer.openPage(i).use { page ->
                            val bmp = renderPdfPage(page, scale)
                            val finalBmp = if (gray) toGray(bmp).also { bmp.recycle() } else bmp
                            val img = JPEGFactory.createFromImage(target, finalBmp, jpegQuality)
                            val pdPage = PDPage(PDRectangle(page.width.toFloat(), page.height.toFloat()))
                            target.addPage(pdPage)
                            PDPageContentStream(target, pdPage).use { cs ->
                                cs.drawImage(img, 0f, 0f, page.width.toFloat(), page.height.toFloat())
                            }
                            finalBmp.recycle()
                        }
                    }
                    save(target, out.file)
                }
            } finally {
                runCatching { renderer.close() }
            }
        }
        progress(1, 1)
        listOf(out)
    }

    /** Re-reads and re-writes the file (PdfBox rebuilds a broken cross reference table). Falls back to rendering. */
    suspend fun repair(context: Context, src: PdfSource, progress: Progress): List<ResultFile> =
        withContext(Dispatchers.IO) {
            val out = newResult(context, "${baseName(src.name)}_repaired.pdf", "application/pdf")
            try {
                openDoc(context, src.file).use { doc ->
                    doc.isAllSecurityToBeRemoved = true
                    if (doc.numberOfPages == 0) throw java.io.IOException("empty")
                    save(doc, out.file)
                }
                listOf(out)
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                out.file.delete()
                rasterize(context, src, false, 1.5f, 0.85f, "repaired", progress)
            }
        }

    // ---------------------------------------------------------------- edit

    /** Maps "displayed page" coordinates (origin bottom left, as rendered) to user space. Returns the displayed size. */
    private fun PDPageContentStream.enterDisplayed(page: PDPage): Pair<Float, Float> {
        val box = page.mediaBox
        val w = box.width
        val h = box.height
        val llx = box.lowerLeftX
        val lly = box.lowerLeftY
        val rot = ((page.rotation % 360) + 360) % 360
        return when (rot) {
            90 -> { transform(Matrix(0f, 1f, -1f, 0f, w + llx, lly)); Pair(h, w) }
            180 -> { transform(Matrix(-1f, 0f, 0f, -1f, w + llx, h + lly)); Pair(w, h) }
            270 -> { transform(Matrix(0f, -1f, 1f, 0f, llx, h + lly)); Pair(h, w) }
            else -> { transform(Matrix(1f, 0f, 0f, 1f, llx, lly)); Pair(w, h) }
        }
    }

    /** Rotation by [clockwiseDegrees] around (cx, cy) in the displayed page space. */
    private fun rotationAround(cx: Float, cy: Float, clockwiseDegrees: Float): Matrix {
        val t = Math.toRadians(-clockwiseDegrees.toDouble())
        val c = cos(t).toFloat()
        val s = sin(t).toFloat()
        return Matrix(c, s, -s, c, cx - c * cx + s * cy, cy - s * cx - c * cy)
    }

    suspend fun watermark(
        context: Context,
        src: PdfSource,
        mark: Bitmap,
        widthFraction: Float,
        rotation: Float,
        tiled: Boolean,
        progress: Progress,
    ): List<ResultFile> = withContext(Dispatchers.IO) {
        val out = newResult(context, "${baseName(src.name)}_watermarked.pdf", "application/pdf")
        openDoc(context, src.file).use { doc ->
            val image = LosslessFactory.createFromImage(doc, mark)
            val total = doc.numberOfPages
            for (i in 0 until total) {
                currentCoroutineContext().ensureActive()
                progress(i, total)
                val page = doc.getPage(i)
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    cs.saveGraphicsState()
                    val (dw, dh) = cs.enterDisplayed(page)
                    val cx = dw / 2f
                    val cy = dh / 2f
                    cs.transform(rotationAround(cx, cy, rotation))
                    val w = dw * widthFraction
                    val h = w * mark.height / mark.width
                    if (!tiled) {
                        cs.drawImage(image, cx - w / 2, cy - h / 2, w, h)
                    } else {
                        val stepX = w * 1.6f
                        val stepY = h * 3f
                        val diag = Math.hypot(dw.toDouble(), dh.toDouble()).toFloat()
                        val cols = (diag / stepX).toInt() / 2 + 1
                        val rows = (diag / stepY).toInt() / 2 + 1
                        var count = 0
                        for (r in -rows..rows) for (c in -cols..cols) {
                            if (count++ > 600) break
                            val ox = c * stepX + if (abs(r) % 2 == 1) stepX / 2 else 0f
                            cs.drawImage(image, cx + ox - w / 2, cy + r * stepY - h / 2, w, h)
                        }
                    }
                    cs.restoreGraphicsState()
                }
            }
            save(doc, out.file)
        }
        progress(1, 1)
        listOf(out)
    }

    suspend fun pageNumbers(
        context: Context,
        src: PdfSource,
        format: String,
        position: NumberPosition,
        fontSize: Float,
        color: Int,
        startNumber: Int,
        skipFirst: Int,
        progress: Progress,
    ): List<ResultFile> = withContext(Dispatchers.IO) {
        val out = newResult(context, "${baseName(src.name)}_numbered.pdf", "application/pdf")
        openDoc(context, src.file).use { doc ->
            val font = PDType1Font.HELVETICA
            val total = doc.numberOfPages
            val margin = 28f
            for (i in 0 until total) {
                currentCoroutineContext().ensureActive()
                progress(i, total)
                if (i < skipFirst) continue
                val page = doc.getPage(i)
                val number = startNumber + i - skipFirst
                val text = format.replace("{n}", number.toString()).replace("{total}", (startNumber + total - skipFirst - 1).toString())
                val canEncode = runCatching { font.encode(text) }.isSuccess
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    cs.saveGraphicsState()
                    val (dw, dh) = cs.enterDisplayed(page)
                    if (canEncode) {
                        val tw = font.getStringWidth(text) / 1000f * fontSize
                        val x = when (position) {
                            NumberPosition.TOP_LEFT, NumberPosition.BOTTOM_LEFT -> margin
                            NumberPosition.TOP_CENTER, NumberPosition.BOTTOM_CENTER -> (dw - tw) / 2
                            else -> dw - tw - margin
                        }
                        val y = when (position) {
                            NumberPosition.TOP_LEFT, NumberPosition.TOP_CENTER, NumberPosition.TOP_RIGHT -> dh - margin - fontSize
                            else -> margin
                        }
                        cs.beginText()
                        cs.setFont(font, fontSize)
                        cs.setNonStrokingColor(Color.red(color), Color.green(color), Color.blue(color))
                        cs.newLineAtOffset(x, y)
                        cs.showText(text)
                        cs.endText()
                    } else {
                        // characters the standard font cannot show (other scripts): draw the text as an image
                        val bmp = textBitmap(text, color, 255, 3f)
                        val h = fontSize * 1.3f
                        val w = h * bmp.width / bmp.height
                        val x = when (position) {
                            NumberPosition.TOP_LEFT, NumberPosition.BOTTOM_LEFT -> margin
                            NumberPosition.TOP_CENTER, NumberPosition.BOTTOM_CENTER -> (dw - w) / 2
                            else -> dw - w - margin
                        }
                        val y = when (position) {
                            NumberPosition.TOP_LEFT, NumberPosition.TOP_CENTER, NumberPosition.TOP_RIGHT -> dh - margin - h
                            else -> margin
                        }
                        cs.drawImage(LosslessFactory.createFromImage(doc, bmp), x, y, w, h)
                        bmp.recycle()
                    }
                    cs.restoreGraphicsState()
                }
            }
            save(doc, out.file)
        }
        progress(1, 1)
        listOf(out)
    }

    suspend fun sign(context: Context, src: PdfSource, placements: List<Placement>, progress: Progress): List<ResultFile> =
        withContext(Dispatchers.IO) {
            val out = newResult(context, "${baseName(src.name)}_signed.pdf", "application/pdf")
            openDoc(context, src.file).use { doc ->
                val total = doc.numberOfPages
                val images = placements.map { LosslessFactory.createFromImage(doc, it.bitmap) }
                for (i in 0 until total) {
                    currentCoroutineContext().ensureActive()
                    progress(i, total)
                    val here = placements.withIndex().filter { it.value.page == i || it.value.page == -1 }
                    if (here.isEmpty()) continue
                    val page = doc.getPage(i)
                    PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                        for ((idx, p) in here) {
                            cs.saveGraphicsState()
                            val (dw, dh) = cs.enterDisplayed(page)
                            val w = dw * p.width
                            val h = w * p.bitmap.height / p.bitmap.width
                            val cx = p.x * dw
                            val cy = dh - p.y * dh
                            cs.transform(rotationAround(cx, cy, p.rotation))
                            cs.drawImage(images[idx], cx - w / 2, cy - h / 2, w, h)
                            cs.restoreGraphicsState()
                        }
                    }
                }
                save(doc, out.file)
            }
            progress(1, 1)
            listOf(out)
        }

    suspend fun readMetadata(context: Context, src: PdfSource): PdfMeta = withContext(Dispatchers.IO) {
        openDoc(context, src.file).use { doc ->
            val info = doc.documentInformation
            val fmt = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            PdfMeta(
                info.title ?: "", info.author ?: "", info.subject ?: "", info.keywords ?: "",
                info.creator ?: "", info.producer ?: "",
                info.creationDate?.let { fmt.format(it.time) } ?: "",
                info.modificationDate?.let { fmt.format(it.time) } ?: "",
                doc.version, doc.numberOfPages,
            )
        }
    }

    suspend fun writeMetadata(
        context: Context,
        src: PdfSource,
        title: String,
        author: String,
        subject: String,
        keywords: String,
        creator: String,
        producer: String,
        clearAll: Boolean,
    ): List<ResultFile> = withContext(Dispatchers.IO) {
        val out = newResult(context, "${baseName(src.name)}_metadata.pdf", "application/pdf")
        openDoc(context, src.file).use { doc ->
            if (clearAll) {
                doc.documentInformation = PDDocumentInformation()
                doc.documentCatalog.metadata = null
            } else {
                val info = doc.documentInformation
                info.title = title.ifBlank { null }
                info.author = author.ifBlank { null }
                info.subject = subject.ifBlank { null }
                info.keywords = keywords.ifBlank { null }
                info.creator = creator.ifBlank { null }
                info.producer = producer.ifBlank { null }
                info.modificationDate = Calendar.getInstance()
            }
            save(doc, out.file)
        }
        listOf(out)
    }

    // ---------------------------------------------------------------- convert

    suspend fun imagesToPdf(
        context: Context,
        images: List<ImageItem>,
        mode: PageSizeMode,
        jpegQuality: Float,
        marginPoints: Float,
        outName: String,
        progress: Progress,
    ): List<ResultFile> = withContext(Dispatchers.IO) {
        PdfTools.ensureInit(context)
        val out = newResult(context, outName, "application/pdf")
        PDDocument().use { doc ->
            var added = 0
            images.forEachIndexed { index, item ->
                currentCoroutineContext().ensureActive()
                progress(index, images.size)
                val bmp = decodeImage(context, item.uri, 3000) ?: return@forEachIndexed
                try {
                    val pdImage = JPEGFactory.createFromImage(doc, bmp, jpegQuality)
                    val iw = bmp.width.toFloat()
                    val ih = bmp.height.toFloat()
                    val rect = when (mode) {
                        PageSizeMode.FIT_IMAGE -> PDRectangle(iw * 72f / 150f + 2 * marginPoints, ih * 72f / 150f + 2 * marginPoints)
                        PageSizeMode.A4, PageSizeMode.LETTER -> {
                            val base = if (mode == PageSizeMode.A4) PDRectangle.A4 else PDRectangle.LETTER
                            if (iw > ih) PDRectangle(base.height, base.width) else PDRectangle(base.width, base.height)
                        }
                    }
                    val page = PDPage(rect)
                    doc.addPage(page)
                    val availW = rect.width - 2 * marginPoints
                    val availH = rect.height - 2 * marginPoints
                    val sc = min(availW / iw, availH / ih)
                    val dw = iw * sc
                    val dh = ih * sc
                    PDPageContentStream(doc, page).use { cs ->
                        cs.drawImage(pdImage, (rect.width - dw) / 2, (rect.height - dh) / 2, dw, dh)
                    }
                    added++
                } finally {
                    bmp.recycle()
                }
            }
            if (added == 0) throw java.io.IOException("no image could be read")
            save(doc, out.file)
        }
        progress(images.size, images.size)
        listOf(out)
    }

    suspend fun pdfToImages(
        context: Context,
        src: PdfSource,
        pages: List<Int>,
        format: ImageFormat,
        scale: Float,
        asZip: Boolean,
        progress: Progress,
    ): List<ResultFile> = withContext(Dispatchers.IO) {
        val base = baseName(src.name)
        val results = ArrayList<ResultFile>()
        val zip = if (asZip) newResult(context, "${base}_images.zip", "application/zip") else null
        val zos: ZipOutputStream? = zip?.let { ZipOutputStream(BufferedOutputStream(FileOutputStream(it.file))) }
        zos?.setLevel(Deflater.BEST_SPEED)
        try {
            ParcelFileDescriptor.open(src.file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                val renderer = PdfRenderer(fd)
                try {
                    pages.forEachIndexed { n, index ->
                        currentCoroutineContext().ensureActive()
                        progress(n, pages.size)
                        renderer.openPage(index).use { page ->
                            val bmp = renderPdfPage(page, scale)
                            val name = "${base}_page_${(index + 1).toString().padStart(3, '0')}.${format.ext}"
                            if (zos != null) {
                                zos.putNextEntry(ZipEntry(name))
                                compress(bmp, format, zos)
                                zos.closeEntry()
                            } else {
                                val r = newResult(context, name, format.mime)
                                FileOutputStream(r.file).use { compress(bmp, format, it) }
                                results.add(r)
                            }
                            bmp.recycle()
                        }
                    }
                } finally {
                    runCatching { renderer.close() }
                }
            }
            zos?.finish()
        } finally {
            runCatching { zos?.close() }
        }
        progress(pages.size, pages.size)
        if (zip != null) listOf(zip) else results
    }

    private fun compress(bmp: Bitmap, format: ImageFormat, out: OutputStream) {
        val cf = when (format) {
            ImageFormat.PNG -> Bitmap.CompressFormat.PNG
            ImageFormat.JPEG -> Bitmap.CompressFormat.JPEG
            ImageFormat.WEBP -> if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSLESS else Bitmap.CompressFormat.WEBP
        }
        bmp.compress(cf, if (format == ImageFormat.JPEG) 92 else 100, out)
    }

    private fun walkImages(
        doc: PDDocument,
        onImage: (page: Int, image: PDImageXObject) -> Unit,
    ) {
        val seen: MutableSet<Any> = Collections.newSetFromMap(IdentityHashMap())
        fun walk(resources: PDResources?, page: Int, depth: Int) {
            if (resources == null || depth > 6) return
            for (name in resources.xObjectNames.toList()) {
                try {
                    val x = resources.getXObject(name)
                    if (!seen.add(x.cosObject)) continue
                    if (x is PDImageXObject) onImage(page, x)
                    else if (x is PDFormXObject) walk(x.resources, page, depth + 1)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // skip unreadable objects
                }
            }
        }
        for (i in 0 until doc.numberOfPages) {
            walk(doc.getPage(i).resources, i, 0)
        }
    }

    suspend fun scanImages(context: Context, src: PdfSource): List<ImageInfo> = withContext(Dispatchers.IO) {
        val list = ArrayList<ImageInfo>()
        openDoc(context, src.file).use { doc ->
            walkImages(doc) { page, img ->
                list.add(ImageInfo(list.size, page, img.width, img.height, img.suffix ?: "png"))
            }
        }
        list
    }

    suspend fun extractImages(context: Context, src: PdfSource, selected: Set<Int>, progress: Progress): List<ResultFile> =
        withContext(Dispatchers.IO) {
            val base = baseName(src.name)
            val zip = newResult(context, "${base}_images.zip", "application/zip")
            val job = currentCoroutineContext()
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zip.file))).use { zos ->
                zos.setLevel(Deflater.BEST_SPEED)
                var index = 0
                var written = 0
                openDoc(context, src.file).use { doc ->
                    walkImages(doc) { page, img ->
                        val current = index++
                        if (current in selected) {
                            job.ensureActive()
                            val suffix = img.suffix ?: "png"
                            val stem = "${base}_p${page + 1}_${current + 1}"
                            try {
                                val passthrough = suffix == "jpg" && img.stream.filters.size == 1
                                if (passthrough) {
                                    zos.putNextEntry(ZipEntry("$stem.jpg"))
                                    img.cosObject.createRawInputStream().use { it.copyTo(zos) }
                                    zos.closeEntry()
                                } else {
                                    val bmp = img.image
                                    if (bmp != null) {
                                        zos.putNextEntry(ZipEntry("$stem.png"))
                                        bmp.compress(Bitmap.CompressFormat.PNG, 100, zos)
                                        zos.closeEntry()
                                        bmp.recycle()
                                    } else {
                                        zos.putNextEntry(ZipEntry("$stem.$suffix"))
                                        img.cosObject.createRawInputStream().use { it.copyTo(zos) }
                                        zos.closeEntry()
                                    }
                                }
                                written++
                            } catch (e: Exception) {
                                // skip this image
                            }
                            progress(written, selected.size)
                        }
                    }
                }
                if (written == 0) {
                    zos.putNextEntry(ZipEntry("empty.txt"))
                    zos.closeEntry()
                }
            }
            progress(selected.size, selected.size)
            listOf(zip)
        }

    suspend fun pdfToText(
        context: Context,
        src: PdfSource,
        pages: List<Int>,
        pageMarkers: Boolean,
        markerFormat: String,
        progress: Progress,
    ): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        openDoc(context, src.file).use { doc ->
            val stripper = PDFTextStripper()
            stripper.sortByPosition = true
            pages.forEachIndexed { n, index ->
                currentCoroutineContext().ensureActive()
                progress(n, pages.size)
                stripper.startPage = index + 1
                stripper.endPage = index + 1
                if (pageMarkers) sb.append(markerFormat.replace("{n}", (index + 1).toString())).append("\n")
                sb.append(stripper.getText(doc).trim()).append("\n\n")
            }
        }
        progress(pages.size, pages.size)
        sb.toString().trim()
    }

    suspend fun textToResult(context: Context, src: PdfSource, text: String): List<ResultFile> = withContext(Dispatchers.IO) {
        val out = newResult(context, "${baseName(src.name)}.txt", "text/plain")
        out.file.writeText(text, Charsets.UTF_8)
        listOf(out)
    }

    // ---------------------------------------------------------------- compare

    suspend fun compare(context: Context, a: PdfSource, b: PdfSource, progress: Progress): CompareResult =
        withContext(Dispatchers.IO) {
            val ha = pageTextHashes(context, a) { d, t -> progress(d, t * 2) }
            val hb = pageTextHashes(context, b) { d, t -> progress(t + d, t * 2) }
            val differing = ArrayList<Int>()
            for (i in 0 until max(ha.size, hb.size)) {
                if (i >= ha.size || i >= hb.size || ha[i] != hb[i]) differing.add(i)
            }
            CompareResult(ha.size, hb.size, differing)
        }

    private suspend fun pageTextHashes(context: Context, src: PdfSource, progress: Progress): IntArray {
        return openDoc(context, src.file).use { doc ->
            val n = doc.numberOfPages
            val stripper = PDFTextStripper()
            stripper.sortByPosition = true
            val result = IntArray(n)
            for (i in 0 until n) {
                currentCoroutineContext().ensureActive()
                progress(i, n)
                stripper.startPage = i + 1
                stripper.endPage = i + 1
                result[i] = stripper.getText(doc).replace(Regex("\\s+"), " ").trim().hashCode()
            }
            result
        }
    }

    // ---------------------------------------------------------------- bitmaps

    fun renderPdfPage(page: PdfRenderer.Page, scale: Float): Bitmap {
        val maxSide = 6000f
        val s = min(scale, maxSide / max(page.width, page.height))
        val w = (page.width * s).toInt().coerceAtLeast(1)
        val h = (page.height * s).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        return bmp
    }

    fun toGray(src: Bitmap): Bitmap {
        val dst = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dst)
        canvas.drawColor(Color.WHITE)
        val paint = Paint()
        paint.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        canvas.drawBitmap(src, 0f, 0f, paint)
        return dst
    }

    private fun flattenOnWhite(src: Bitmap): Bitmap {
        if (!src.hasAlpha()) return src
        val dst = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dst)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(src, 0f, 0f, null)
        return dst
    }

    /** A line of text as a transparent bitmap. [scale] pixels per unit of text size 20. */
    fun textBitmap(text: String, color: Int, alpha: Int, scale: Float): Bitmap {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 20f * scale
        paint.color = color
        paint.alpha = alpha.coerceIn(0, 255)
        val fm = paint.fontMetrics
        val pad = (4f * scale).toInt()
        val w = paint.measureText(text).toInt().coerceAtLeast(1) + 2 * pad
        val h = (fm.descent - fm.ascent).toInt().coerceAtLeast(1) + 2 * pad
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawText(text, pad.toFloat(), pad - fm.ascent, paint)
        return bmp
    }

    /** The picture for an image watermark, with the opacity applied. */
    fun imageMark(context: Context, uri: Uri, opacity: Float): Bitmap? {
        val src = decodeImage(context, uri, 1600, keepAlpha = true) ?: return null
        val dst = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        paint.alpha = (opacity * 255).toInt().coerceIn(0, 255)
        Canvas(dst).drawBitmap(src, 0f, 0f, paint)
        src.recycle()
        return dst
    }

    /** Decodes an image with an upper limit for its longer side and applies its EXIF orientation. */
    fun decodeImage(context: Context, uri: Uri, maxSide: Int, keepAlpha: Boolean = false): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return null
            val orientation = runCatching {
                context.contentResolver.openInputStream(uri)?.use {
                    ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                }
            }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
            val m = GfxMatrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
                else -> Unit
            }
            val rotated = if (m.isIdentity) decoded else {
                Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true).also {
                    if (it !== decoded) decoded.recycle()
                }
            }
            if (keepAlpha) rotated else flattenOnWhite(rotated).also { if (it !== rotated) rotated.recycle() }
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }
}
