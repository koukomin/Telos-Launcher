package com.telos.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telos.desktop.apps.DesktopEntry
import com.telos.desktop.apps.DesktopFileLauncher
import com.telos.desktop.apps.LinuxAppScanner
import com.telos.desktop.apps.TelosFileHandler
import com.telos.desktop.apps.TelosVirtualApp
import com.telos.desktop.apps.TelosVirtualApps
import com.telos.desktop.i18n.TelosI18n.t
import com.telos.desktop.icons.CandyCyberIcon
import com.telos.desktop.icons.CandyIcon
import com.telos.desktop.icons.LinuxIconThemeResolver
import com.telos.desktop.linux.Sys
import com.telos.desktop.overlay.TelosClipboardManager
import com.telos.desktop.search.HelixPaletteActions
import com.telos.desktop.search.HelixPaletteItem
import com.telos.desktop.search.HelixPaletteKind
import com.telos.desktop.search.HelixPaletteWindow
import com.telos.desktop.vault.PhantomVault
import com.telos.desktop.wm.TelosWindowManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Privacy-safe global command palette (KRunner / PowerToys Run class).
 * Opaque [TelosColors.menu] / surfaceContainer Cyber-Tech popup — never a cleared surface.
 * Toggle: Super+Period or Ctrl+Space (Kickoff stays Super+Space).
 */
