package com.telos.desktop.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telos.desktop.i18n.TelosI18n.t
import com.telos.desktop.ui.TelosColors
import org.jetbrains.skia.Image
import java.awt.Rectangle
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Composable
fun ScreenshotCaptureMenu() {
    if (!TelosCaptureTool.menuOpen) return
    Column(
        Modifier
            .width(300.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(TelosColors.menu)
            .border(1.dp, TelosColors.outline, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(t("desktop_shot_title"), color = TelosColors.secondary, fontSize = 13.sp)
        Text(
            t("desktop_shot_full"),
            color = TelosColors.onSurface,
            modifier = Modifier.fillMaxWidth().clickable { TelosCaptureTool.capture(CaptureMode.Full) }.padding(8.dp),
        )
        Text(
            t("desktop_shot_region"),
            color = TelosColors.onSurface,
            modifier = Modifier.fillMaxWidth().clickable { TelosCaptureTool.capture(CaptureMode.Region) }.padding(8.dp),
        )
        Text(
            t("desktop_shot_window"),
            color = TelosColors.onSurface,
            modifier = Modifier.fillMaxWidth().clickable { TelosCaptureTool.capture(CaptureMode.Window) }.padding(8.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = TelosCaptureTool.copyToClipboard,
                onCheckedChange = { TelosCaptureTool.copyToClipboard = it },
            )
            Column {
                Text(t("desktop_shot_clipboard"), color = TelosColors.onSurface, fontSize = 12.sp)
                Text(t("desktop_shot_clipboard_hint"), color = TelosColors.secondary, fontSize = 10.sp)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = TelosCaptureTool.redactBeforeSave,
                onCheckedChange = { TelosCaptureTool.redactBeforeSave = it },
            )
            Column {
                Text(t("desktop_shot_redact"), color = TelosColors.onSurface, fontSize = 12.sp)
                Text(t("desktop_shot_redact_hint"), color = TelosColors.secondary, fontSize = 10.sp)
            }
        }
        Text(t("desktop_shot_path_hint"), color = TelosColors.secondary, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
        TextButton(onClick = { TelosCaptureTool.menuOpen = false }) {
            Text(t("hc_cancel"))
        }
    }
}

@Composable
fun ScreenshotRedactionOverlay() {
    val pending = TelosCaptureTool.pendingRedact ?: return
    val bytes = remember(pending.absolutePath, pending.length()) {
        runCatching { pending.readBytes() }.getOrNull()
    } ?: return
    val image = remember(bytes) {
        runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()
    } ?: return
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragEnd by remember { mutableStateOf<Offset?>(null) }

    Box(
        Modifier
            .fillMaxSize()
            .background(TelosColors.background.copy(alpha = 0.92f))
            .padding(24.dp),
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(t("desktop_shot_redact_hint"), color = TelosColors.onSurface, fontSize = 13.sp)
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(TelosColors.surfaceContainer)
                    .border(1.dp, TelosColors.outline, RoundedCornerShape(8.dp))
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(image) {
                        detectDragGestures(
                            onDragStart = { dragStart = it; dragEnd = it },
                            onDrag = { change, _ -> dragEnd = change.position },
                        )
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val iw = image.width.toFloat()
                    val ih = image.height.toFloat()
                    val scale = min(size.width / iw, size.height / ih)
                    val dw = iw * scale
                    val dh = ih * scale
                    val ox = (size.width - dw) / 2f
                    val oy = (size.height - dh) / 2f
                    drawImage(
                        image,
                        dstOffset = IntOffset(ox.toInt(), oy.toInt()),
                        dstSize = IntSize(dw.toInt().coerceAtLeast(1), dh.toInt().coerceAtLeast(1)),
                    )
                    val a = dragStart
                    val b = dragEnd
                    if (a != null && b != null) {
                        val left = min(a.x, b.x)
                        val top = min(a.y, b.y)
                        val w = abs(a.x - b.x)
                        val h = abs(a.y - b.y)
                        drawRect(
                            color = Color.Black.copy(alpha = 0.72f),
                            topLeft = Offset(left, top),
                            size = Size(w, h),
                        )
                        drawRect(
                            color = TelosColors.secondary.copy(alpha = 0.9f),
                            topLeft = Offset(left, top),
                            size = Size(w, h),
                            style = Stroke(width = 2f),
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    val rect = selectionToImageRect(dragStart, dragEnd, canvasSize, image.width, image.height)
                    TelosCaptureTool.applyRedactionAndSave(pending, rect)
                }) { Text(t("desktop_shot_save")) }
                TextButton(onClick = {
                    TelosCaptureTool.finalizeCapture(pending)
                }) { Text(t("desktop_shot_save_without_redact")) }
                TextButton(onClick = { TelosCaptureTool.cancelPending() }) { Text(t("hc_cancel")) }
            }
        }
    }
}

private fun selectionToImageRect(
    start: Offset?,
    end: Offset?,
    canvas: IntSize,
    imgW: Int,
    imgH: Int,
): Rectangle {
    if (start == null || end == null || canvas.width == 0 || canvas.height == 0) {
        return Rectangle(0, 0, 0, 0)
    }
    val scale = min(canvas.width.toFloat() / imgW, canvas.height.toFloat() / imgH)
    val dw = imgW * scale
    val dh = imgH * scale
    val ox = (canvas.width - dw) / 2f
    val oy = (canvas.height - dh) / 2f
    fun toImgX(x: Float) = ((x - ox) / scale).toInt().coerceIn(0, imgW)
    fun toImgY(y: Float) = ((y - oy) / scale).toInt().coerceIn(0, imgH)
    val x1 = toImgX(start.x)
    val y1 = toImgY(start.y)
    val x2 = toImgX(end.x)
    val y2 = toImgY(end.y)
    val left = min(x1, x2)
    val top = min(y1, y2)
    val right = max(x1, x2)
    val bottom = max(y1, y2)
    return Rectangle(left, top, (right - left).coerceAtLeast(0), (bottom - top).coerceAtLeast(0))
}
