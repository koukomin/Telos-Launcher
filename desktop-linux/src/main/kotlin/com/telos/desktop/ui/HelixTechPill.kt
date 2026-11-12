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
import de.mm20.launcher2.uikmp.TelosSurfaceTokens

/** Desktop Cyber-Tech pill (opaque [TelosSurfaceTokens.techPill]; neon accent when selected). */
@Composable
fun HelixTechPill(
    text: String,
    selected: Boolean,
    accent: Color = TelosColors.secondary,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(999.dp)
    val bg = if (selected) accent.copy(alpha = 0.22f) else TelosSurfaceTokens.techPill
    val border = if (selected) accent else TelosColors.outline
    val fg = if (selected) accent else TelosColors.onSurface
    Text(
        text,
        color = fg,
        fontSize = 12.sp,
        maxLines = 1,
        modifier = modifier
            .clip(shape)
            .background(bg)
            .border(1.2.dp, border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}
