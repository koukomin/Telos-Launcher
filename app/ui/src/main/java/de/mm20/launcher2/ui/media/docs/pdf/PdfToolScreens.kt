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

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.base.R as BaseR
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

/** Chooses the screen of a tool. */
@Composable
fun ToolRouter(vm: PdfToolsViewModel, tool: PdfTool, snackbar: SnackbarHostState) {
    when (tool) {
        PdfTool.MERGE -> MergeScreen(vm, snackbar)
        PdfTool.SPLIT -> SinglePdfTool(vm, tool, snackbar) { SplitBody(vm, it) }
        PdfTool.ROTATE -> SinglePdfTool(vm, tool, snackbar) { RotateBody(vm, it) }
        PdfTool.REARRANGE -> SinglePdfTool(vm, tool, snackbar) { RearrangeBody(vm, it) }
        PdfTool.DELETE -> SinglePdfTool(vm, tool, snackbar) { DeleteBody(vm, it) }
        PdfTool.BOOKMARKS -> SinglePdfTool(vm, tool, snackbar) { BookmarksBody(vm, it) }
        PdfTool.COMPRESS -> SinglePdfTool(vm, tool, snackbar) { CompressBody(vm, it) }
        PdfTool.GRAYSCALE -> SinglePdfTool(vm, tool, snackbar) { GrayscaleBody(vm, it) }
        PdfTool.REPAIR -> SinglePdfTool(vm, tool, snackbar, allowBroken = true) { RepairBody(vm, it) }
        PdfTool.PROTECT -> SinglePdfTool(vm, tool, snackbar) { ProtectBody(vm, it) }
        PdfTool.UNLOCK -> SinglePdfTool(vm, tool, snackbar) { UnlockBody(vm, it) }
        PdfTool.WATERMARK -> SinglePdfTool(vm, tool, snackbar) { WatermarkBody(vm, it) }
        PdfTool.PAGE_NUMBERS -> SinglePdfTool(vm, tool, snackbar) { PageNumbersBody(vm, it) }
        PdfTool.SIGN -> SinglePdfTool(vm, tool, snackbar) { SignBody(vm, it) }
        PdfTool.METADATA -> SinglePdfTool(vm, tool, snackbar) { MetadataBody(vm, it) }
        PdfTool.IMAGES_TO_PDF -> ImagesToPdfScreen(vm, snackbar)
        PdfTool.PDF_TO_IMAGES -> SinglePdfTool(vm, tool, snackbar) { PdfToImagesBody(vm, it) }
        PdfTool.EXTRACT_IMAGES -> SinglePdfTool(vm, tool, snackbar) { ExtractImagesBody(vm, it) }
        PdfTool.PDF_TO_TEXT -> SinglePdfTool(vm, tool, snackbar) { PdfToTextBody(vm, it) }
        PdfTool.PREVIEW -> SinglePdfTool(vm, tool, snackbar) { PreviewBody(it) }
        PdfTool.COMPARE -> CompareScreen(vm, snackbar)
    }
}

/** Frame for tools that work on one PDF: file header with change button, then the tool's body. */
@Composable
fun SinglePdfTool(
    vm: PdfToolsViewModel,
    tool: PdfTool,
    snackbar: SnackbarHostState,
    allowBroken: Boolean = false,
    content: @Composable ColumnScope.(PdfSource) -> Unit,
) {
    val pick = rememberPdfPicker(multiple = false) { vm.addPdfs(it) }
    val src = vm.pdfs.firstOrNull { it.id == vm.selectedId && (allowBroken || !it.broken) }
        ?: vm.pdfs.lastOrNull { allowBroken || !it.broken }
    var previewPage by remember { mutableStateOf<Int?>(null) }
    ToolFrame(stringResource(tool.title), onBack = { vm.back {} }, snackbar = snackbar) { padding ->
        if (src == null) {
            Box(Modifier.padding(padding).fillMaxSize()) {
                EmptyPick(stringResource(R.string.pdft_pick_pdf_hint), stringResource(R.string.pdft_choose_pdf), pick)
            }
        } else {
            Column(Modifier.padding(padding).fillMaxSize()) {
                FileHeader(src, onChange = pick, onPreview = { previewPage = 0 })
                key(src.id) { content(src) }
            }
        }
    }
    PreviewHost(src, previewPage) { previewPage = null }
}

