package de.mm20.launcher2.ui.files

import android.content.ClipData
import android.graphics.Paint
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp

internal data class FilesInternalDrag(val workspace: String, val paths: List<String>, val root: Boolean, val shizuku: Boolean = false)
internal class FilesDragScope(val workspace: String, val dropped: (FilesViewModel, FilesInternalDrag) -> Unit)
internal val LocalFilesDragScope = staticCompositionLocalOf<FilesDragScope?> { null }

/** Internal Android drag data never grants other apps access to the selected paths. */
@Suppress("DEPRECATION")
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun fileDragSource(entry: FsEntry, vm: FilesViewModel): Modifier {
    val scope = LocalFilesDragScope.current ?: return Modifier
    return remember(entry.path, vm, scope) {
        Modifier.dragAndDropSource(drawDragDecoration = {
            drawRoundRect(Color(0xFFDCE4FF))
            drawContext.canvas.nativeCanvas.drawText(entry.name.take(40), 12.dp.toPx(), 28.dp.toPx(),
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; textSize = 16.dp.toPx() })
        }, block = {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val held = awaitLongPressOrCancellation(down.id)
                if (held != null) {
                    var distance = androidx.compose.ui.geometry.Offset.Zero
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == held.id } ?: break
                        if (!change.pressed) break
                        distance += change.positionChange()
                        if (distance.getDistance() > viewConfiguration.touchSlop) {
                            val paths = if (entry.path in vm.selection) vm.selectedEntries().map { it.path } else listOf(entry.path)
                            change.consume()
                            startTransfer(DragAndDropTransferData(clipData = ClipData.newPlainText("telos_internal_files", ""),
                                localState = FilesInternalDrag(scope.workspace, paths.toList(), vm.rootMode, vm.shizukuMode)))
                            break
                        }
                    }
                }
            }
        })
    }
}
