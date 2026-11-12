package com.telos.desktop.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telos.desktop.i18n.TelosI18n.t
import com.telos.desktop.linux.Sys
import com.telos.desktop.ui.TelosColors
import com.telos.desktop.vault.PhantomVault
import kotlinx.coroutines.delay
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

data class ClipItem(val text: String, var pinned: Boolean, val vaulted: Boolean = false)

object TelosClipboardManager {
    val history = mutableStateListOf<ClipItem>()
    var popup by mutableStateOf(false)
    private var last = ""

    suspend fun pollLoop() {
        while (true) {
            val text = current()
            if (!text.isNullOrBlank() && text != last) {
                last = text
                val vaulted = runCatching {
                    PhantomVault.engine.opsec.handleClipboardText(text)
                }.getOrDefault(false)
                if (vaulted) {
                    if (history.none { it.vaulted && it.text == text }) {
                        history.add(0, ClipItem(text, pinned = true, vaulted = true))
                    }
                } else if (history.none { it.text == text }) {
                    history.add(0, ClipItem(text, false))
                    while (history.count { !it.pinned && !it.vaulted } > 50) {
                        val i = history.indexOfLast { !it.pinned && !it.vaulted }
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
        runCatching { PhantomVault.engine.opsec.handleClipboardText(text) }
    }

    fun clearHistory() {
        history.clear()
        last = ""
    }

    fun clearSystemAndHistory() {
        runCatching { PhantomVault.engine.opsec.clearSystemClipboard() }
        clearHistory()
        runCatching { PhantomVault.engine.opsec.clearVaultedClip() }
    }
}

@Composable
fun ClipboardPopup() {
    LaunchedEffect(Unit) { TelosClipboardManager.pollLoop() }
    androidx.compose.animation.AnimatedVisibility(
        TelosClipboardManager.popup,
        enter = com.telos.desktop.ui.HelixMotion.enter,
        exit = com.telos.desktop.ui.HelixMotion.exit,
    ) {
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
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(
                t("desktop_opsec_clipboard_title"),
                color = TelosColors.secondary,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { TelosClipboardManager.clearSystemAndHistory() }) {
                Text(t("desktop_opsec_clipboard_clear_now"), fontSize = 11.sp)
            }
        }
        Text(
            t("desktop_opsec_clipboard_hint"),
            color = TelosColors.onSurface.copy(alpha = 0.65f),
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
        LazyColumn {
            items(TelosClipboardManager.history, key = { it.text.hashCode() xor it.vaulted.hashCode() }) { item ->
                val prefix = when {
                    item.vaulted -> "🔒 "
                    item.pinned -> "★ "
                    else -> ""
                }
                Text(
                    prefix + item.text.take(120),
                    color = TelosColors.onSurface,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth().clickable {
                        if (item.vaulted) {
                            runCatching { PhantomVault.engine.opsec.recallVaultedClip() }
                        } else {
                            TelosClipboardManager.set(item.text)
                        }
                        TelosClipboardManager.popup = false
                    }.padding(8.dp),
                )
            }
        }
    }
    }
}
