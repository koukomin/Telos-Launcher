package com.telos.desktop.quake

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telos.desktop.ui.TelosColors
import com.telos.desktop.wm.WorkArea
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor

enum class SplitMode { None, Horizontal, Vertical }

class QuakePane {
    val pty = PtySession()
    val buffer = AnsiTerminalBuffer()
    init {
        pty.listeners += { buffer.feed(it) }
        pty.start()
    }
    fun close() { pty.destroy() }
}

class QuakeTab(var title: String) {
    val panes = mutableStateListOf(QuakePane())
    var split by mutableStateOf(SplitMode.None)
    var splitRatio by mutableFloatStateOf(0.5f)
    fun active(): QuakePane = panes.first()
}

object TelosQuake {
    var open by mutableStateOf(false)
    var pinned by mutableStateOf(false)
    var retractOnFocusLoss by mutableStateOf(true)
    var widthFrac by mutableFloatStateOf(0.9f)
    var heightFrac by mutableFloatStateOf(0.5f)
    var glass by mutableFloatStateOf(0.88f)
    val tabs = mutableStateListOf(QuakeTab("Session 1"))
    var tabIndex by mutableStateOf(0)
    var showSearch by mutableStateOf(false)
    var searchQuery by mutableStateOf("")

    fun toggle() { open = !open }
    fun current() = tabs.getOrNull(tabIndex) ?: tabs.first()

    fun newTab() {
        tabs += QuakeTab("Session ${tabs.size + 1}")
        tabIndex = tabs.lastIndex
    }

    fun closeTab() {
        if (tabs.size <= 1) {
            current().panes.forEach { it.close() }
            open = false
            return
        }
        val t = tabs.removeAt(tabIndex)
        t.panes.forEach { it.close() }
        tabIndex = tabIndex.coerceAtMost(tabs.lastIndex)
    }

    fun onGlobalKey(ev: KeyEvent): Boolean {
        if (ev.type != KeyEventType.KeyDown) return false
        if (ev.key == Key.F12) { toggle(); return true }
        if (ev.isCtrlPressed && ev.key == Key.Grave) { toggle(); return true }
        if (!open) return false
        val pane = current().active()
        if (ev.isCtrlPressed && ev.isShiftPressed && ev.key == Key.T) { newTab(); return true }
        if (ev.isCtrlPressed && ev.isShiftPressed && ev.key == Key.W) { closeTab(); return true }
        if (ev.isShiftPressed && ev.key == Key.DirectionLeft) { tabIndex = (tabIndex - 1 + tabs.size) % tabs.size; return true }
        if (ev.isShiftPressed && ev.key == Key.DirectionRight) { tabIndex = (tabIndex + 1) % tabs.size; return true }
        if (ev.isCtrlPressed && ev.isShiftPressed && ev.key == Key.F) { showSearch = !showSearch; return true }
        if (ev.isCtrlPressed && ev.isShiftPressed && ev.key == Key.C) {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(
                java.awt.datatransfer.StringSelection(pane.buffer.text().takeLast(4000)),
                null,
            )
            return true
        }
        if (ev.isCtrlPressed && ev.isShiftPressed && ev.key == Key.V) {
            val clip = runCatching {
                Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.stringFlavor) as String
            }.getOrNull()
            if (clip != null) pane.pty.write(clip)
            return true
        }
        if (ev.isCtrlPressed && ev.key == Key.C) { pane.pty.sigint(); return true }
        if (ev.key == Key.Enter) { pane.pty.write("\n"); return true }
        if (ev.key == Key.Backspace) { pane.pty.write("\u007f"); return true }
        if (ev.key == Key.Tab) { pane.pty.write("\t"); return true }
        if (ev.key == Key.DirectionUp) { pane.pty.write("\u001b[A"); return true }
        if (ev.key == Key.DirectionDown) { pane.pty.write("\u001b[B"); return true }
        val ch = ev.utf16CodePoint
        if (ch in 32..126) {
            pane.pty.write(ch.toChar().toString())
            return true
        }
        return false
    }
}

