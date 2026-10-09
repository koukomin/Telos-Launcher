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

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.base.R as BaseR
import java.io.File

/** What a tool needs as input. */
enum class ToolInput { ONE_PDF, MANY_PDF, TWO_PDF, IMAGES }

enum class ToolGroup(@StringRes val title: Int) {
    ORGANIZE(R.string.pdft_group_organize),
    OPTIMIZE(R.string.pdft_group_optimize),
    SECURITY(R.string.pdft_group_security),
    EDIT(R.string.pdft_group_edit),
    CONVERT(R.string.pdft_group_convert),
    VIEW(R.string.pdft_group_view),
}

enum class PdfTool(
    val key: String,
    val group: ToolGroup,
    val input: ToolInput,
    @StringRes val title: Int,
    @StringRes val description: Int,
    @DrawableRes val icon: Int,
) {
    MERGE("merge", ToolGroup.ORGANIZE, ToolInput.MANY_PDF, R.string.pdft_tool_merge, R.string.pdft_tool_merge_desc, BaseR.drawable.attach_file_24px),
    SPLIT("split", ToolGroup.ORGANIZE, ToolInput.ONE_PDF, R.string.pdft_tool_split, R.string.pdft_tool_split_desc, BaseR.drawable.content_cut_24px),
    ROTATE("rotate", ToolGroup.ORGANIZE, ToolInput.ONE_PDF, R.string.pdft_tool_rotate, R.string.pdft_tool_rotate_desc, BaseR.drawable.autorenew_24px),
    REARRANGE("rearrange", ToolGroup.ORGANIZE, ToolInput.ONE_PDF, R.string.pdft_tool_rearrange, R.string.pdft_tool_rearrange_desc, BaseR.drawable.swap_horiz_24px),
    DELETE("delete", ToolGroup.ORGANIZE, ToolInput.ONE_PDF, R.string.pdft_tool_delete, R.string.pdft_tool_delete_desc, BaseR.drawable.delete_24px),
    BOOKMARKS("bookmarks", ToolGroup.ORGANIZE, ToolInput.ONE_PDF, R.string.pdft_tool_bookmarks, R.string.pdft_tool_bookmarks_desc, BaseR.drawable.label_24px),

    COMPRESS("compress", ToolGroup.OPTIMIZE, ToolInput.ONE_PDF, R.string.pdft_tool_compress, R.string.pdft_tool_compress_desc, BaseR.drawable.archive_24px),
    GRAYSCALE("grayscale", ToolGroup.OPTIMIZE, ToolInput.ONE_PDF, R.string.pdft_tool_grayscale, R.string.pdft_tool_grayscale_desc, BaseR.drawable.palette_24px),
    REPAIR("repair", ToolGroup.OPTIMIZE, ToolInput.ONE_PDF, R.string.pdft_tool_repair, R.string.pdft_tool_repair_desc, BaseR.drawable.handyman_24px),

    PROTECT("protect", ToolGroup.SECURITY, ToolInput.ONE_PDF, R.string.pdft_tool_protect, R.string.pdft_tool_protect_desc, BaseR.drawable.lock_24px),
    UNLOCK("unlock", ToolGroup.SECURITY, ToolInput.ONE_PDF, R.string.pdft_tool_unlock, R.string.pdft_tool_unlock_desc, BaseR.drawable.encrypted_24px),

    WATERMARK("watermark", ToolGroup.EDIT, ToolInput.ONE_PDF, R.string.pdft_tool_watermark, R.string.pdft_tool_watermark_desc, BaseR.drawable.opacity_24px),
    PAGE_NUMBERS("page_numbers", ToolGroup.EDIT, ToolInput.ONE_PDF, R.string.pdft_tool_page_numbers, R.string.pdft_tool_page_numbers_desc, BaseR.drawable._123_24px),
    SIGN("sign", ToolGroup.EDIT, ToolInput.ONE_PDF, R.string.pdft_tool_sign, R.string.pdft_tool_sign_desc, BaseR.drawable.gesture_24px),
    METADATA("metadata", ToolGroup.EDIT, ToolInput.ONE_PDF, R.string.pdft_tool_metadata, R.string.pdft_tool_metadata_desc, BaseR.drawable.info_24px),

    IMAGES_TO_PDF("images_to_pdf", ToolGroup.CONVERT, ToolInput.IMAGES, R.string.pdft_tool_images_to_pdf, R.string.pdft_tool_images_to_pdf_desc, BaseR.drawable.photo_24px),
    PDF_TO_IMAGES("pdf_to_images", ToolGroup.CONVERT, ToolInput.ONE_PDF, R.string.pdft_tool_pdf_to_images, R.string.pdft_tool_pdf_to_images_desc, BaseR.drawable.wallpaper_24px),
    EXTRACT_IMAGES("extract_images", ToolGroup.CONVERT, ToolInput.ONE_PDF, R.string.pdft_tool_extract_images, R.string.pdft_tool_extract_images_desc, BaseR.drawable.image_search_24px),
    PDF_TO_TEXT("pdf_to_text", ToolGroup.CONVERT, ToolInput.ONE_PDF, R.string.pdft_tool_pdf_to_text, R.string.pdft_tool_pdf_to_text_desc, BaseR.drawable.text_fields_24px),

    PREVIEW("preview", ToolGroup.VIEW, ToolInput.ONE_PDF, R.string.pdft_tool_preview, R.string.pdft_tool_preview_desc, BaseR.drawable.visibility_24px),
    COMPARE("compare", ToolGroup.VIEW, ToolInput.TWO_PDF, R.string.pdft_tool_compare, R.string.pdft_tool_compare_desc, BaseR.drawable.swap_vert_24px);

    companion object {
        fun fromKey(key: String?): PdfTool? = entries.firstOrNull { it.key == key }
    }
}

