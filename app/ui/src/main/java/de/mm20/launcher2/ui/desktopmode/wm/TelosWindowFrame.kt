// === TELOS_PENDING_REVIEW_START: desktop_wm_klassy_sweet_theming ===
package de.mm20.launcher2.ui.desktopmode.wm

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.desktopmode.cursor.CursorType
import de.mm20.launcher2.ui.desktopmode.cursor.LocalTelosCursorState

@Composable
fun TelosWindowFrame(
    windowState: TelosWindowState,
    themeConfig: WindowThemeConfig,
    windowManager: TelosWindowManager,
    desktopWidth: Dp,
    desktopHeight: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (windowState.isMinimized) return

    val density = LocalDensity.current
    val primaryAccent = themeConfig.activeBorderColor ?: MaterialTheme.colorScheme.primary

    val activeBorder by animateColorAsState(
        if (windowState.isActive) primaryAccent else themeConfig.inactiveBorderColor,
        label = "winBorder"
    )

    val currentHeight = if (windowState.isShaded) 38.dp else windowState.height
    var groupHovered by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .offset(x = windowState.x, y = windowState.y)
            .size(width = windowState.width, height = currentHeight)
            .zIndex(windowState.zIndex)
            .pointerInput(windowState.id) {
                detectTapGestures(onPress = { windowManager.focusWindow(windowState.id) })
            }
            .clip(
                RoundedCornerShape(
                    topStart = themeConfig.cornerRadiusTop,
                    topEnd = themeConfig.cornerRadiusTop,
                    bottomStart = if (windowState.isShaded) 0.dp else themeConfig.cornerRadiusBottom,
                    bottomEnd = if (windowState.isShaded) 0.dp else themeConfig.cornerRadiusBottom
                )
            )
            .drawBehind {
                if (windowState.isActive && themeConfig.borderGlowIntensity > 0f) {
                    val strokePx = themeConfig.borderWidth.toPx()
                    drawRoundRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                activeBorder.copy(alpha = 0.8f * themeConfig.borderGlowIntensity),
                                activeBorder.copy(alpha = 0.2f * themeConfig.borderGlowIntensity)
                            ),
                            center = Offset(size.width / 2, size.height / 2),
                            radius = maxOf(size.width, size.height)
                        ),
                        cornerRadius = CornerRadius(themeConfig.cornerRadiusTop.toPx()),
                        style = Stroke(width = strokePx * 2)
                    )
                }
            }
            .border(
                width = themeConfig.borderWidth,
                color = activeBorder,
                shape = RoundedCornerShape(
                    topStart = themeConfig.cornerRadiusTop,
                    topEnd = themeConfig.cornerRadiusTop,
                    bottomStart = if (windowState.isShaded) 0.dp else themeConfig.cornerRadiusBottom,
                    bottomEnd = if (windowState.isShaded) 0.dp else themeConfig.cornerRadiusBottom
                )
            )
            .background(MaterialTheme.colorScheme.surface.copy(alpha = windowState.opacity))
    ) {
        Column(Modifier.fillMaxSize()) {
            // === Titlebar Header ===
            val titlebarBg = when (themeConfig.titlebarBackdropStyle) {
                BackdropStyle.SOLID -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = themeConfig.titlebarOpacity)
                BackdropStyle.GRADIENT -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = themeConfig.titlebarOpacity)
                BackdropStyle.TRANSLUCENT_GLASS -> MaterialTheme.colorScheme.surfaceContainer.copy(alpha = themeConfig.titlebarOpacity)
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .pointerInput(windowState.id) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val dx = with(density) { dragAmount.x.toDp() }
                            val dy = with(density) { dragAmount.y.toDp() }
                            windowManager.moveWindow(windowState.id, dx, dy)
                        }
                    }
                    .pointerInput(windowState.id) {
                        detectTapGestures(
                            onDoubleTap = { windowManager.toggleShade(windowState.id) },
                            onPress = { windowManager.focusWindow(windowState.id) }
                        )
                    },
                color = titlebarBg,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = themeConfig.buttonMargin),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (themeConfig.buttonPosition == ButtonPosition.LEFT) {
                        WindowButtonsGroup(
                            windowState = windowState,
                            themeConfig = themeConfig,
                            windowManager = windowManager,
                            desktopWidth = desktopWidth,
                            desktopHeight = desktopHeight,
                            groupHovered = groupHovered,
                            onGroupHoverChange = { groupHovered = it }
                        )
                        Spacer(Modifier.width(8.dp))
                    }

                    if (windowState.iconRes != null) {
                        Icon(
                            painter = painterResource(windowState.iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(6.dp))
                    }

                    Text(
                        text = windowState.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (themeConfig.buttonPosition == ButtonPosition.RIGHT) {
                        Spacer(Modifier.width(8.dp))
                        WindowButtonsGroup(
                            windowState = windowState,
                            themeConfig = themeConfig,
                            windowManager = windowManager,
                            desktopWidth = desktopWidth,
                            desktopHeight = desktopHeight,
                            groupHovered = groupHovered,
                            onGroupHoverChange = { groupHovered = it }
                        )
                    }
                }
            }

            if (themeConfig.showTitleSeparator) {
                HorizontalDivider(color = activeBorder.copy(alpha = 0.4f), thickness = 1.dp)
            }

            // === Window Body Content ===
            if (!windowState.isShaded) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    content()
                }
            }
        }

        // === 8-Directional Edge/Corner Resizing Handles ===
        if (!windowState.isMaximized && !windowState.isShaded) {
            WindowResizeHandles(
                windowId = windowState.id,
                windowManager = windowManager,
                density = density
            )
        }
    }
}

