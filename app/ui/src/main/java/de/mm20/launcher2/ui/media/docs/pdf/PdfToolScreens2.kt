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
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlin.math.max
import kotlin.math.min

private fun digitsOnly(s: String): String = s.filter { it.isDigit() }.take(6)

private val inkColors = listOf(0xFF000000.toInt(), 0xFF0D47A1.toInt(), 0xFF1B5E20.toInt())
private val markColors = listOf(0xFF000000.toInt(), 0xFF757575.toInt(), 0xFFD32F2F.toInt(), 0xFF1976D2.toInt(), 0xFF388E3C.toInt())

@Composable
private fun ColorRow(colors: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(0.dp), modifier = Modifier.padding(vertical = 0.dp)) {
        colors.forEach { c ->
            val isSelected = c == selected
            Box(Modifier.size(48.dp).clickable { onSelect(c) }, contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(36.dp)
                        .background(Color(c), CircleShape)
                        .border(
                            if (isSelected) 3.dp else 1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            CircleShape,
                        ),
                )
            }
        }
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit, valueText: String) {
    Column {
        Row {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(valueText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

// ---------------------------------------------------------------- watermark

@Composable
internal fun ColumnScope.WatermarkBody(vm: PdfToolsViewModel, src: PdfSource) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var useImage by remember { mutableStateOf(false) }
    var text by rememberSaveable { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var size by remember { mutableFloatStateOf(0.5f) }
    var opacity by remember { mutableFloatStateOf(0.3f) }
    var rotation by remember { mutableFloatStateOf(-45f) }
    var color by remember { mutableIntStateOf(markColors[1]) }
    var tiled by remember { mutableStateOf(false) }
    val pickImage = rememberSingleImagePicker { imageUri = it }
    val pages = rememberPdfPages(src.file)

    val mark by produceState<Bitmap?>(null, useImage, text, imageUri, color, opacity) {
        value = withContext(Dispatchers.IO) {
            if (useImage) imageUri?.let { PdfEngine.imageMark(context, it, opacity) }
            else if (text.isNotBlank()) PdfEngine.textBitmap(text.trim(), color, (opacity * 255).roundToInt(), 3f)
            else null
        }
    }

    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.height(220.dp).aspectRatio(0.707f).clip(MaterialTheme.shapes.small).border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)) {
                if (pages != null) PdfThumb(pages, 0, Modifier.fillMaxSize(), widthPx = 400)
                val m = mark
                if (m != null) {
                    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Image(
                            m.asImageBitmap(), null,
                            Modifier.width(maxWidth * size).graphicsLayer { rotationZ = rotation },
                            contentScale = ContentScale.FillWidth,
                        )
                    }
                }
            }
        }
        ChoiceRow(
            listOf(false to stringResource(R.string.pdft_watermark_text), true to stringResource(R.string.pdft_watermark_image)),
            useImage,
        ) { useImage = it }
        if (useImage) {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = pickImage) { Text(stringResource(R.string.pdft_choose_image)) }
                if (imageUri != null) Text(stringResource(R.string.pdft_image_chosen), Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodySmall)
            }
        } else {
            OutlinedTextField(
                value = text, onValueChange = { text = it.take(80) }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                label = { Text(stringResource(R.string.pdft_watermark_text_label)) },
            )
            SectionLabel(stringResource(R.string.pdft_color))
            ColorRow(markColors, color) { color = it }
        }
        LabeledSlider(stringResource(R.string.pdft_size), size, 0.1f..1f, { size = it }, "${(size * 100).roundToInt()}%")
        LabeledSlider(stringResource(R.string.pdft_opacity), opacity, 0.05f..1f, { opacity = it }, "${(opacity * 100).roundToInt()}%")
        LabeledSlider(stringResource(R.string.pdft_rotation), rotation, -180f..180f, { rotation = it }, "${rotation.roundToInt()}°")
        SwitchRow(stringResource(R.string.pdft_watermark_tiled), tiled) { tiled = it }
    }
    ActionBar(stringResource(R.string.pdft_apply), mark != null, onClick = {
        val m = mark ?: return@ActionBar
        val s = size
        val r = rotation
        val t = tiled
        vm.run(PdfTool.WATERMARK, src.sizeBytes) { p -> PdfEngine.watermark(context.applicationContext, src, m, s, r, t, p) }
    })
}

// ---------------------------------------------------------------- page numbers

