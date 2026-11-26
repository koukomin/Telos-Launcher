package de.mm20.launcher2.ui.component

import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
enum class TrustLevel {
    Verified, High, Medium, Low, Unknown
}

/**
 * High-performance, Coracle-style Web-of-Trust (WoT) trust score badge component.
 * Displays mutual follow degree and Nostr Web of Trust rank with Material 3 styling.
 */
@Composable
fun WebOfTrustBadge(
    score: Int,
    trustLevel: TrustLevel,
    modifier: Modifier = Modifier,
) {
    val (bgColor, fgColor, iconRes) = when (trustLevel) {
        TrustLevel.Verified -> Triple(Color(0xFF1B5E20), Color(0xFF81C784), BaseR.drawable.ic_calc_vat)
        TrustLevel.High -> Triple(Color(0xFF0D47A1), Color(0xFF64B5F6), BaseR.drawable.info_20px)
        TrustLevel.Medium -> Triple(Color(0xFFE65100), Color(0xFFFFB74D), BaseR.drawable.info_20px)
        TrustLevel.Low -> Triple(Color(0xFFB71C1C), Color(0xFFE57373), BaseR.drawable.info_20px)
        TrustLevel.Unknown -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, BaseR.drawable.info_20px)
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor.copy(alpha = 0.2f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = fgColor,
            modifier = Modifier.size(12.dp)
        )
        Text(
            text = if (trustLevel == TrustLevel.Verified) "WoT Verified" else "WoT $score%",
            color = fgColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}
