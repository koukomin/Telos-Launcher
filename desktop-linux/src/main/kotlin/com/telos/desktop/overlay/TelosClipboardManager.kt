package com.telos.desktop.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telos.desktop.linux.Sys
import com.telos.desktop.ui.TelosColors
import kotlinx.coroutines.delay
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

data class ClipItem(val text: String, var pinned: Boolean)

object TelosClipboardManager {
    val history = mutableStateListOf<ClipItem>()
    var popup by androidx.compose.runtime.mutableStateOf(false)
    private var last = ""

    suspend fun pollLoop() {
        while (true) {
            val text = current()
            if (!text.isNullOrBlank() && text != last) {
                last = text
                if (history.none { it.text == text }) {
                    history.add(0, ClipItem(text, false))
                    while (history.count { !it.pinned } > 50) {
                        val i = history.indexOfLast { !it.pinned }
                        if (i >= 0) history.removeAt(i) else break
                    }
                }
            }
            delay(700)
        }
    }

    fun current(): String? {
        val wl = Sys.which("wl-paste")
        if (wl != null) {
            val t = Sys.run(wl).stdout
            if (t.isNotBlank()) return t
        }
        return runCatching {
            Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.stringFlavor) as? String
        }.getOrNull()
    }

    fun set(text: String) {
        val copy = Sys.which("wl-copy")
        if (copy != null) Sys.run(copy, input = text)
        runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) }
    }
}

@Composable
fun ClipboardPopup() {
    LaunchedEffect(Unit) { TelosClipboardManager.pollLoop() }
    if (!TelosClipboardManager.popup) return
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .width(360.dp)
            .heightIn(max = 420.dp)
            .clip(shape)
            .background(TelosColors.menu)
            .border(1.dp, TelosColors.outline, shape)
            .padding(8.dp),
    ) {
        Text("Clipboard", color = TelosColors.secondary, modifier = Modifier.padding(8.dp))
        LazyColumn {
            items(TelosClipboardManager.history, key = { it.text.hashCode() }) { item ->
                Text(
                    (if (item.pinned) "★ " else "") + item.text.take(120),
                    color = TelosColors.onSurface,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth().clickable {
                        TelosClipboardManager.set(item.text)
                        TelosClipboardManager.popup = false
                    }.padding(8.dp),
                )
            }
        }
    }
}