@Composable
internal fun ColumnScope.PageNumbersBody(vm: PdfToolsViewModel, src: PdfSource) {
    var format by rememberSaveable { mutableStateOf("{n}") }
    var position by rememberSaveable { mutableStateOf(NumberPosition.BOTTOM_CENTER) }
    var fontSize by remember { mutableFloatStateOf(12f) }
    var color by remember { mutableIntStateOf(markColors[0]) }
    var start by rememberSaveable { mutableStateOf("1") }
    var skip by rememberSaveable { mutableStateOf("0") }
    val startNumber = start.toIntOrNull() ?: 1
    val skipFirst = (skip.toIntOrNull() ?: 0).coerceIn(0, src.pageCount - 1)
    val sample = format.replace("{n}", startNumber.toString()).replace("{total}", (startNumber + src.pageCount - skipFirst - 1).toString())

    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        SectionLabel(stringResource(R.string.pdft_numbers_format))
        ChoiceRow(
            listOf("{n}" to "1", "{n} / {total}" to "1 / N", "- {n} -" to "- 1 -").map { it.first to it.second },
            format,
        ) { format = it }
        OutlinedTextField(
            value = format, onValueChange = { format = it }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            label = { Text(stringResource(R.string.pdft_numbers_format_label)) },
            supportingText = { Text(stringResource(R.string.pdft_numbers_format_hint)) },
        )
        Text(stringResource(R.string.pdft_numbers_sample, sample), style = MaterialTheme.typography.bodyMedium)
        SectionLabel(stringResource(R.string.pdft_position))
        ChoiceRow(
            listOf(
                NumberPosition.TOP_LEFT to stringResource(R.string.pdft_pos_top_left),
                NumberPosition.TOP_CENTER to stringResource(R.string.pdft_pos_top_center),
                NumberPosition.TOP_RIGHT to stringResource(R.string.pdft_pos_top_right),
                NumberPosition.BOTTOM_LEFT to stringResource(R.string.pdft_pos_bottom_left),
                NumberPosition.BOTTOM_CENTER to stringResource(R.string.pdft_pos_bottom_center),
                NumberPosition.BOTTOM_RIGHT to stringResource(R.string.pdft_pos_bottom_right),
            ),
            position,
        ) { position = it }
        LabeledSlider(stringResource(R.string.pdft_size), fontSize, 8f..32f, { fontSize = it }, "${fontSize.roundToInt()}")
        SectionLabel(stringResource(R.string.pdft_color))
        ColorRow(markColors, color) { color = it }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = start, onValueChange = { start = digitsOnly(it) }, singleLine = true,
                modifier = Modifier.weight(1f),
                label = { Text(stringResource(R.string.pdft_numbers_start)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                value = skip, onValueChange = { skip = digitsOnly(it) }, singleLine = true,
                modifier = Modifier.weight(1f),
                label = { Text(stringResource(R.string.pdft_numbers_skip)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
    }
    ActionBar(stringResource(R.string.pdft_apply), format.isNotBlank() && start.isNotEmpty(), onClick = {
        val f = format
        val pos = position
        val fs = fontSize
        val c = color
        vm.run(PdfTool.PAGE_NUMBERS, src.sizeBytes) { p ->
            PdfEngine.pageNumbers(vm.context, src, f, pos, fs, c, startNumber, skipFirst, p)
        }
    })
}

// ---------------------------------------------------------------- sign

private class PlaceState {
    var x by mutableFloatStateOf(0.7f)
    var y by mutableFloatStateOf(0.85f)
    var w by mutableFloatStateOf(0.3f)
    var rot by mutableFloatStateOf(0f)
}

private enum class SignScope { THIS_PAGE, ALL_PAGES, LAST_PAGE }

@Composable
internal fun ColumnScope.SignBody(vm: PdfToolsViewModel, src: PdfSource) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var signature by remember { mutableStateOf<Bitmap?>(null) }
    var fromImage by remember { mutableStateOf(false) }
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var padSize by remember { mutableStateOf(IntSize.Zero) }
    var ink by remember { mutableIntStateOf(inkColors[1]) }
    var transparentBg by remember { mutableStateOf(true) }
    var rawImage by remember { mutableStateOf<Bitmap?>(null) }
    val density = LocalDensity.current
    val pickImage = rememberSingleImagePicker { uri ->
        scope.launch {
            rawImage = withContext(Dispatchers.IO) { PdfEngine.decodeImage(context, uri, 1200, keepAlpha = true) }
        }
    }
    val placement = remember { PlaceState() }
    var page by remember { mutableIntStateOf(0) }
    var signScope by remember { mutableStateOf(SignScope.THIS_PAGE) }
    val pages = rememberPdfPages(src.file)

    val sig = signature
    if (sig == null) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            ChoiceRow(
                listOf(false to stringResource(R.string.pdft_sign_draw), true to stringResource(R.string.pdft_sign_image)),
                fromImage,
            ) { fromImage = it }
            if (!fromImage) {
                Text(
                    stringResource(R.string.pdft_sign_draw_hint),
                    Modifier.padding(vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SignaturePad(
                    strokes, Color(ink),
                    Modifier.fillMaxWidth().height(220.dp).clip(MaterialTheme.shapes.medium)
                        .background(Color.White).border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                        .onSizeChanged { padSize = it },
                )
                ColorRow(inkColors, ink) { ink = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) }, enabled = strokes.isNotEmpty()) {
                        Text(stringResource(R.string.pdft_undo))
                    }
                    OutlinedButton(onClick = { strokes.clear() }, enabled = strokes.isNotEmpty()) { Text(stringResource(R.string.pdft_clear)) }
                }
            } else {
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(onClick = pickImage) { Text(stringResource(R.string.pdft_choose_image)) }
                }
                SwitchRow(stringResource(R.string.pdft_sign_remove_bg), transparentBg) { transparentBg = it }
                rawImage?.let { raw ->
                    Image(
                        raw.asImageBitmap(), null,
                        Modifier.fillMaxWidth().height(180.dp).padding(top = 8.dp).background(Color(0xFFE0E0E0)),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
        val ready = if (fromImage) rawImage != null else strokes.isNotEmpty()
        ActionBar(stringResource(R.string.pdft_sign_use), ready, onClick = {
            scope.launch {
                signature = withContext(Dispatchers.IO) {
                    if (fromImage) rawImage?.let { if (transparentBg) whiteToTransparent(it) else it }
                    else strokesToBitmap(strokes.toList(), ink, with(density) { 3.dp.toPx() })
                }
            }
        })
    } else {
        Text(
            stringResource(R.string.pdft_sign_place_hint),
            Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(Modifier.weight(1f).fillMaxWidth().padding(8.dp)) {
            if (pages != null) PlacementEditor(pages, page, sig, placement, Modifier.fillMaxSize())
        }
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { page = (page - 1).coerceAtLeast(0) }, enabled = page > 0) {
                Ico(BaseR.drawable.arrow_back_24px, description = stringResource(R.string.pdft_previous))
            }
            Text(stringResource(R.string.pdft_page_of, page + 1, src.pageCount), style = MaterialTheme.typography.labelLarge)
            IconButton(onClick = { page = (page + 1).coerceAtMost(src.pageCount - 1) }, enabled = page < src.pageCount - 1) {
                Ico(BaseR.drawable.arrow_forward_24px, description = stringResource(R.string.pdft_next))
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { signature = null }) { Text(stringResource(R.string.pdft_sign_change)) }
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            ChoiceRow(
                listOf(
                    SignScope.THIS_PAGE to stringResource(R.string.pdft_sign_scope_this),
                    SignScope.ALL_PAGES to stringResource(R.string.pdft_sign_scope_all),
                    SignScope.LAST_PAGE to stringResource(R.string.pdft_sign_scope_last),
                ),
                signScope,
            ) { signScope = it }
            LabeledSlider(
                stringResource(R.string.pdft_rotation), placement.rot, -180f..180f, { placement.rot = it },
                "${placement.rot.roundToInt()}°",
            )
        }
        ActionBar(stringResource(R.string.pdft_apply), true, onClick = {
            val target = when (signScope) {
                SignScope.THIS_PAGE -> page
                SignScope.ALL_PAGES -> -1
                SignScope.LAST_PAGE -> src.pageCount - 1
            }
            val pl = Placement(target, placement.x, placement.y, placement.w, placement.rot, sig)
            vm.run(PdfTool.SIGN, src.sizeBytes) { p -> PdfEngine.sign(vm.context, src, listOf(pl), p) }
        })
    }
}

@Composable
private fun PlacementEditor(pages: PdfPages, page: Int, sig: Bitmap, st: PlaceState, modifier: Modifier) {
    val bmp by produceState<Bitmap?>(null, pages, page) {
        val fresh = withContext(Dispatchers.IO) { runCatching { pages.render(page, 1000) }.getOrNull() }
        if (fresh != null) value = fresh
    }
    val b = bmp
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        if (b != null) {
            val aspect = b.width.toFloat() / b.height
            val w = minOf(maxWidth, maxHeight * aspect)
            val h = w / aspect
            Box(
                Modifier
                    .size(w, h)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, rotation ->
                            st.x = (st.x + pan.x / size.width).coerceIn(0f, 1f)
                            st.y = (st.y + pan.y / size.height).coerceIn(0f, 1f)
                            st.w = (st.w * zoom).coerceIn(0.05f, 0.95f)
                            st.rot = ((st.rot + rotation + 540f) % 360f) - 180f
                        }
                    },
            ) {
                Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                val sw = w * st.w
                val sh = sw * sig.height / sig.width
                Image(
                    sig.asImageBitmap(), null,
                    Modifier
                        .offset { IntOffset((st.x * w.toPx() - sw.toPx() / 2).roundToInt(), (st.y * h.toPx() - sh.toPx() / 2).roundToInt()) }
                        .size(sw, sh)
                        .graphicsLayer { rotationZ = st.rot },
                    contentScale = ContentScale.FillBounds,
                )
            }
        }
    }
}