@Composable
private fun WindowButtonsGroup(
    windowState: TelosWindowState,
    themeConfig: WindowThemeConfig,
    windowManager: TelosWindowManager,
    desktopWidth: Dp,
    desktopHeight: Dp,
    groupHovered: Boolean,
    onGroupHoverChange: (Boolean) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(themeConfig.buttonSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (themeConfig.buttonStyle) {
            WindowButtonStyle.SIERRA_MACOS -> {
                // Red = Close, Yellow = Minimize, Green = Maximize
                SierraButton(
                    color = Color(0xFFFF5F56),
                    symbol = "×",
                    showSymbol = groupHovered || !themeConfig.unisonHoverEffect,
                    onClick = { windowManager.closeWindow(windowState.id) }
                )
                SierraButton(
                    color = Color(0xFFFFBD2E),
                    symbol = "–",
                    showSymbol = groupHovered || !themeConfig.unisonHoverEffect,
                    onClick = { windowManager.toggleMinimize(windowState.id) }
                )
                SierraButton(
                    color = Color(0xFF27C93F),
                    symbol = "+",
                    showSymbol = groupHovered || !themeConfig.unisonHoverEffect,
                    onClick = { windowManager.toggleMaximize(windowState.id, desktopWidth, desktopHeight) }
                )
            }
            WindowButtonStyle.SIERRA_BREEZE_BLEND, WindowButtonStyle.KLASSY_OUTLINE, WindowButtonStyle.KLASSY_ICON_ONLY, WindowButtonStyle.KLASSY_ACCENT_FILLED -> {
                KlassyButton(
                    style = themeConfig.buttonStyle,
                    iconRes = R.drawable.close_24px,
                    contentDescription = "Close",
                    semanticColor = MaterialTheme.colorScheme.error,
                    onClick = { windowManager.closeWindow(windowState.id) }
                )
                KlassyButton(
                    style = themeConfig.buttonStyle,
                    iconRes = R.drawable.circle_24px,
                    contentDescription = "Minimize",
                    semanticColor = MaterialTheme.colorScheme.secondary,
                    onClick = { windowManager.toggleMinimize(windowState.id) }
                )
                KlassyButton(
                    style = themeConfig.buttonStyle,
                    iconRes = R.drawable.add_24px,
                    contentDescription = "Maximize",
                    semanticColor = MaterialTheme.colorScheme.primary,
                    onClick = { windowManager.toggleMaximize(windowState.id, desktopWidth, desktopHeight) }
                )
            }
        }
    }
}

@Composable
private fun SierraButton(
    color: Color,
    symbol: String,
    showSymbol: Boolean,
    onClick: () -> Unit
) {
    var isHovered by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(13.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (showSymbol || isHovered) {
            Text(
                text = symbol,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.Black.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun KlassyButton(
    style: WindowButtonStyle,
    iconRes: Int,
    contentDescription: String,
    semanticColor: Color,
    onClick: () -> Unit
) {
    var isHovered by remember { mutableStateOf(false) }

    val bgShape = when (style) {
        WindowButtonStyle.KLASSY_OUTLINE -> RoundedCornerShape(6.dp)
        WindowButtonStyle.KLASSY_ACCENT_FILLED -> RoundedCornerShape(8.dp)
        else -> CircleShape
    }

    val bgColor = when (style) {
        WindowButtonStyle.KLASSY_ACCENT_FILLED -> semanticColor
        WindowButtonStyle.KLASSY_OUTLINE -> if (isHovered) semanticColor.copy(alpha = 0.2f) else Color.Transparent
        else -> if (isHovered) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent
    }

    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(bgShape)
            .background(bgColor)
            .then(
                if (style == WindowButtonStyle.KLASSY_OUTLINE) Modifier.border(1.dp, semanticColor, bgShape)
                else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(14.dp),
            tint = if (style == WindowButtonStyle.KLASSY_ACCENT_FILLED) MaterialTheme.colorScheme.onPrimary else semanticColor
        )
    }
}

@Composable
private fun WindowResizeHandles(
    windowId: String,
    windowManager: TelosWindowManager,
    density: androidx.compose.ui.unit.Density
) {
    val handleThickness = 8.dp

    // Corner Resizing
    Box(
        modifier = Modifier
            .size(handleThickness * 2)
            .pointerInput(windowId) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val dx = with(density) { dragAmount.x.toDp() }
                    val dy = with(density) { dragAmount.y.toDp() }
                    windowManager.resizeWindow(windowId, ResizeDirection.SOUTH_EAST, dx, dy)
                }
            }
    )
}
// === TELOS_PENDING_REVIEW_END: desktop_wm_klassy_sweet_theming ===