private fun parseRanges(text: String, max: Int): List<IntRange>? {
    val tokens = text.split(',', ';', ' ').map { it.trim() }.filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return null
    val result = ArrayList<IntRange>()
    for (t in tokens) {
        val parts = t.split('-')
        val a = parts[0].toIntOrNull() ?: return null
        val b = if (parts.size == 1) a else (parts.getOrNull(1)?.toIntOrNull() ?: return null)
        if (parts.size > 2 || a < 1 || b < a || b > max) return null
        result.add((a - 1)..(b - 1))
    }
    return result
}

private fun digits(s: String): String = s.filter { it.isDigit() }.take(6)

// ---------------------------------------------------------------- merge

@Composable
private fun MergeScreen(vm: PdfToolsViewModel, snackbar: SnackbarHostState) {
    val pick = rememberPdfPicker(multiple = true) { vm.addPdfs(it) }
    // the files stay in the order the user arranges them, new ones are added to the end
    val order = remember { mutableStateListOf<Long>() }
    val files = vm.pdfs.filter { !it.broken }
    LaunchedEffect(files.map { it.id }) {
        val ids = files.map { it.id }
        order.removeAll { it !in ids }
        ids.forEach { if (it !in order) order.add(it) }
    }
    val ordered = order.mapNotNull { id -> files.firstOrNull { it.id == id } }
    var name by remember { mutableStateOf("") }
    val defaultName = stringResource(R.string.pdft_default_name_merged)
    var previewing by remember { mutableStateOf<PdfSource?>(null) }

    ToolFrame(stringResource(PdfTool.MERGE.title), onBack = { vm.back {} }, snackbar = snackbar) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (ordered.isEmpty()) {
                Box(Modifier.weight(1f)) {
                    EmptyPick(stringResource(R.string.pdft_merge_hint), stringResource(R.string.pdft_add_pdf), pick)
                }
            } else {
                Text(
                    stringResource(R.string.pdft_merge_order_hint),
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(ordered, key = { _, s -> s.id }) { index, src ->
                        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            Row(Modifier.padding(start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    IconButton(onClick = {
                                        if (index > 0) { val i = order.indexOf(src.id); order.removeAt(i); order.add(i - 1, src.id) }
                                    }, enabled = index > 0) { Ico(BaseR.drawable.keyboard_arrow_up_24px, description = stringResource(R.string.pdft_move_up)) }
                                    IconButton(onClick = {
                                        if (index < ordered.lastIndex) { val i = order.indexOf(src.id); order.removeAt(i); order.add(i + 1, src.id) }
                                    }, enabled = index < ordered.lastIndex) { Ico(BaseR.drawable.keyboard_arrow_down_24px, description = stringResource(R.string.pdft_move_down)) }
                                }
                                Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                                    Text(src.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        stringResource(R.string.pdft_file_info, src.pageCount, formatBytes(LocalContext.current, src.sizeBytes)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { previewing = src }) { Ico(BaseR.drawable.visibility_24px, description = stringResource(R.string.pdft_preview)) }
                                IconButton(onClick = { vm.removePdf(src) }) { Ico(BaseR.drawable.close_24px, description = stringResource(R.string.pdft_remove)) }
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    singleLine = true,
                    label = { Text(stringResource(R.string.pdft_output_name)) },
                    placeholder = { Text(defaultName) },
                )
            }
            ActionBar(
                label = stringResource(R.string.pdft_merge_action, ordered.size),
                enabled = ordered.size >= 2,
                onClick = {
                    val list = ordered
                    val outName = (name.trim().ifEmpty { defaultName }).removeSuffix(".pdf") + ".pdf"
                    vm.run(PdfTool.MERGE, list.sumOf { it.sizeBytes }) { p -> PdfEngine.merge(vm.context, list, outName, p) }
                },
                secondary = { OutlinedButton(onClick = pick) { Text(stringResource(R.string.pdft_add)) } },
            )
        }
    }
    PreviewHost(previewing, if (previewing != null) 0 else null) { previewing = null }
}

// ---------------------------------------------------------------- split

private enum class SplitMode { RANGES, EVERY_N, EACH_PAGE, SELECTED }

@Composable
private fun ColumnScope.SplitBody(vm: PdfToolsViewModel, src: PdfSource) {
    var mode by rememberSaveable { mutableStateOf(SplitMode.RANGES) }
    var ranges by rememberSaveable { mutableStateOf("") }
    var everyN by rememberSaveable { mutableStateOf("1") }
    val selected = remember { mutableStateListOf<Int>() }
    val pages = rememberPdfPages(src.file)
    val parsed = if (mode == SplitMode.RANGES) parseRanges(ranges, src.pageCount) else null
    val n = everyN.toIntOrNull() ?: 0

    Column(Modifier.padding(horizontal = 16.dp)) {
        ChoiceRow(
            listOf(
                SplitMode.RANGES to stringResource(R.string.pdft_split_ranges),
                SplitMode.EVERY_N to stringResource(R.string.pdft_split_every),
                SplitMode.EACH_PAGE to stringResource(R.string.pdft_split_each),
                SplitMode.SELECTED to stringResource(R.string.pdft_split_selected),
            ),
            mode,
        ) { mode = it }
        when (mode) {
            SplitMode.RANGES -> OutlinedTextField(
                value = ranges,
                onValueChange = { ranges = it },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                singleLine = true,
                label = { Text(stringResource(R.string.pdft_split_ranges)) },
                supportingText = { Text(stringResource(R.string.pdft_split_ranges_hint, src.pageCount)) },
                isError = ranges.isNotEmpty() && parsed == null,
            )
            SplitMode.EVERY_N -> OutlinedTextField(
                value = everyN,
                onValueChange = { everyN = digits(it) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                singleLine = true,
                label = { Text(stringResource(R.string.pdft_split_every)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            SplitMode.EACH_PAGE -> Unit
            SplitMode.SELECTED -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.pdft_selected_count, selected.size), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { selected.clear(); selected.addAll(0 until src.pageCount) }) { Text(stringResource(R.string.pdft_select_all)) }
                TextButton(onClick = { selected.clear() }) { Text(stringResource(R.string.pdft_select_none)) }
            }
        }
    }
    Box(Modifier.weight(1f)) {
        if (mode == SplitMode.SELECTED && pages != null) {
            PageGrid(pages, (0 until src.pageCount).toList(), Modifier.fillMaxSize(), selected = selected.toSet()) { p ->
                if (p in selected) selected.remove(p) else selected.add(p)
            }
        }
    }
    val valid = when (mode) {
        SplitMode.RANGES -> parsed != null
        SplitMode.EVERY_N -> n in 1 until src.pageCount
        SplitMode.EACH_PAGE -> src.pageCount > 1
        SplitMode.SELECTED -> selected.isNotEmpty()
    }
    ActionBar(stringResource(R.string.pdft_apply), valid, onClick = {
        val groups: List<PageGroup> = when (mode) {
            SplitMode.RANGES -> parsed!!.map { r -> PageGroup(if (r.first == r.last) "p${r.first + 1}" else "p${r.first + 1}-${r.last + 1}", r.toList()) }
            SplitMode.EVERY_N -> (0 until src.pageCount).chunked(n).map { PageGroup("p${it.first() + 1}-${it.last() + 1}", it) }
            SplitMode.EACH_PAGE -> (0 until src.pageCount).map { PageGroup("p${it + 1}", listOf(it)) }
            SplitMode.SELECTED -> listOf(PageGroup("selection", selected.sorted()))
        }
        vm.run(PdfTool.SPLIT, src.sizeBytes) { p -> PdfEngine.extractPages(vm.context, src, groups, p) }
    })
}

// ---------------------------------------------------------------- rotate

@Composable
private fun ColumnScope.RotateBody(vm: PdfToolsViewModel, src: PdfSource) {
    val pages = rememberPdfPages(src.file)
    var rotations by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    fun add(page: Int, deg: Int) {
        rotations = rotations + (page to ((((rotations[page] ?: 0) + deg) % 360 + 360) % 360))
    }
    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        FilledTonalButton(onClick = { for (i in 0 until src.pageCount) add(i, -90) }) { Text(stringResource(R.string.pdft_rotate_all_left)) }
        FilledTonalButton(onClick = { for (i in 0 until src.pageCount) add(i, 90) }) { Text(stringResource(R.string.pdft_rotate_all_right)) }
        TextButton(onClick = { rotations = emptyMap() }) { Text(stringResource(R.string.pdft_reset)) }
    }
    Text(
        stringResource(R.string.pdft_rotate_hint),
        Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Box(Modifier.weight(1f)) {
        if (pages != null) {
            PageGrid(
                pages, (0 until src.pageCount).toList(), Modifier.fillMaxSize(),
                rotations = rotations,
                label = { p -> if ((rotations[p] ?: 0) != 0) "${p + 1}  (${rotations[p]}°)" else "${p + 1}" },
                onLongClick = { add(it, -90) },
            ) { add(it, 90) }
        }
    }
    val changed = rotations.values.count { it != 0 }
    ActionBar(stringResource(R.string.pdft_apply), changed > 0, onClick = {
        val map = rotations
        vm.run(PdfTool.ROTATE, src.sizeBytes) { p -> PdfEngine.rotate(vm.context, src, map, p) }
    })
}

// ---------------------------------------------------------------- rearrange

@Composable
private fun ColumnScope.RearrangeBody(vm: PdfToolsViewModel, src: PdfSource) {
    val pages = rememberPdfPages(src.file)
    val order = remember { mutableStateListOf<Int>().also { it.addAll(0 until src.pageCount) } }
    var current by remember { mutableStateOf<Int?>(null) }
    fun move(page: Int, newIndex: Int) {
        val i = order.indexOf(page)
        if (i < 0) return
        val target = newIndex.coerceIn(0, order.lastIndex)
        order.removeAt(i)
        order.add(target, page)
    }
    val pos = current?.let { order.indexOf(it) } ?: -1
    Text(
        stringResource(R.string.pdft_rearrange_hint),
        Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        FilledTonalButton(onClick = { current?.let { move(it, 0) } }, enabled = pos > 0) { Text(stringResource(R.string.pdft_to_start)) }
        IconButton(onClick = { current?.let { move(it, pos - 1) } }, enabled = pos > 0) { Ico(BaseR.drawable.arrow_back_24px, description = stringResource(R.string.pdft_move_earlier)) }
        IconButton(onClick = { current?.let { move(it, pos + 1) } }, enabled = pos in 0 until order.lastIndex) { Ico(BaseR.drawable.arrow_forward_24px, description = stringResource(R.string.pdft_move_later)) }
        FilledTonalButton(onClick = { current?.let { move(it, order.lastIndex) } }, enabled = pos in 0 until order.lastIndex) { Text(stringResource(R.string.pdft_to_end)) }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = { val r = order.reversed(); order.clear(); order.addAll(r) }) { Text(stringResource(R.string.pdft_reverse)) }
    }
    Box(Modifier.weight(1f)) {
        if (pages != null) {
            PageGrid(
                pages, order.toList(), Modifier.fillMaxSize(),
                selected = setOfNotNull(current),
                label = { p -> val at = order.indexOf(p) + 1; if (at == p + 1) "$at" else "$at  (${p + 1})" },
            ) { p -> current = if (current == p) null else p }
        }
    }
    val changed = order.toList() != (0 until src.pageCount).toList()
    ActionBar(stringResource(R.string.pdft_apply), changed, onClick = {
        val list = order.toList()
        vm.run(PdfTool.REARRANGE, src.sizeBytes) { p -> PdfEngine.reorder(vm.context, src, list, "rearranged", p) }
    })
}

// ---------------------------------------------------------------- delete

@Composable
private fun ColumnScope.DeleteBody(vm: PdfToolsViewModel, src: PdfSource) {
    val pages = rememberPdfPages(src.file)
    val selected = remember { mutableStateListOf<Int>() }
    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.pdft_selected_count, selected.size), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { selected.clear(); selected.addAll(0 until src.pageCount) }) { Text(stringResource(R.string.pdft_select_all)) }
        TextButton(onClick = { selected.clear() }) { Text(stringResource(R.string.pdft_select_none)) }
        TextButton(onClick = {
            val rest = (0 until src.pageCount).filter { it !in selected }
            selected.clear(); selected.addAll(rest)
        }) { Text(stringResource(R.string.pdft_select_invert)) }
    }
    Box(Modifier.weight(1f)) {
        if (pages != null) {
            PageGrid(pages, (0 until src.pageCount).toList(), Modifier.fillMaxSize(), selected = selected.toSet(), faded = selected.toSet()) { p ->
                if (p in selected) selected.remove(p) else selected.add(p)
            }
        }
    }
    ActionBar(
        stringResource(R.string.pdft_delete_action, selected.size),
        selected.isNotEmpty() && selected.size < src.pageCount,
        onClick = {
            val keep = (0 until src.pageCount).filter { it !in selected }
            vm.run(PdfTool.DELETE, src.sizeBytes) { p -> PdfEngine.reorder(vm.context, src, keep, "edited", p) }
        },
    )
}