/** A PDF that has been copied to the cache (and decrypted, if it was protected). */
data class PdfSource(
    val id: Long,
    val name: String,
    val file: File,
    val pageCount: Int,
    val sizeBytes: Long,
    /** The file was password protected when it was added. */
    val wasEncrypted: Boolean = false,
    /** The file could not be parsed. Only the repair tool accepts such files. */
    val broken: Boolean = false,
)

/** A result file living in the cache until the user saves it. */
data class ResultFile(val file: File, val name: String, val mime: String) {
    val size: Long get() = file.length()
}

class ImageItem(val id: Long, val uri: Uri, val name: String)

/**
 * Entry points of the PDF tools.
 *
 * Start [PdfToolsActivity] with [createIntent] / [start]. The intent data is the (optional)
 * content or file Uri of a PDF, the optional extra [EXTRA_NAME] is its display name.
 */
object PdfTools {
    const val ACTION_PDF_TOOLS = "de.mm20.launcher2.ui.media.docs.pdf.action.PDF_TOOLS"
    const val EXTRA_NAME = "name"

    /** Optional: key of a [PdfTool] to open straight away. */
    const val EXTRA_TOOL = "tool"

    fun createIntent(context: Context, uri: Uri? = null, name: String? = null, tool: PdfTool? = null): Intent {
        return Intent(ACTION_PDF_TOOLS).apply {
            setClass(context, PdfToolsActivity::class.java)
            if (uri != null) {
                data = uri
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            if (name != null) putExtra(EXTRA_NAME, name)
            if (tool != null) putExtra(EXTRA_TOOL, tool.key)
        }
    }

    fun start(context: Context, uri: Uri? = null, name: String? = null, tool: PdfTool? = null) {
        val intent = createIntent(context, uri, name, tool)
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private var initialized = false

    /** Initialises PdfBox-Android (fonts, glyph lists) once. */
    @Synchronized
    fun ensureInit(context: Context) {
        if (initialized) return
        PDFBoxResourceLoader.init(context.applicationContext)
        initialized = true
    }
}