@Composable
fun TelosQuakeTerminal(workArea: WorkArea) {
    val shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = TelosQuake.open,
            enter = slideInVertically { -it },
            exit = slideOutVertically { -it },
        ) {
            val w = (workArea.screenWidth * TelosQuake.widthFrac).dp
            val h = (workArea.height * TelosQuake.heightFrac).dp
            Column(
                Modifier
                    .padding(top = workArea.topBarHeight.dp)
                    .width(w)
                    .height(h)
                    .clip(shape)
                    .background(TelosColors.surface.copy(alpha = TelosQuake.glass))
                    .border(2.dp, TelosColors.secondary, shape),
            ) {
                val tab = TelosQuake.current()
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (tab.split) {
                        SplitMode.None -> PaneView(tab.panes.first())
                        SplitMode.Vertical -> Row(Modifier.fillMaxSize()) {
                            Box(Modifier.weight(tab.splitRatio)) { PaneView(tab.panes[0]) }
                            Box(
                                Modifier.width(6.dp).fillMaxHeight().background(TelosColors.secondary)
                                    .pointerInput(Unit) {
                                        detectDragGestures { _, d ->
                                            tab.splitRatio = (tab.splitRatio + d.x / 800f).coerceIn(0.2f, 0.8f)
                                        }
                                    },
                            )
                            Box(Modifier.weight(1f - tab.splitRatio)) { PaneView(tab.panes.getOrElse(1) { tab.panes[0] }) }
                        }
                        SplitMode.Horizontal -> Column(Modifier.fillMaxSize()) {
                            Box(Modifier.weight(tab.splitRatio)) { PaneView(tab.panes[0]) }
                            Box(
                                Modifier.height(6.dp).fillMaxWidth().background(TelosColors.secondary)
                                    .pointerInput(Unit) {
                                        detectDragGestures { _, d ->
                                            tab.splitRatio = (tab.splitRatio + d.y / 500f).coerceIn(0.2f, 0.8f)
                                        }
                                    },
                            )
                            Box(Modifier.weight(1f - tab.splitRatio)) { PaneView(tab.panes.getOrElse(1) { tab.panes[0] }) }
                        }
                    }
                }
                if (TelosQuake.showSearch) {
                    TextField(TelosQuake.searchQuery, { TelosQuake.searchQuery = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Find") })
                }
                Row(
                    Modifier.fillMaxWidth().height(36.dp).background(TelosColors.surfaceContainer),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TelosQuake.tabs.forEachIndexed { i, t ->
                        Text(
                            t.title,
                            color = if (i == TelosQuake.tabIndex) TelosColors.secondary else TelosColors.onSurface,
                            fontSize = 12.sp,
                            modifier = Modifier.clickable { TelosQuake.tabIndex = i }.padding(horizontal = 10.dp),
                        )
                    }
                    Text("+", color = TelosColors.primary, modifier = Modifier.clickable { TelosQuake.newTab() }.padding(8.dp))
                    Text("×", color = TelosColors.primary, modifier = Modifier.clickable { TelosQuake.closeTab() }.padding(8.dp))
                    Text("H-split", fontSize = 11.sp, color = TelosColors.onSurface, modifier = Modifier.clickable {
                        if (tab.panes.size < 2) tab.panes += QuakePane()
                        tab.split = SplitMode.Horizontal
                    }.padding(6.dp))
                    Text("V-split", fontSize = 11.sp, color = TelosColors.onSurface, modifier = Modifier.clickable {
                        if (tab.panes.size < 2) tab.panes += QuakePane()
                        tab.split = SplitMode.Vertical
                    }.padding(6.dp))
                    Text(if (TelosQuake.pinned) "Pinned" else "Pin", color = TelosColors.secondary, modifier = Modifier.clickable {
                        TelosQuake.pinned = !TelosQuake.pinned
                        TelosQuake.retractOnFocusLoss = !TelosQuake.pinned
                    }.padding(8.dp))
                }
            }
        }
    }
}

@Composable
private fun PaneView(pane: QuakePane) {
    val scroll = rememberScrollState()
    val lines = pane.buffer.visible()
    val q = TelosQuake.searchQuery
    Column(Modifier.fillMaxSize().background(Color(0xCC0B0812)).verticalScroll(scroll).padding(8.dp)) {
        lines.forEach { line ->
            val show = if (q.isBlank()) line else if (line.contains(q, true)) line else null
            if (show != null) Text(show, color = TelosColors.onSurface, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
        }
    }
    DisposableEffect(pane) { onDispose { } }
}
