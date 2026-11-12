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
import com.telos.desktop.vault.ClipboardHistoryItem
import com.telos.desktop.vault.ClipboardHistoryStore
import com.telos.desktop.vault.PhantomVault
import com.telos.desktop.vault.TelosOpSecGuard
import kotlinx.coroutines.delay
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

object TelosClipboardManager {
    private val guardProbe = TelosOpSecGuard()
    val store = ClipboardHistoryStore(
        looksSensitive = { text ->
            runCatching { PhantomVault.engine.opsec.looksSensitive(text) }
                .getOrElse { guardProbe.looksSensitive(text) }
        },
    )
    /** Compose-visible mirror of [store] (newest first). */
    val history = mutableStateListOf<ClipboardHistoryItem>()
    var popup by mutableStateOf(false)
    private var last = ""
    private var vaultLoaded = false

    fun ensureLoaded() {
        if (vaultLoaded) return
        vaultLoaded = true
        runCatching { store.loadFromVault() }
        refreshUi()
    }

    fun refreshUi() {
        val snap = store.snapshot()
        if (history.toList() == snap) return
        history.clear()
        history.addAll(snap)
    }

    suspend fun pollLoop() {
        ensureLoaded()
        while (true) {
            store.expire()
            refreshUi()
            val text = current()
            if (!text.isNullOrBlank() && text != last) {
                last = text
                val vaulted = runCatching {
                    PhantomVault.engine.opsec.handleClipboardText(text)
                }.getOrDefault(false)
                if (!vaulted) {
                    store.offer(text)
                    refreshUi()
                } else {
                    // Sensitive: scrub from history; OpSec keeps a single vaulted recall slot.
                    store.expire()
                    refreshUi()
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
        val vaulted = runCatching { PhantomVault.engine.opsec.handleClipboardText(text) }.getOrDefault(false)
        last = text
        if (!vaulted) {
            store.offer(text)
            refreshUi()
        }
    }

    fun togglePin(id: Long) {
        store.togglePinned(id)
        refreshUi()
    }

    fun clearHistory() {
        store.clear()
        last = ""
        refreshUi()
    }

    fun clearSystemAndHistory() {
        runCatching { PhantomVault.engine.opsec.clearSystemClipboard() }
        clearHistory()
        runCatching { PhantomVault.engine.opsec.clearVaultedClip() }
    }

    /** Session lock / vault lock: pin-until-lock expires; wipe vault file. */
    fun onSessionLock() {
        store.onLock()
        last = ""
        refreshUi()
        runCatching { PhantomVault.engine.opsec.clearVaultedClip() }
        runCatching { PhantomVault.engine.opsec.clearSystemClipboard() }
    }

    fun hasVaultedRecall(): Boolean =
        runCatching { !PhantomVault.engine.opsec.vaultedClip().isNullOrBlank() }.getOrDefault(false)
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
            .width(380.dp)
            .heightIn(max = 440.dp)
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
                Text(t("desktop_opsec_clipboard_clear_all"), fontSize = 11.sp)
            }
        }
        Text(
            t("desktop_opsec_clipboard_hint"),
            color = TelosColors.onSurface.copy(alpha = 0.65f),
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
        Text(
            t("desktop_opsec_clipboard_history_meta"),
            color = TelosColors.onSurface.copy(alpha = 0.55f),
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
        if (TelosClipboardManager.hasVaultedRecall()) {
            Text(
                t("desktop_opsec_clipboard_recall_vaulted"),
                color = TelosColors.secondary,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().clickable {
                    runCatching { PhantomVault.engine.opsec.recallVaultedClip() }
                    TelosClipboardManager.popup = false
                }.padding(8.dp),
            )
        }
        if (TelosClipboardManager.history.isEmpty()) {
            Text(
                t("desktop_opsec_clipboard_history_empty"),
                color = TelosColors.onSurface.copy(alpha = 0.55f),
                fontSize = 12.sp,
                modifier = Modifier.padding(8.dp),
            )
        } else {
            LazyColumn {
                items(TelosClipboardManager.history, key = { it.id }) { item ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    ) {
                        Text(
                            (if (item.pinned) "★ " else "") + item.text.take(120),
                            color = TelosColors.onSurface,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    TelosClipboardManager.set(item.text)
                                    TelosClipboardManager.popup = false
                                }
                                .padding(8.dp),
                        )
                        TextButton(onClick = { TelosClipboardManager.togglePin(item.id) }) {
                            Text(
                                if (item.pinned) {
                                    t("desktop_opsec_clipboard_unpin")
                                } else {
                                    t("desktop_opsec_clipboard_pin_until_lock")
                                },
                                fontSize = 10.sp,
                            )
                        }
                    }
                }
            }
        }
    }
    }
}
