package de.mm20.launcher2.ui.comms

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import de.mm20.launcher2.comms.search.GreekText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import de.mm20.launcher2.comms.model.CallLogEntry
import de.mm20.launcher2.comms.model.CallType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal val CommsCardShape = RoundedCornerShape(24.dp)
internal val CommsRowShape = RoundedCornerShape(20.dp)
internal val RdGreenCall = Color(0xFF34C759)
internal val RdRedCall = Color(0xFFFF3B30)
internal val RdSwipePurple = Color(0xFF5E5CE6)

private val AvatarPalette = listOf(
    Color(0xFF5B8DEF),
    Color(0xFF30D158),
    Color(0xFFFF9F0A),
    Color(0xFFFF453A),
    Color(0xFFBF5AF2),
    Color(0xFF64D2FF),
    Color(0xFFFF375F),
    Color(0xFF5856D6),
)

internal fun avatarColorFor(name: String): Color {
    if (name.isEmpty()) return AvatarPalette[0]
    return AvatarPalette[name.hashCode().and(0x7fffffff) % AvatarPalette.size]
}

@Composable
internal fun EmptyCommsTab(title: String, message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun CommsAvatar(
    name: String,
    photoUri: String?,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(avatarColorFor(name)),
        contentAlignment = Alignment.Center,
    ) {
        if (photoUri != null) {
            AsyncImage(
                model = photoUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            val initials = name.trim().split(Regex("\\s+")).take(2)
                .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
                .joinToString("")
                .ifEmpty { "?" }
            Text(
                text = initials,
                style = if (size >= 88.dp) {
                    MaterialTheme.typography.headlineMedium
                } else if (size >= 56.dp) {
                    MaterialTheme.typography.titleLarge
                } else {
                    MaterialTheme.typography.titleSmall
                },
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
        }
    }
}

@Composable
internal fun CommsRoundAction(
    icon: Int,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(containerColor.copy(alpha = if (enabled) 1f else 0.4f))
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = label,
                tint = contentColor.copy(alpha = if (enabled) 1f else 0.4f),
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
internal fun CommsActionCard(
    icon: Int,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = MaterialTheme.colorScheme.surfaceContainer
    val tint = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.4f)
    Card(
        modifier = modifier
            .clip(CommsCardShape)
            .clickable(enabled = enabled, onClick = onClick),
        shape = CommsCardShape,
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun CommsDetailCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = CommsCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
internal fun CommsHistoryRow(call: CallLogEntry) {
    val missed = call.type == CallType.Missed || call.type == CallType.Rejected
    val color = if (missed) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurface
    val typeLabel = when (call.type) {
        CallType.Incoming -> "Incoming"
        CallType.Outgoing -> "Outgoing"
        CallType.Missed -> "Missed"
        CallType.Rejected -> "Rejected"
        CallType.Blocked -> "Blocked"
        CallType.Unknown -> "Call"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = typeLabel,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = color,
            )
            Text(
                text = formatCallTimestamp(call.timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (call.durationSeconds > 0) {
            Text(
                text = formatCallDuration(call.durationSeconds),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal fun formatCallTimestamp(timestamp: Long): String {
    return SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(timestamp))
}

internal fun formatCallDuration(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}

internal fun highlightedName(text: String, query: String, highlightColor: Color): androidx.compose.ui.text.AnnotatedString {
    if (query.isBlank()) return androidx.compose.ui.text.AnnotatedString(text)
    val foldedText = GreekText.fold(text)
    val foldedQuery = GreekText.fold(query)
    val start = foldedText.indexOf(foldedQuery)
    if (start < 0 || start >= text.length) return androidx.compose.ui.text.AnnotatedString(text)
    val end = (start + query.length).coerceAtMost(text.length)
    return buildAnnotatedString {
        append(text.substring(0, start))
        withStyle(SpanStyle(color = highlightColor, fontWeight = FontWeight.Bold)) {
            append(text.substring(start, end))
        }
        append(text.substring(end))
    }
}