@Composable
private fun SignaturePad(strokes: SnapshotStateList<List<Offset>>, color: Color, modifier: Modifier) {
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    Canvas(
        modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { o -> current = listOf(o) },
                onDragEnd = {
                    if (current.isNotEmpty()) strokes.add(current)
                    current = emptyList()
                },
                onDragCancel = {
                    if (current.isNotEmpty()) strokes.add(current)
                    current = emptyList()
                },
            ) { change, _ ->
                change.consume()
                current = current + change.position
            }
        },
    ) {
        val width = 3.dp.toPx()
        (strokes.toList() + listOf(current)).forEach { pts ->
            if (pts.size == 1) {
                drawCircle(color, width / 2, pts[0])
            } else if (pts.size > 1) {
                val path = Path()
                path.moveTo(pts[0].x, pts[0].y)
                for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)
                drawPath(path, color, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

private fun strokesToBitmap(strokes: List<List<Offset>>, ink: Int, strokePx: Float): Bitmap? {
    val pts = strokes.flatten()
    if (pts.isEmpty()) return null
    val minX = pts.minOf { it.x }
    val maxX = pts.maxOf { it.x }
    val minY = pts.minOf { it.y }
    val maxY = pts.maxOf { it.y }
    val pad = strokePx * 2
    val bw = (maxX - minX) + 2 * pad
    val bh = (maxY - minY) + 2 * pad
    val scale = min(3f, 1200f / max(bw, 1f))
    val bmp = Bitmap.createBitmap((bw * scale).toInt().coerceAtLeast(1), (bh * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val line = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
        this.strokeWidth = strokePx * scale
        this.color = ink
    }
    val dot = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { this.color = ink }
    for (s in strokes) {
        if (s.size == 1) {
            canvas.drawCircle((s[0].x - minX + pad) * scale, (s[0].y - minY + pad) * scale, strokePx * scale / 2, dot)
        } else if (s.size > 1) {
            val path = android.graphics.Path()
            path.moveTo((s[0].x - minX + pad) * scale, (s[0].y - minY + pad) * scale)
            for (i in 1 until s.size) path.lineTo((s[i].x - minX + pad) * scale, (s[i].y - minY + pad) * scale)
            canvas.drawPath(path, line)
        }
    }
    return bmp
}

/** Makes the light paper of a photographed signature transparent. */
private fun whiteToTransparent(src: Bitmap): Bitmap {
    val w = src.width
    val h = src.height
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val row = IntArray(w)
    for (y in 0 until h) {
        src.getPixels(row, 0, w, 0, y, w, 1)
        for (x in 0 until w) {
            val c = row[x]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val lum = (r * 3 + g * 6 + b) / 10
            val alpha = ((225 - lum) * 255 / 120).coerceIn(0, 255) * ((c ushr 24) and 0xFF) / 255
            row[x] = (alpha shl 24) or (r shl 16) or (g shl 8) or b
        }
        out.setPixels(row, 0, w, 0, y, w, 1)
    }
    return out
}

// ---------------------------------------------------------------- metadata

@Composable
internal fun ColumnScope.MetadataBody(vm: PdfToolsViewModel, src: PdfSource) {
    var meta by remember { mutableStateOf<PdfMeta?>(null) }
    var title by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var keywords by remember { mutableStateOf("") }
    var creator by remember { mutableStateOf("") }
    var producer by remember { mutableStateOf("") }
    var clearAll by remember { mutableStateOf(false) }
    LaunchedEffect(src.id) {
        val m = runCatching { PdfEngine.readMetadata(vm.context, src) }.getOrNull() ?: return@LaunchedEffect
        meta = m
        title = m.title; author = m.author; subject = m.subject
        keywords = m.keywords; creator = m.creator; producer = m.producer
    }
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        meta?.let { m ->
            SectionLabel(stringResource(R.string.pdft_meta_info))
            val unknown = stringResource(R.string.pdft_meta_unknown)
            Text(stringResource(R.string.pdft_meta_pages_version, m.pages, m.version.toString()), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.pdft_meta_created, m.created.ifEmpty { unknown }), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.pdft_meta_modified, m.modified.ifEmpty { unknown }), style = MaterialTheme.typography.bodyMedium)
        }
        val fieldModifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        OutlinedTextField(title, { title = it }, fieldModifier, enabled = !clearAll, singleLine = true, label = { Text(stringResource(R.string.pdft_meta_title)) })
        OutlinedTextField(author, { author = it }, fieldModifier, enabled = !clearAll, singleLine = true, label = { Text(stringResource(R.string.pdft_meta_author)) })
        OutlinedTextField(subject, { subject = it }, fieldModifier, enabled = !clearAll, singleLine = true, label = { Text(stringResource(R.string.pdft_meta_subject)) })
        OutlinedTextField(keywords, { keywords = it }, fieldModifier, enabled = !clearAll, singleLine = true, label = { Text(stringResource(R.string.pdft_meta_keywords)) })
        OutlinedTextField(creator, { creator = it }, fieldModifier, enabled = !clearAll, singleLine = true, label = { Text(stringResource(R.string.pdft_meta_creator)) })
        OutlinedTextField(producer, { producer = it }, fieldModifier, enabled = !clearAll, singleLine = true, label = { Text(stringResource(R.string.pdft_meta_producer)) })
        SwitchRow(stringResource(R.string.pdft_meta_clear), clearAll) { clearAll = it }
    }
    ActionBar(stringResource(R.string.pdft_apply), meta != null, onClick = {
        val ca = clearAll
        vm.run(PdfTool.METADATA, src.sizeBytes) { p ->
            PdfEngine.writeMetadata(vm.context, src, title, author, subject, keywords, creator, producer, ca).also { p(1, 1) }
        }
    })
}

// ---------------------------------------------------------------- images to pdf

@Composable
internal fun ImagesToPdfScreen(vm: PdfToolsViewModel, snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val pick = rememberImagePicker { vm.addImages(it) }
    var mode by remember { mutableStateOf(PageSizeMode.FIT_IMAGE) }
    var quality by remember { mutableFloatStateOf(0.85f) }
    var margin by remember { mutableFloatStateOf(0f) }
    var name by remember { mutableStateOf("") }
    val defaultName = stringResource(R.string.pdft_default_name_images)
    val images = vm.images
    ToolFrame(stringResource(PdfTool.IMAGES_TO_PDF.title), onBack = { vm.back {} }, snackbar = snackbar) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (images.isEmpty()) {
                Box(Modifier.weight(1f)) {
                    EmptyPick(stringResource(R.string.pdft_images_hint), stringResource(R.string.pdft_add_images), pick)
                }
            } else {
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(images.toList(), key = { _, img -> img.id }) { index, item ->
                        val thumb by produceState<Bitmap?>(null, item.id) {
                            value = withContext(Dispatchers.IO) { PdfEngine.decodeImage(context, item.uri, 160) }
                        }
                        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(56.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                                    thumb?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                                }
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    Text(item.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text("${index + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { val x = images.removeAt(index); images.add(index - 1, x) }, enabled = index > 0) {
                                    Ico(BaseR.drawable.keyboard_arrow_up_24px, description = stringResource(R.string.pdft_move_up))
                                }
                                IconButton(onClick = { val x = images.removeAt(index); images.add(index + 1, x) }, enabled = index < images.lastIndex) {
                                    Ico(BaseR.drawable.keyboard_arrow_down_24px, description = stringResource(R.string.pdft_move_down))
                                }
                                IconButton(onClick = { images.removeAt(index) }) {
                                    Ico(BaseR.drawable.close_24px, description = stringResource(R.string.pdft_remove))
                                }
                            }
                        }
                    }
                }
                Column(Modifier.heightIn(max = 230.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                    SectionLabel(stringResource(R.string.pdft_images_page_size))
                    ChoiceRow(
                        listOf(
                            PageSizeMode.FIT_IMAGE to stringResource(R.string.pdft_page_size_fit),
                            PageSizeMode.A4 to "A4",
                            PageSizeMode.LETTER to "Letter",
                        ),
                        mode,
                    ) { mode = it }
                    SectionLabel(stringResource(R.string.pdft_images_margin))
                    ChoiceRow(
                        listOf(
                            0f to stringResource(R.string.pdft_margin_none),
                            18f to stringResource(R.string.pdft_margin_small),
                            36f to stringResource(R.string.pdft_margin_medium),
                        ),
                        margin,
                    ) { margin = it }
                    SectionLabel(stringResource(R.string.pdft_quality))
                    ChoiceRow(
                        listOf(
                            0.7f to stringResource(R.string.pdft_quality_standard),
                            0.85f to stringResource(R.string.pdft_quality_high),
                            0.95f to stringResource(R.string.pdft_quality_max),
                        ),
                        quality,
                    ) { quality = it }
                    OutlinedTextField(
                        value = name, onValueChange = { name = it }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
                        label = { Text(stringResource(R.string.pdft_output_name)) },
                        placeholder = { Text(defaultName) },
                    )
                }
            }
            ActionBar(
                stringResource(R.string.pdft_images_action, images.size),
                images.isNotEmpty(),
                onClick = {
                    val list = images.toList()
                    val m = mode
                    val q = quality
                    val mg = margin
                    val outName = name.trim().ifEmpty { defaultName }.removeSuffix(".pdf") + ".pdf"
                    vm.run(PdfTool.IMAGES_TO_PDF, 0L) { p -> PdfEngine.imagesToPdf(context.applicationContext, list, m, q, mg, outName, p) }
                },
                secondary = { OutlinedButton(onClick = pick) { Text(stringResource(R.string.pdft_add)) } },
            )
        }
    }
}

