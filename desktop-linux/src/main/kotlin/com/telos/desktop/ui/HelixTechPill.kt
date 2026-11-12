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
import com.telos.desktop.theme.PanicMuteController
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
    val techBorders = PanicMuteController.showTechBorders()
    val shape = RoundedCornerShape(999.dp)
    val bg = when {
        !techBorders -> TelosSurfaceTokens.techPill
        selected -> accent.copy(alpha = 0.22f)
        else -> TelosSurfaceTokens.techPill
    }
    val borderColor = when {
        !techBorders -> TelosColors.outline.copy(alpha = 0.35f)
        selected -> accent
        else -> TelosColors.outline
    }
    val fg = when {
        !techBorders -> TelosColors.onSurface
        selected -> accent
        else -> TelosColors.onSurface
    }
    Text(
        text,
        color = fg,
        fontSize = 12.sp,
        maxLines = 1,
        modifier = modifier
            .clip(shape)
            .background(bg)
            .border(if (techBorders) 1.2.dp else 1.dp, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}
