package com.telos.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telos.desktop.theme.DesktopThemeChrome
import de.mm20.launcher2.uikmp.TelosSurfaceTokens

/** Desktop Cyber-Tech pill (opaque [TelosSurfaceTokens.techPill]; neon accent when selected). */
@Composable
fun HelixTechPill(
    text: String,
    selected: Boolean,
    accent: Color = TelosColors.secondary,
    modifier: Modifier = Modifier,
    scale: Float = DesktopThemeChrome.snapPillScale,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(999.dp)
    val fillA = DesktopThemeChrome.selectedPillFillAlpha
    val borderA = if (selected) DesktopThemeChrome.techBorderAlpha else 0.55f
    val bg = if (selected) accent.copy(alpha = fillA) else TelosSurfaceTokens.techPill
    val border = if (selected) accent.copy(alpha = borderA) else TelosColors.outline
    val fg = if (selected) accent else TelosColors.onSurface
    val hPad = (12f * scale).dp
    val vPad = (7f * scale).dp
    val font = (12f * scale).sp
    Text(
        text,
        color = fg,
        fontSize = font,
        maxLines = 1,
        modifier = modifier
            .clip(shape)
            .background(bg)
            .border(1.2.dp, border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = hPad, vertical = vPad),
    )
}