// ---------------------------------------------------------------- pdf to images

@Composable
internal fun ColumnScope.PdfToImagesBody(vm: PdfToolsViewModel, src: PdfSource) {
    val pages = rememberPdfPages(src.file)
    val selected = remember { mutableStateListOf<Int>().also { it.addAll(0 until src.pageCount) } }
    var format by remember { mutableStateOf(ImageFormat.PNG) }
    var scale by remember { mutableFloatStateOf(2f) }
    var zip by remember { mutableStateOf(true) }
    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.pdft_selected_count, selected.size), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { selected.clear(); selected.addAll(0 until src.pageCount) }) { Text(stringResource(R.string.pdft_select_all)) }
        TextButton(onClick = { selected.clear() }) { Text(stringResource(R.string.pdft_select_none)) }
    }
    Box(Modifier.weight(1f)) {
        if (pages != null) {
            PageGrid(pages, (0 until src.pageCount).toList(), Modifier.fillMaxSize(), selected = selected.toSet()) { p ->
                if (p in selected) selected.remove(p) else selected.add(p)
            }
        }
    }
    Column(Modifier.heightIn(max = 190.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        ChoiceRow(
            listOf(ImageFormat.PNG to "PNG", ImageFormat.JPEG to "JPEG", ImageFormat.WEBP to "WebP"),
            format,
        ) { format = it }
        ChoiceRow(
            listOf(
                1.5f to stringResource(R.string.pdft_quality_standard),
                2f to stringResource(R.string.pdft_quality_high),
                3f to stringResource(R.string.pdft_quality_max),
            ),
            scale,
        ) { scale = it }
        SwitchRow(stringResource(R.string.pdft_images_zip), zip) { zip = it }
    }
    ActionBar(stringResource(R.string.pdft_to_images_action, selected.size), selected.isNotEmpty(), onClick = {
        val list = selected.sorted()
        val f = format
        val s = scale
        val z = zip
        vm.run(PdfTool.PDF_TO_IMAGES, src.sizeBytes) { p -> PdfEngine.pdfToImages(vm.context, src, list, f, s, z, p) }
    })
}

