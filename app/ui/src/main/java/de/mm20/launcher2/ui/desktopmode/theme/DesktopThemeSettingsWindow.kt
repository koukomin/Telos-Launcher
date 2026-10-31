// === TELOS_PENDING_REVIEW_START: desktop_wm_klassy_sweet_theming ===
package de.mm20.launcher2.ui.desktopmode.theme
import de.mm20.launcher2.base.R as BaseR

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.desktopmode.cursor.CursorConfig
import de.mm20.launcher2.ui.desktopmode.cursor.CursorThemePreset
import de.mm20.launcher2.ui.desktopmode.wm.ButtonPosition
import de.mm20.launcher2.ui.desktopmode.wm.WindowButtonStyle
import de.mm20.launcher2.ui.desktopmode.wm.WindowDecorationPreset
import de.mm20.launcher2.ui.desktopmode.wm.WindowThemeConfig

@Composable
fun DesktopThemeSettingsWindow(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember(context) { DesktopThemeRepository.getInstance(context) }
    val windowTheme by repository.windowTheme.collectAsStateWithLifecycle()
    val cursorConfig by repository.cursorConfig.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }

    Surface(
        modifier = modifier
            .width(520.dp)
            .height(580.dp)
            .clip(RoundedCornerShape(16.dp)),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 10.dp,
        shadowElevation = 12.dp
    ) {
        Column(Modifier.fillMaxSize()) {
            // Window Titlebar
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.fillMaxWidth().height(44.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(BaseR.drawable.palette_24px),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Desktop Theme & Window Manager",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(painterResource(BaseR.drawable.close_24px), contentDescription = "Close")
                    }
                }
            }

            // Tab Selector
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Window Decorations") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Sweet Cursors") }
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (selectedTab == 0) {
                    WindowDecorationsTab(
                        config = windowTheme,
                        onUpdateConfig = { repository.updateWindowTheme(it) },
                        onUpdatePreset = { repository.updateWindowPreset(it) }
                    )
                } else {
                    CursorThemeTab(
                        config = cursorConfig,
                        onUpdateConfig = { repository.updateCursorConfig(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun WindowDecorationsTab(
    config: WindowThemeConfig,
    onUpdateConfig: (WindowThemeConfig) -> Unit,
    onUpdatePreset: (WindowDecorationPreset) -> Unit
) {
    Text("Klassy & SierraBreeze Presets", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)

    // Preset Chips
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        WindowDecorationPreset.entries.forEach { preset ->
            FilterChip(
                selected = config.preset == preset,
                onClick = { onUpdatePreset(preset) },
                label = { Text(preset.name.replace("_", " "), style = MaterialTheme.typography.labelSmall) }
            )
        }
    }

    HorizontalDivider()

    Text("Button Style & Position", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)

    // Button Style
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(
            WindowButtonStyle.SIERRA_MACOS to "macOS Traffic",
            WindowButtonStyle.SIERRA_BREEZE_BLEND to "Breeze Blend",
            WindowButtonStyle.KLASSY_OUTLINE to "Klassy Outline",
            WindowButtonStyle.KLASSY_ICON_ONLY to "Icon Only"
        ).forEach { (style, label) ->
            FilterChip(
                selected = config.buttonStyle == style,
                onClick = { onUpdateConfig(config.copy(buttonStyle = style)) },
                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
            )
        }
    }

    // Button Position
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("Button Side (Left / Right)", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = config.buttonPosition == ButtonPosition.LEFT,
                onClick = { onUpdateConfig(config.copy(buttonPosition = ButtonPosition.LEFT)) },
                label = { Text("Left") }
            )
            FilterChip(
                selected = config.buttonPosition == ButtonPosition.RIGHT,
                onClick = { onUpdateConfig(config.copy(buttonPosition = ButtonPosition.RIGHT)) },
                label = { Text("Right") }
            )
        }
    }

    HorizontalDivider()

    // Corner Radius
    Text("Frame Corner Radii: ${config.cornerRadiusTop.value.toInt()}dp", style = MaterialTheme.typography.bodyMedium)
    Slider(
        value = config.cornerRadiusTop.value,
        onValueChange = { onUpdateConfig(config.copy(cornerRadiusTop = it.dp, cornerRadiusBottom = it.dp)) },
        valueRange = 0f..24f
    )

    // Border Glow Intensity
    Text("Active Border Glow: ${(config.borderGlowIntensity * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
    Slider(
        value = config.borderGlowIntensity,
        onValueChange = { onUpdateConfig(config.copy(borderGlowIntensity = it)) },
        valueRange = 0f..1f
    )

    // Titlebar Opacity
    Text("Titlebar Glass Opacity: ${(config.titlebarOpacity * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
    Slider(
        value = config.titlebarOpacity,
        onValueChange = { onUpdateConfig(config.copy(titlebarOpacity = it)) },
        valueRange = 0.2f..1.0f
    )

    // Unison Hover (SierraBreeze)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text("Sierra Unison Hover Effect", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text("Show all button symbols when hovering anywhere over buttons", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = config.unisonHoverEffect,
            onCheckedChange = { onUpdateConfig(config.copy(unisonHoverEffect = it)) }
        )
    }
}

@Composable
private fun CursorThemeTab(
    config: CursorConfig,
    onUpdateConfig: (CursorConfig) -> Unit
) {
    Text("Sweet Cursor Presets", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)

    // Preset Chips
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CursorThemePreset.entries.forEach { preset ->
            FilterChip(
                selected = config.preset == preset,
                onClick = { onUpdateConfig(config.copy(preset = preset)) },
                label = { Text(preset.name.replace("_", " "), style = MaterialTheme.typography.labelSmall) }
            )
        }
    }

    HorizontalDivider()

    // Cursor Size
    Text("Cursor Size: ${config.size.value.toInt()}dp", style = MaterialTheme.typography.bodyMedium)
    Slider(
        value = config.size.value,
        onValueChange = { onUpdateConfig(config.copy(size = it.dp)) },
        valueRange = 24f..48f
    )

    // Neon Glow
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text("Neon Shimmer Glow", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text("Outer vibrant neon gradient aura for Sweet Cursor", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = config.showNeonGlow,
            onCheckedChange = { onUpdateConfig(config.copy(showNeonGlow = it)) }
        )
    }

    // Click Ripple Pulse
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text("Click Ripple Pulse", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text("Expanding neon gradient ring on pointer click", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = config.showClickPulse,
            onCheckedChange = { onUpdateConfig(config.copy(showClickPulse = it)) }
        )
    }
}
// === TELOS_PENDING_REVIEW_END: desktop_wm_klassy_sweet_theming ===