@Composable
fun HelixCommandPalette(
    visible: Boolean,
    wm: TelosWindowManager,
    icons: LinuxIconThemeResolver,
    onDismiss: () -> Unit,
    onOpenTelos: (TelosVirtualApp) -> Unit,
    onOpenSettings: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableIntStateOf(0) }
    var apps by remember { mutableStateOf<List<DesktopEntry>>(emptyList()) }
    var results by remember { mutableStateOf<List<HelixPaletteItem>>(emptyList()) }
    val focusRequester = remember { FocusRequester() }
    val neon = TelosColors.secondary
    val shape = RoundedCornerShape(14.dp)

    val labels = remember {
        HelixPaletteActions.CommandLabels(
            openFiles = t("desktop_helix_palette_open_files"),
            openSettings = t("desktop_helix_palette_open_settings"),
            openNetwork = t("desktop_helix_palette_open_network"),
            killFocused = t("desktop_helix_palette_kill_window"),
            pasteVaultClip = t("desktop_helix_palette_paste_vault_clip"),
            window = t("desktop_helix_palette_window"),
            calculator = t("desktop_helix_palette_calc"),
            command = t("desktop_helix_palette_command"),
        )
    }

    LaunchedEffect(visible) {
        if (visible) {
            query = ""
            selected = 0
            apps = withContext(Dispatchers.IO) { LinuxAppScanner.scan() }
            focusRequester.requestFocus()
        }
    }

    LaunchedEffect(query, apps, visible, wm.focusedId, wm.windows.size, TelosClipboardManager.history.size) {
        if (!visible) return@LaunchedEffect
        val windows = wm.visibleWindows().map { HelixPaletteWindow(it.id, it.title) }
        val vaultClip = TelosClipboardManager.hasVaultedRecall() ||
            TelosClipboardManager.history.isNotEmpty()
        val roots = withContext(Dispatchers.IO) { HelixPaletteActions.privacySafeFileRoots() }
        results = withContext(Dispatchers.Default) {
            HelixPaletteActions.build(
                query = query,
                windows = windows,
                telos = TelosVirtualApps.all,
                apps = apps,
                filesRoot = roots,
                hasFocusedWindow = wm.focusedId != null || Sys.which("wlrctl") != null,
                hasVaultClip = vaultClip,
                commandLabels = labels,
            )
        }
        if (selected >= results.size) selected = 0
    }

    fun activate(item: HelixPaletteItem) {
        when (item.kind) {
            HelixPaletteKind.Calculator -> {
                item.calcValue?.let { TelosClipboardManager.set(it) }
                HelixToast.show(t("desktop_helix_palette_calc"), item.title)
            }
            HelixPaletteKind.Window -> item.windowId?.let { wm.focus(it) }
            HelixPaletteKind.TelosApp -> {
                val app = TelosVirtualApps.all.find { it.domain == item.telosDomain } ?: return
                when (app.windowTitle) {
                    "Settings" -> onOpenSettings()
                    else -> onOpenTelos(app)
                }
            }
            HelixPaletteKind.Desktop -> {
                val path = item.desktopPath ?: return
                apps.find { it.file.absolutePath == path }?.let { DesktopFileLauncher.launch(it) }
            }
            HelixPaletteKind.File -> item.filePath?.let { TelosFileHandler.open(File(it)) }
            HelixPaletteKind.Command -> when (item.commandId) {
                HelixPaletteActions.CMD_FILES ->
                    onOpenTelos(TelosVirtualApps.all.first { it.windowTitle == "Files" })
                HelixPaletteActions.CMD_SETTINGS -> onOpenSettings()
                HelixPaletteActions.CMD_NETWORK ->
                    onOpenTelos(TelosVirtualApps.all.first { it.windowTitle == "Network" })
                HelixPaletteActions.CMD_KILL -> HelixPaletteKill.killFocused(wm)
                HelixPaletteActions.CMD_PASTE_VAULT -> {
                    val text = runCatching { PhantomVault.engine.opsec.recallVaultedClip() }.getOrNull()
                    if (text != null) {
                        HelixToast.show(t("desktop_helix_palette_paste_vault_clip"), text.take(48))
                    }
                }
            }
        }
        onDismiss()
    }

    AnimatedVisibility(
        visible = visible,
        enter = HelixMotion.enter,
        exit = HelixMotion.exit,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { onDismiss() },
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier
                    .padding(top = 96.dp)
                    .widthIn(min = 420.dp, max = 640.dp)
                    .fillMaxWidth(0.92f)
                    .clip(shape)
                    // virtualAppPopup / opaque surfaceContainer — no transparent surface
                    .background(TelosColors.menu)
                    .border(1.5.dp, neon.copy(alpha = 0.85f), shape)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) {}
                    .onPreviewKeyEvent { ev ->
                        if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (ev.key) {
                            Key.Escape -> {
                                onDismiss()
                                true
                            }
                            Key.DirectionDown -> {
                                if (results.isNotEmpty()) {
                                    selected = (selected + 1) % results.size
                                }
                                true
                            }
                            Key.DirectionUp -> {
                                if (results.isNotEmpty()) {
                                    selected = (selected - 1 + results.size) % results.size
                                }
                                true
                            }
                            Key.Enter -> {
                                results.getOrNull(selected)?.let { activate(it) }
                                true
                            }
                            else -> false
                        }
                    }
                    .padding(12.dp),
            ) {
                Text(
                    t("desktop_helix_palette_title"),
                    color = neon,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        selected = 0
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    placeholder = {
                        Text(
                            t("desktop_helix_palette_placeholder"),
                            color = TelosColors.onSurface.copy(alpha = 0.55f),
                        )
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = TelosColors.surfaceContainer,
                        unfocusedContainerColor = TelosColors.surfaceContainer,
                        focusedBorderColor = neon.copy(alpha = 0.55f),
                        unfocusedBorderColor = TelosColors.outline,
                        focusedTextColor = TelosColors.onSurface,
                        unfocusedTextColor = TelosColors.onSurface,
                        cursorColor = neon,
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 18.sp),
                    leadingIcon = { CandyCyberIcon(CandyIcon.Search, 22.dp) },
                )
                Text(
                    t("desktop_helix_palette_privacy"),
                    color = TelosColors.onSurface.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                )
                Box(Modifier.fillMaxWidth().height(1.dp).background(neon.copy(alpha = 0.35f)))
                if (results.isEmpty()) {
                    Text(
                        t("desktop_helix_palette_no_results"),
                        color = TelosColors.onSurface.copy(alpha = 0.6f),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    LazyColumn(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .padding(top = 4.dp),
                    ) {
                        itemsIndexed(results, key = { _, item -> item.id }) { index, item ->
                            val active = index == selected
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (active) TelosColors.surfaceHigh else TelosColors.surfaceContainer,
                                    )
                                    .border(
                                        width = if (active) 1.dp else 0.dp,
                                        color = if (active) neon.copy(alpha = 0.7f) else Color.Transparent,
                                        shape = RoundedCornerShape(10.dp),
                                    )
                                    .clickable { activate(item) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                PaletteLeadingIcon(item, icons, apps)
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        item.title,
                                        color = TelosColors.onSurface,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                    )
                                    if (item.subtitle.isNotBlank()) {
                                        Text(
                                            item.subtitle,
                                            color = TelosColors.onSurface.copy(alpha = 0.55f),
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                        )
                                    }
                                }
                                Text(
                                    kindChip(item.kind),
                                    color = neon.copy(alpha = 0.85f),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaletteLeadingIcon(
    item: HelixPaletteItem,
    icons: LinuxIconThemeResolver,
    apps: List<DesktopEntry>,
) {
    val candy = when (item.kind) {
        HelixPaletteKind.Calculator -> CandyIcon.Calculator
        HelixPaletteKind.Window -> CandyIcon.Monitor
        HelixPaletteKind.Command -> when (item.commandId) {
            HelixPaletteActions.CMD_FILES -> CandyIcon.Folder
            HelixPaletteActions.CMD_SETTINGS -> CandyIcon.Settings
            HelixPaletteActions.CMD_NETWORK -> CandyIcon.Network
            HelixPaletteActions.CMD_KILL -> CandyIcon.Power
            HelixPaletteActions.CMD_PASTE_VAULT -> CandyIcon.Clip
            else -> CandyIcon.Search
        }
        HelixPaletteKind.TelosApp ->
            TelosVirtualApps.all.find { it.domain == item.telosDomain }?.icon ?: CandyIcon.AppGrid
        HelixPaletteKind.File -> CandyIcon.File
        HelixPaletteKind.Desktop -> null
    }
    if (candy != null) {
        CandyCyberIcon(candy, 28.dp)
    } else {
        val entry = apps.find { it.file.absolutePath == item.desktopPath }
        if (entry != null) {
            DiskIcon(icons.resolve(entry.icon), entry.name, 28.dp)
        } else {
            Box(Modifier.size(28.dp).background(TelosColors.surfaceHigh, RoundedCornerShape(6.dp)))
        }
    }
}

private fun kindChip(kind: HelixPaletteKind): String = when (kind) {
    HelixPaletteKind.Calculator -> t("desktop_helix_palette_calc")
    HelixPaletteKind.Window -> t("desktop_helix_palette_window")
    HelixPaletteKind.Command -> t("desktop_helix_palette_command")
    HelixPaletteKind.TelosApp -> "Telos"
    HelixPaletteKind.Desktop -> "App"
    HelixPaletteKind.File -> t("desktop_helix_palette_file")
}

/** Close the focused Telos window; best-effort labwc/host Close via wlrctl. */
object HelixPaletteKill {
    fun killFocused(wm: TelosWindowManager): Boolean {
        wm.focusedId?.let {
            wm.close(it)
            return true
        }
        val wlrctl = Sys.which("wlrctl") ?: return false
        return Sys.run(wlrctl, "toplevel", "close").ok
    }
}