// ---------------------------------------------------------------- extract images

@Composable
internal fun ColumnScope.ExtractImagesBody(vm: PdfToolsViewModel, src: PdfSource) {
    var infos by remember { mutableStateOf<List<ImageInfo>?>(null) }
    val selected = remember { mutableStateListOf<Int>() }
    LaunchedEffect(src.id) {
        val list = runCatching { PdfEngine.scanImages(vm.context, src) }.getOrDefault(emptyList())
        infos = list
        selected.clear()
        selected.addAll(list.map { it.index })
    }
    val list = infos
    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (list == null) stringResource(R.string.pdft_scanning) else stringResource(R.string.pdft_images_found, list.size),
            Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = { selected.clear(); selected.addAll(list?.map { it.index } ?: emptyList()) }) { Text(stringResource(R.string.pdft_select_all)) }
        TextButton(onClick = { selected.clear() }) { Text(stringResource(R.string.pdft_select_none)) }
    }
    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 12.dp)) {
        items(list ?: emptyList(), key = { it.index }) { info ->
            Row(
                Modifier.fillMaxWidth().clickable { if (info.index in selected) selected.remove(info.index) else selected.add(info.index) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = info.index in selected, onCheckedChange = { if (it) selected.add(info.index) else selected.remove(info.index) })
                Column {
                    Text(stringResource(R.string.pdft_image_row, info.index + 1, info.page + 1), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${info.width} x ${info.height}  ${info.suffix.uppercase()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (list != null && list.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.pdft_no_images),
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    ActionBar(stringResource(R.string.pdft_extract_action, selected.size), selected.isNotEmpty(), onClick = {
        val sel = selected.toSet()
        vm.run(PdfTool.EXTRACT_IMAGES, src.sizeBytes) { p -> PdfEngine.extractImages(vm.context, src, sel, p) }
    })
}

// ---------------------------------------------------------------- pdf to text

@Composable
internal fun ColumnScope.PdfToTextBody(vm: PdfToolsViewModel, src: PdfSource) {
    val pages = rememberPdfPages(src.file)
    val selected = remember { mutableStateListOf<Int>().also { it.addAll(0 until src.pageCount) } }
    var markers by remember { mutableStateOf(true) }
    val markerFormat = stringResource(R.string.pdft_page_marker)
    val noText = stringResource(R.string.pdft_no_text)
    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.pdft_selected_count, selected.size), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { selected.clear(); selected.addAll(0 until src.pageCount) }) { Text(stringResource(R.string.pdft_select_all)) }
        TextButton(onClick = { selected.clear() }) { Text(stringResource(R.string.pdft_select_none)) }
    }
    Box(Modifier.weight(1f)) {
        if (pages != null) {
            PageGrid(pages, (0 until src.pageCount).toList(), Modifier.fillMaxSize(), selected = selected.toSet()) { p ->
                if (p in selected) selected.remove(p) else selected.add(p)
            }
        }
    }
    Column(Modifier.padding(horizontal = 16.dp)) {
        SwitchRow(stringResource(R.string.pdft_text_markers), markers) { markers = it }
    }
    ActionBar(stringResource(R.string.pdft_apply), selected.isNotEmpty(), onClick = {
        val list = selected.sorted()
        val mk = markers
        vm.run(PdfTool.PDF_TO_TEXT, src.sizeBytes) { p ->
            val text = PdfEngine.pdfToText(vm.context, src, list, mk, markerFormat, p)
                ?: throw IllegalStateException(noText)
            PdfEngine.textToResult(vm.context, src, text)
        }
    })
}

// ---------------------------------------------------------------- preview

@Composable
internal fun ColumnScope.PreviewBody(src: PdfSource) {
    val pages = rememberPdfPages(src.file)
    var open by remember { mutableStateOf<Int?>(null) }
    Text(
        stringResource(R.string.pdft_preview_hint),
        Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Box(Modifier.weight(1f)) {
        if (pages != null) PageGrid(pages, (0 until src.pageCount).toList(), Modifier.fillMaxSize()) { open = it }
    }
    val p = pages
    val o = open
    if (p != null && o != null) PdfPreviewDialog(p, o) { open = null }
}

// ---------------------------------------------------------------- compare

private enum class CompareMode { SIDE_BY_SIDE, DIFFERENCE }

@Composable
internal fun CompareScreen(vm: PdfToolsViewModel, snackbar: SnackbarHostState) {
    var a by remember { mutableStateOf(vm.pdfs.filter { !it.broken }.let { if (it.size >= 2) it[it.size - 2] else null }) }
    var b by remember { mutableStateOf(vm.pdfs.filter { !it.broken }.let { if (it.size >= 2) it[it.size - 1] else null }) }
    val pickA = rememberPdfPicker(multiple = false) { vm.addPdfs(it) { s -> a = s } }
    val pickB = rememberPdfPicker(multiple = false) { vm.addPdfs(it) { s -> b = s } }
    val pagesA = rememberPdfPages(a?.file)
    val pagesB = rememberPdfPages(b?.file)
    var mode by remember { mutableStateOf(CompareMode.SIDE_BY_SIDE) }
    var page by remember { mutableIntStateOf(0) }
    var lightbox by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var progress by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    var compareFailed by remember { mutableStateOf(false) }
    val analysis by produceState<CompareResult?>(null, a?.id, b?.id) {
        value = null
        compareFailed = false
        val x = a
        val y = b
        if (x != null && y != null) {
            value = try {
                PdfEngine.compare(vm.context, x, y) { d, t -> progress = d to t }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                compareFailed = true
                null
            }
        }
        progress = null
    }
    val maxPages = max(pagesA?.count ?: 0, pagesB?.count ?: 0)
    if (page >= maxPages && maxPages > 0) page = maxPages - 1

    ToolFrame(stringResource(PdfTool.COMPARE.title), onBack = { vm.back {} }, snackbar = snackbar) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompareSlot(stringResource(R.string.pdft_compare_original), a, pickA, Modifier.weight(1f))
                CompareSlot(stringResource(R.string.pdft_compare_revised), b, pickB, Modifier.weight(1f))
            }
            if (a == null || b == null) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    Text(
                        stringResource(R.string.pdft_compare_hint),
                        Modifier.padding(32.dp).align(Alignment.Center),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    val r = analysis
                    val pr = progress
                    Text(
                        when {
                            r != null && r.differing.isEmpty() -> stringResource(R.string.pdft_compare_same_text)
                            r != null -> stringResource(R.string.pdft_compare_summary, r.differing.size, max(r.pagesA, r.pagesB))
                            compareFailed -> stringResource(R.string.pdft_error_title)
                            pr != null -> stringResource(R.string.pdft_progress, pr.first.coerceAtMost(pr.second), pr.second)
                            else -> stringResource(R.string.pdft_working)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (r != null && r.differing.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                            items(r.differing.take(200)) { p ->
                                androidx.compose.material3.AssistChip(onClick = { page = p }, label = { Text("${p + 1}") })
                            }
                        }
                    }
                    ChoiceRow(
                        listOf(
                            CompareMode.SIDE_BY_SIDE to stringResource(R.string.pdft_compare_side),
                            CompareMode.DIFFERENCE to stringResource(R.string.pdft_compare_diff),
                        ),
                        mode,
                    ) { mode = it }
                }
                Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { page = (page - 1).coerceAtLeast(0) }, enabled = page > 0) {
                        Ico(BaseR.drawable.arrow_back_24px, description = stringResource(R.string.pdft_previous))
                    }
                    Text(stringResource(R.string.pdft_page_of, page + 1, maxPages), style = MaterialTheme.typography.labelLarge)
                    IconButton(onClick = { page = (page + 1).coerceAtMost((maxPages - 1).coerceAtLeast(0)) }, enabled = page < maxPages - 1) {
                        Ico(BaseR.drawable.arrow_forward_24px, description = stringResource(R.string.pdft_next))
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth().padding(8.dp)) {
                    if (mode == CompareMode.SIDE_BY_SIDE) {
                        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ComparePage(pagesA, page, Modifier.weight(1f).fillMaxHeight()) { lightbox = 0 to page }
                            ComparePage(pagesB, page, Modifier.weight(1f).fillMaxHeight()) { lightbox = 1 to page }
                        }
                    } else {
                        DiffPage(pagesA, pagesB, page, Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
    lightbox?.let { (which, p) ->
        val pages = if (which == 0) pagesA else pagesB
        if (pages != null) PdfPreviewDialog(pages, p) { lightbox = null }
    }
}

@Composable
private fun CompareSlot(label: String, src: PdfSource?, onPick: () -> Unit, modifier: Modifier) {
    Surface(onClick = onPick, modifier = modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(
                src?.name ?: stringResource(R.string.pdft_choose_pdf),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (src != null) {
                Text(
                    stringResource(R.string.pdft_pages_count, src.pageCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ComparePage(pages: PdfPages?, page: Int, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        if (pages != null && page < pages.count) {
            PdfThumb(pages, page, Modifier.fillMaxSize(), widthPx = 700)
        } else {
            Text(stringResource(R.string.pdft_compare_missing), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DiffPage(pagesA: PdfPages?, pagesB: PdfPages?, page: Int, modifier: Modifier) {
    val diff by produceState<Bitmap?>(null, pagesA, pagesB, page) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (pagesA == null || pagesB == null || page >= pagesA.count || page >= pagesB.count) null
                else diffBitmap(pagesA.render(page, 700), pagesB.render(page, 700))
            }.getOrNull()
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        val d = diff
        if (d != null) {
            Image(d.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        } else {
            Text(stringResource(R.string.pdft_compare_missing), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Page A faded, everything that differs from page B in red. */
private fun diffBitmap(a: Bitmap, b: Bitmap): Bitmap {
    val w = a.width
    val h = a.height
    val scaledB = if (b.width == w && b.height == h) b else Bitmap.createScaledBitmap(b, w, h, true)
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val ra = IntArray(w)
    val rb = IntArray(w)
    val ro = IntArray(w)
    for (y in 0 until h) {
        a.getPixels(ra, 0, w, 0, y, w, 1)
        scaledB.getPixels(rb, 0, w, 0, y, w, 1)
        for (x in 0 until w) {
            val pa = ra[x]
            val pb = rb[x]
            val dr = abs(((pa shr 16) and 0xFF) - ((pb shr 16) and 0xFF))
            val dg = abs(((pa shr 8) and 0xFF) - ((pb shr 8) and 0xFF))
            val db = abs((pa and 0xFF) - (pb and 0xFF))
            ro[x] = if (dr + dg + db > 96) {
                0xFFE53935.toInt()
            } else {
                val gray = (((pa shr 16) and 0xFF) + ((pa shr 8) and 0xFF) + (pa and 0xFF)) / 3
                val faded = 255 - (255 - gray) / 3
                (0xFF shl 24) or (faded shl 16) or (faded shl 8) or faded
            }
        }
        out.setPixels(ro, 0, w, 0, y, w, 1)
    }
    if (scaledB !== b) scaledB.recycle()
    return out
}
