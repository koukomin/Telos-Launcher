package de.mm20.launcher2.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Dot-Matrix / Monospace Tactical Tech Text.
 */
@Composable
fun TelosDotMatrixText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    fontSize: TextUnit = 14.sp,
    fontWeight: FontWeight = FontWeight.Bold,
    letterSpacing: TextUnit = 2.sp
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        fontFamily = FontFamily.Monospace,
        letterSpacing = letterSpacing
    )
}
