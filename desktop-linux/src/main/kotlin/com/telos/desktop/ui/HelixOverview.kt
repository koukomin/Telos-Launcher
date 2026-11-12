package com.telos.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telos.desktop.i18n.TelosI18n.t
import com.telos.desktop.settings.TelosDesktopSettings
import com.telos.desktop.wm.SnapLayout
import com.telos.desktop.wm.TelosWindowManager
import com.telos.desktop.wm.WindowMode

@Composable
fun HelixOverview(
    wm: TelosWindowManager,
    settings: TelosDesktopSettings? = null,
    onDismiss: () -> Unit,
) {
    Box(
        Modifier.fillMaxSize().background(TelosColors.background.copy(alpha = 0.88f)).clickable { onDismiss() },
    ) {
        Column(
            Modifier.fillMaxSize().padding(48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HelixSnapZonesBar(wm)
            if (settings != null) {
                HelixWindowModeBar(settings)
            }
            for (row in 0..1) {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    for (col in 0..1) {
                        val i = row * 2 + col
                        WorkspaceTile(wm, i, onDismiss, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
internal fun HelixSnapZonesBar(wm: TelosWindowManager) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(TelosColors.menu)
            .border(1.dp, TelosColors.outline, shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(t("desktop_helix_snap_title"), color = TelosColors.secondary, fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HelixTechPill(t("desktop_helix_snap_50_50"), selected = false, accent = TelosColors.secondary) {
                wm.snapFocused(SnapLayout.HalfLeft)
            }
            HelixTechPill(t("desktop_helix_snap_two_thirds"), selected = false, accent = TelosColors.primary) {
                wm.snapFocused(SnapLayout.TwoThirdsLeft)
            }
            HelixTechPill(t("desktop_helix_snap_grid"), selected = false, accent = ColorNeonGreen) {
                wm.snapFocused(SnapLayout.GridTopLeft)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HelixTechPill(t("desktop_helix_snap_left"), selected = false, accent = TelosColors.secondary) {
                wm.cycleSnapLeft()
            }
            HelixTechPill(t("desktop_helix_snap_right"), selected = false, accent = TelosColors.secondary) {
                wm.cycleSnapRight()
            }
            HelixTechPill(t("desktop_helix_snap_maximize"), selected = false, accent = TelosColors.primary) {
                wm.snapFocused(SnapLayout.Maximize)
            }
        }
    }
}

@Composable
internal fun HelixWindowModeBar(settings: TelosDesktopSettings) {
    val mode = WindowMode.parse(settings.windowMode)
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(TelosColors.menu)
            .border(1.dp, TelosColors.outline, shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(t("desktop_helix_window_mode"), color = TelosColors.secondary, fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HelixTechPill(
                t("desktop_helix_tiling"),
                selected = mode == WindowMode.Tiling,
                accent = TelosColors.secondary,
            ) {
                settings.windowMode = WindowMode.Tiling.propertyValue
                settings.save()
            }
            HelixTechPill(
                t("desktop_helix_stacking"),
                selected = mode == WindowMode.Stacking,
                accent = TelosColors.primary,
            ) {
                settings.windowMode = WindowMode.Stacking.propertyValue
                settings.save()
            }
        }
        Text(t("desktop_helix_window_mode_hint"), color = TelosColors.onSurface.copy(0.7f), fontSize = 11.sp)
    }
}

private val ColorNeonGreen = androidx.compose.ui.graphics.Color(0xFF00E676)

@Composable
private fun WorkspaceTile(wm: TelosWindowManager, i: Int, onDismiss: () -> Unit, modifier: Modifier) {
    val active = wm.currentWorkspace == i
    val wins = wm.windows.filter { it.workspace == i && !it.minimized }
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .fillMaxSize()
            .clip(shape)
            .background(if (active) TelosColors.surfaceHigh else TelosColors.surfaceContainer)
            .border(1.dp, if (active) TelosColors.secondary else TelosColors.outline, shape)
            .clickable {
                wm.switchWorkspace(i)
                onDismiss()
            }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Desktop ${i + 1}", color = TelosColors.secondary, fontSize = 16.sp)
        if (wins.isEmpty()) {
            Text("Empty", color = TelosColors.onSurface.copy(0.6f), fontSize = 13.sp)
        } else {
            wins.take(8).forEach { w ->
                Text(w.title, color = TelosColors.onSurface, fontSize = 13.sp, maxLines = 1)
            }
        }
    }
}
