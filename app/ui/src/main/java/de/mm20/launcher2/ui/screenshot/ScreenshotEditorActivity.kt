package de.mm20.launcher2.ui.screenshot

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.theme.LauncherTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import android.graphics.Color as AColor
import androidx.compose.ui.graphics.Path as ComposePath

/**
 * The editor of Telos Screenshot. It opens a screenshot that was just taken to choose a part of it
 * (partial screenshot), or a saved one to edit it: crop, hide parts with pixelate or blur, and
 * draw. Nothing is uploaded, and the original stays as it is: saving makes a new picture.
 */
class ScreenshotEditorActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent.getStringExtra(EXTRA_URI)?.let { Uri.parse(it) }
        val path = intent.getStringExtra(EXTRA_PATH)
        val partial = intent.getBooleanExtra(EXTRA_PARTIAL, false)
        setContent {
            LauncherTheme {
                ScreenshotEditor(uri = uri, path = path, partial = partial, onClose = { finish() })
            }
        }
    }

    companion object {
        const val EXTRA_URI = "uri"
        const val EXTRA_PATH = "path"
        const val EXTRA_PARTIAL = "partial"

        fun open(context: android.content.Context, uri: Uri) {
            context.startActivity(
                Intent(context, ScreenshotEditorActivity::class.java).putExtra(EXTRA_URI, uri.toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

private enum class EditTool { Select, Crop, Pixelate, Blur, Draw }
private enum class SelectShape { Rectangle, Oval, Freeform }

private val DRAW_COLORS = listOf(Color(0xFFE53935), Color(0xFFFDD835), Color(0xFF43A047), Color(0xFF1E88E5), Color.White, Color.Black)

@Composable
private fun ScreenshotEditor(uri: Uri?, path: String?, partial: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    val undo = remember { mutableStateListOf<Bitmap>() }
    var tool by remember { mutableStateOf(if (partial) EditTool.Select else EditTool.Crop) }
    var shape by remember { mutableStateOf(SelectShape.Rectangle) }
    var color by remember { mutableStateOf(DRAW_COLORS[0]) }
    var strokeWidth by remember { mutableStateOf(8f) }
    // what the user is drawing or selecting now, in the coordinates of the view
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragEnd by remember { mutableStateOf<Offset?>(null) }
    val freePoints = remember { mutableStateListOf<Offset>() }
    val penWidth = strokeWidth
    var selectionDone by remember { mutableStateOf(false) }

    LaunchedEffect(uri, path) {
        bitmap = withContext(Dispatchers.IO) {
            when {
                path != null -> android.graphics.BitmapFactory.decodeFile(path)
                uri != null -> ScreenshotStore.load(context, uri)
                else -> null
            }
        }
        if (bitmap == null) {
            Toast.makeText(context, R.string.screenshot_cannot_open, Toast.LENGTH_SHORT).show()
            onClose()
        }
    }

    fun push(newBitmap: Bitmap) {
        bitmap?.let { undo.add(it) }
        if (undo.size > 8) undo.removeAt(0)
        bitmap = newBitmap
    }
    fun resetSelection() {
        dragStart = null
        dragEnd = null
        freePoints.clear()
        selectionDone = false
    }

    Column(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(painterResource(R.drawable.close_24px), contentDescription = stringResource(R.string.close), tint = Color.White)
            }
            Box(Modifier.weight(1f))
            TextButton(
                onClick = { undo.removeLastOrNull()?.let { bitmap = it; resetSelection() } },
                enabled = undo.isNotEmpty(),
            ) { Text(stringResource(R.string.screenshot_undo), color = if (undo.isNotEmpty()) Color.White else Color.Gray) }
            TextButton(onClick = {
                val b = bitmap ?: return@TextButton
                val saved = ScreenshotStore.save(context, b)
                if (saved == null) {
                    Toast.makeText(context, R.string.screenshot_save_failed, Toast.LENGTH_SHORT).show()
                } else {
                    runCatching {
                        context.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).setType("image/*").putExtra(Intent.EXTRA_STREAM, saved).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                                null,
                            )
                        )
                    }
                }
            }) { Text(stringResource(R.string.voice_share), color = Color.White) }
            TextButton(onClick = {
                val b = bitmap ?: return@TextButton
                if (ScreenshotStore.save(context, b) != null) {
                    Toast.makeText(context, R.string.screenshot_saved, Toast.LENGTH_SHORT).show()
                    path?.let { File(it).delete() }
                    onClose()
                } else {
                    Toast.makeText(context, R.string.screenshot_save_failed, Toast.LENGTH_SHORT).show()
                }
            }) { Text(stringResource(R.string.voice_save), color = MaterialTheme.colorScheme.primary) }
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val b = bitmap
            if (b != null) {
                val density = androidx.compose.ui.platform.LocalDensity.current
                val viewW = with(density) { maxWidth.toPx() }
                val viewH = with(density) { maxHeight.toPx() }
                val scale = minOf(viewW / b.width, viewH / b.height)
                val offX = (viewW - b.width * scale) / 2
                val offY = (viewH - b.height * scale) / 2
                fun toBitmap(p: Offset) = Offset(((p.x - offX) / scale).coerceIn(0f, b.width.toFloat()), ((p.y - offY) / scale).coerceIn(0f, b.height.toFloat()))
                fun selectionRect(): RectF? {
                    val s = dragStart ?: return null
                    val e = dragEnd ?: return null
                    val a = toBitmap(s)
                    val c = toBitmap(e)
                    return RectF(minOf(a.x, c.x), minOf(a.y, c.y), maxOf(a.x, c.x), maxOf(a.y, c.y))
                }

                ComposeCanvas(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(tool, shape, color, strokeWidth, b) {
                            detectDragGestures(
                                onDragStart = { start ->
                                    resetSelection()
                                    dragStart = start
                                    dragEnd = start
                                    if (tool == EditTool.Draw || (tool == EditTool.Select && shape == SelectShape.Freeform)) freePoints.add(start)
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    dragEnd = change.position
                                    if (freePoints.isNotEmpty()) freePoints.add(change.position)
                                },
                                onDragEnd = {
                                    when (tool) {
                                        EditTool.Draw -> {
                                            if (freePoints.size > 1) {
                                                val out = b.copy(Bitmap.Config.ARGB_8888, true)
                                                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                                    style = Paint.Style.STROKE
                                                    strokeCap = Paint.Cap.ROUND
                                                    strokeJoin = Paint.Join.ROUND
                                                    this.strokeWidth = penWidth
                                                    this.color = AColor.argb((color.alpha * 255).toInt(), (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt())
                                                }
                                                val p = Path()
                                                freePoints.forEachIndexed { i, pt ->
                                                    val q = toBitmap(pt)
                                                    if (i == 0) p.moveTo(q.x, q.y) else p.lineTo(q.x, q.y)
                                                }
                                                Canvas(out).drawPath(p, paint)
                                                push(out)
                                            }
                                            resetSelection()
                                        }
                                        EditTool.Pixelate, EditTool.Blur -> {
                                            val r = selectionRect()
                                            if (r != null && r.width() > 4 && r.height() > 4) push(obscure(b, r, tool == EditTool.Pixelate))
                                            resetSelection()
                                        }
                                        else -> selectionDone = true
                                    }
                                },
                                onDragCancel = { resetSelection() },
                            )
                        },
                ) {
                    drawImage(
                        b.asImageBitmap(),
                        dstOffset = IntOffset(offX.toInt(), offY.toInt()),
                        dstSize = IntSize((b.width * scale).toInt(), (b.height * scale).toInt()),
                    )
                    val s = dragStart
                    val e = dragEnd
                    if (s != null && e != null) {
                        val stroke = Stroke(width = 3.dp.toPx())
                        val outline = Color.White
                        when {
                            tool == EditTool.Draw -> {
                                val p = ComposePath()
                                freePoints.forEachIndexed { i, pt -> if (i == 0) p.moveTo(pt.x, pt.y) else p.lineTo(pt.x, pt.y) }
                                drawPath(p, color, style = Stroke(width = strokeWidth * scale, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                            }
                            tool == EditTool.Select && shape == SelectShape.Freeform -> {
                                val p = ComposePath()
                                freePoints.forEachIndexed { i, pt -> if (i == 0) p.moveTo(pt.x, pt.y) else p.lineTo(pt.x, pt.y) }
                                if (selectionDone) p.close()
                                drawPath(p, outline, style = stroke)
                            }
                            tool == EditTool.Select && shape == SelectShape.Oval -> {
                                drawOval(outline, Offset(minOf(s.x, e.x), minOf(s.y, e.y)), Size(kotlin.math.abs(e.x - s.x), kotlin.math.abs(e.y - s.y)), style = stroke)
                            }
                            else -> {
                                drawRect(outline, Offset(minOf(s.x, e.x), minOf(s.y, e.y)), Size(kotlin.math.abs(e.x - s.x), kotlin.math.abs(e.y - s.y)), style = stroke)
                            }
                        }
                    }
                }

                // the selection waits for a tap on Apply
                val canApply = selectionDone && (tool == EditTool.Crop || tool == EditTool.Select)
                if (canApply) {
                    Row(Modifier.align(Alignment.BottomCenter).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(onClick = { resetSelection() }) { Text(stringResource(R.string.voice_cancel), color = Color.White) }
                        TextButton(onClick = {
                            val r = selectionRect()
                            if (r != null && r.width() > 8 && r.height() > 8) {
                                val result = if (tool == EditTool.Crop || (tool == EditTool.Select && shape == SelectShape.Rectangle)) {
                                    cropBitmap(b, r)
                                } else {
                                    shaped(b, r, shape, freePoints.map { toBitmap(it) })
                                }
                                push(result)
                            }
                            resetSelection()
                        }) { Text(stringResource(R.string.screenshot_apply), color = MaterialTheme.colorScheme.primary) }
                    }
                }
            }
        }

        Column(Modifier.fillMaxWidth().background(Color(0xFF1B1B1B)).padding(vertical = 8.dp)) {
            if (tool == EditTool.Select) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = shape == SelectShape.Rectangle, onClick = { shape = SelectShape.Rectangle; resetSelection() }, label = { Text(stringResource(R.string.screenshot_shape_rectangle)) })
                    FilterChip(selected = shape == SelectShape.Oval, onClick = { shape = SelectShape.Oval; resetSelection() }, label = { Text(stringResource(R.string.screenshot_shape_oval)) })
                    FilterChip(selected = shape == SelectShape.Freeform, onClick = { shape = SelectShape.Freeform; resetSelection() }, label = { Text(stringResource(R.string.screenshot_shape_freeform)) })
                }
            }
            if (tool == EditTool.Draw) {
                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (c in DRAW_COLORS) {
                        Box(
                            Modifier.size(if (c == color) 34.dp else 28.dp).clip(CircleShape).background(c).clickable { color = c },
                        )
                    }
                    Slider(value = strokeWidth, onValueChange = { strokeWidth = it }, valueRange = 3f..30f, modifier = Modifier.weight(1f))
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (partial) FilterChip(selected = tool == EditTool.Select, onClick = { tool = EditTool.Select; resetSelection() }, label = { Text(stringResource(R.string.screenshot_tool_select)) })
                FilterChip(selected = tool == EditTool.Crop, onClick = { tool = EditTool.Crop; resetSelection() }, label = { Text(stringResource(R.string.screenshot_tool_crop)) })
                FilterChip(selected = tool == EditTool.Pixelate, onClick = { tool = EditTool.Pixelate; resetSelection() }, label = { Text(stringResource(R.string.screenshot_tool_pixelate)) })
                FilterChip(selected = tool == EditTool.Blur, onClick = { tool = EditTool.Blur; resetSelection() }, label = { Text(stringResource(R.string.screenshot_tool_blur)) })
                FilterChip(selected = tool == EditTool.Draw, onClick = { tool = EditTool.Draw; resetSelection() }, label = { Text(stringResource(R.string.screenshot_tool_draw)) })
            }
            Text(
                stringResource(
                    when (tool) {
                        EditTool.Select -> R.string.screenshot_hint_select
                        EditTool.Crop -> R.string.screenshot_hint_crop
                        EditTool.Pixelate, EditTool.Blur -> R.string.screenshot_hint_obscure
                        EditTool.Draw -> R.string.screenshot_hint_draw
                    }
                ),
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
}

private fun cropBitmap(src: Bitmap, r: RectF): Bitmap {
    val left = r.left.toInt().coerceIn(0, src.width - 1)
    val top = r.top.toInt().coerceIn(0, src.height - 1)
    val width = r.width().toInt().coerceIn(1, src.width - left)
    val height = r.height().toInt().coerceIn(1, src.height - top)
    return Bitmap.createBitmap(src, left, top, width, height)
}

/** The part of the picture inside an oval or a free shape, with a transparent outside */
private fun shaped(src: Bitmap, r: RectF, shape: SelectShape, free: List<Offset>): Bitmap {
    val box = if (shape == SelectShape.Freeform && free.size > 2) {
        RectF(free.minOf { it.x }, free.minOf { it.y }, free.maxOf { it.x }, free.maxOf { it.y })
    } else r
    val left = box.left.toInt().coerceIn(0, src.width - 1)
    val top = box.top.toInt().coerceIn(0, src.height - 1)
    val width = box.width().toInt().coerceIn(1, src.width - left)
    val height = box.height().toInt().coerceIn(1, src.height - top)
    val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val mask = Path()
    if (shape == SelectShape.Oval) {
        mask.addOval(RectF(r.left - left, r.top - top, r.right - left, r.bottom - top), Path.Direction.CW)
    } else {
        free.forEachIndexed { i, p -> if (i == 0) mask.moveTo(p.x - left, p.y - top) else mask.lineTo(p.x - left, p.y - top) }
        mask.close()
    }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    canvas.drawPath(mask, paint)
    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    canvas.drawBitmap(src, -left.toFloat(), -top.toFloat(), paint)
    return out
}

/** Hides a part of the picture with blocks (pixelate) or by smearing it (blur) */
private fun obscure(src: Bitmap, r: RectF, pixelate: Boolean): Bitmap {
    val out = src.copy(Bitmap.Config.ARGB_8888, true)
    val left = r.left.toInt().coerceIn(0, src.width - 1)
    val top = r.top.toInt().coerceIn(0, src.height - 1)
    val width = r.width().toInt().coerceIn(1, src.width - left)
    val height = r.height().toInt().coerceIn(1, src.height - top)
    val region = Bitmap.createBitmap(src, left, top, width, height)
    val block = if (pixelate) 24 else 14
    val smallW = (width / block).coerceAtLeast(1)
    val smallH = (height / block).coerceAtLeast(1)
    val small = Bitmap.createScaledBitmap(region, smallW, smallH, !pixelate)
    val back = Bitmap.createScaledBitmap(small, width, height, !pixelate)
    Canvas(out).drawBitmap(back, left.toFloat(), top.toFloat(), null)
    return out
}