// ---------------------------------------------------------------- bookmarks

private data class BmItem(val id: Long, val title: String, val page: Int, val level: Int)

@Composable
private fun ColumnScope.BookmarksBody(vm: PdfToolsViewModel, src: PdfSource) {
    val items = remember { mutableStateListOf<BmItem>() }
    var loaded by remember { mutableStateOf(false) }
    var previewPage by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(src.id) {
        val list = runCatching { PdfEngine.readBookmarks(vm.context, src) }.getOrDefault(emptyList())
        items.clear()
        list.forEach { items.add(BmItem(PdfEngine.nextId(), it.title, it.page + 1, it.level)) }
        loaded = true
    }
    fun update(index: Int, f: (BmItem) -> BmItem) { items[index] = f(items[index]) }
    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (loaded && items.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.pdft_bookmarks_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        itemsIndexed(items.toList(), key = { _, b -> b.id }) { index, b ->
            Surface(
                Modifier.padding(start = (b.level.coerceAtMost(5) * 16).dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Column(Modifier.padding(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = b.title,
                            onValueChange = { v -> update(index) { it.copy(title = v) } },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text(stringResource(R.string.pdft_bookmark_title)) },
                        )
                        OutlinedTextField(
                            value = if (b.page > 0) b.page.toString() else "",
                            onValueChange = { v ->
                                // an empty field is allowed while typing (page 0), Apply stays disabled until it is filled
                                val n = digits(v).toIntOrNull() ?: 0
                                update(index) { it.copy(page = n.coerceIn(0, src.pageCount)) }
                            },
                            isError = b.page < 1,
                            modifier = Modifier.width(84.dp),
                            singleLine = true,
                            label = { Text(stringResource(R.string.pdft_page)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { update(index) { it.copy(level = (it.level - 1).coerceAtLeast(0)) } }, enabled = b.level > 0) {
                            Ico(BaseR.drawable.arrow_back_24px, description = stringResource(R.string.pdft_outdent))
                        }
                        IconButton(onClick = { update(index) { it.copy(level = (it.level + 1).coerceAtMost(8)) } }) {
                            Ico(BaseR.drawable.arrow_forward_24px, description = stringResource(R.string.pdft_indent))
                        }
                        IconButton(onClick = { val x = items.removeAt(index); items.add(index - 1, x) }, enabled = index > 0) {
                            Ico(BaseR.drawable.keyboard_arrow_up_24px, description = stringResource(R.string.pdft_move_up))
                        }
                        IconButton(onClick = { val x = items.removeAt(index); items.add(index + 1, x) }, enabled = index < items.lastIndex) {
                            Ico(BaseR.drawable.keyboard_arrow_down_24px, description = stringResource(R.string.pdft_move_down))
                        }
                        IconButton(onClick = { previewPage = (b.page - 1).coerceAtLeast(0) }) {
                            Ico(BaseR.drawable.visibility_24px, description = stringResource(R.string.pdft_preview))
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { items.removeAt(index) }) {
                            Ico(BaseR.drawable.delete_24px, description = stringResource(R.string.pdft_remove))
                        }
                    }
                }
            }
        }
    }
    val addedDefault = stringResource(R.string.pdft_bookmark_new)
    ActionBar(
        stringResource(R.string.pdft_apply),
        loaded && items.none { it.title.isBlank() || it.page < 1 },
        onClick = {
            val list = items.map { Bookmark(it.title.trim(), it.page - 1, it.level) }
            vm.run(PdfTool.BOOKMARKS, src.sizeBytes) { p -> PdfEngine.writeBookmarks(vm.context, src, list).also { p(1, 1) } }
        },
        secondary = {
            OutlinedButton(onClick = { items.add(BmItem(PdfEngine.nextId(), addedDefault, 1, 0)) }) { Text(stringResource(R.string.pdft_add)) }
        },
    )
    PreviewHost(src, previewPage) { previewPage = null }
}

// ---------------------------------------------------------------- compress / grayscale / repair

@Composable
private fun ColumnScope.CompressBody(vm: PdfToolsViewModel, src: PdfSource) {
    var level by remember { mutableStateOf(CompressLevel.MEDIUM) }
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        SectionLabel(stringResource(R.string.pdft_compress_level))
        ChoiceRow(
            listOf(
                CompressLevel.LOW to stringResource(R.string.pdft_compress_low),
                CompressLevel.MEDIUM to stringResource(R.string.pdft_compress_medium),
                CompressLevel.HIGH to stringResource(R.string.pdft_compress_high),
            ),
            level,
        ) { level = it }
        Text(
            stringResource(R.string.pdft_compress_hint),
            Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    ActionBar(stringResource(R.string.pdft_apply), true, onClick = {
        val l = level
        vm.run(PdfTool.COMPRESS, src.sizeBytes) { p -> PdfEngine.compress(vm.context, src, l, p) }
    })
}

@Composable
private fun ColumnScope.GrayscaleBody(vm: PdfToolsViewModel, src: PdfSource) {
    var scale by remember { mutableFloatStateOf(1.5f) }
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        SectionLabel(stringResource(R.string.pdft_quality))
        ChoiceRow(
            listOf(
                1.0f to stringResource(R.string.pdft_quality_standard),
                1.5f to stringResource(R.string.pdft_quality_high),
                2.5f to stringResource(R.string.pdft_quality_max),
            ),
            scale,
        ) { scale = it }
        Text(
            stringResource(R.string.pdft_grayscale_hint),
            Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    ActionBar(stringResource(R.string.pdft_apply), true, onClick = {
        val s = scale
        vm.run(PdfTool.GRAYSCALE, src.sizeBytes) { p -> PdfEngine.rasterize(vm.context, src, true, s, 0.8f, "grayscale", p) }
    })
}

@Composable
private fun ColumnScope.RepairBody(vm: PdfToolsViewModel, src: PdfSource) {
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.pdft_repair_hint), style = MaterialTheme.typography.bodyMedium)
        if (src.broken) {
            Text(
                stringResource(R.string.pdft_repair_broken),
                Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
    ActionBar(stringResource(R.string.pdft_apply), true, onClick = {
        vm.run(PdfTool.REPAIR, src.sizeBytes) { p -> PdfEngine.repair(vm.context, src, p) }
    })
}

// ---------------------------------------------------------------- protect / unlock

@Composable
private fun ColumnScope.ProtectBody(vm: PdfToolsViewModel, src: PdfSource) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var owner by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var allowPrint by remember { mutableStateOf(true) }
    var allowCopy by remember { mutableStateOf(true) }
    var allowModify by remember { mutableStateOf(true) }
    val visual = if (show) VisualTransformation.None else PasswordVisualTransformation()
    val mismatch = confirm.isNotEmpty() && confirm != password
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = password, onValueChange = { password = it }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            label = { Text(stringResource(R.string.pdft_password)) },
            visualTransformation = visual,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        OutlinedTextField(
            value = confirm, onValueChange = { confirm = it }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            label = { Text(stringResource(R.string.pdft_password_confirm)) },
            visualTransformation = visual,
            isError = mismatch,
            supportingText = if (mismatch) ({ Text(stringResource(R.string.pdft_password_mismatch)) }) else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        OutlinedTextField(
            value = owner, onValueChange = { owner = it }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            label = { Text(stringResource(R.string.pdft_owner_password)) },
            supportingText = { Text(stringResource(R.string.pdft_owner_password_hint)) },
            visualTransformation = visual,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        SwitchRow(stringResource(R.string.pdft_show_password), show) { show = it }
        SectionLabel(stringResource(R.string.pdft_permissions))
        SwitchRow(stringResource(R.string.pdft_allow_print), allowPrint) { allowPrint = it }
        SwitchRow(stringResource(R.string.pdft_allow_copy), allowCopy) { allowCopy = it }
        SwitchRow(stringResource(R.string.pdft_allow_modify), allowModify) { allowModify = it }
    }
    ActionBar(stringResource(R.string.pdft_apply), password.isNotEmpty() && password == confirm, onClick = {
        val pw = password
        val ow = owner
        val a = allowPrint
        val b = allowCopy
        val c = allowModify
        vm.run(PdfTool.PROTECT, src.sizeBytes) { p ->
            PdfEngine.protect(vm.context, src, pw, ow, a, b, c).also { p(1, 1) }
        }
    })
}

@Composable
private fun ColumnScope.UnlockBody(vm: PdfToolsViewModel, src: PdfSource) {
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            if (src.wasEncrypted) stringResource(R.string.pdft_unlock_hint_protected) else stringResource(R.string.pdft_unlock_hint_not_protected),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    ActionBar(stringResource(R.string.pdft_unlock_save), true, onClick = {
        vm.run(PdfTool.UNLOCK, src.sizeBytes) { p -> PdfEngine.unlock(vm.context, src).also { p(1, 1) } }
    })
}
