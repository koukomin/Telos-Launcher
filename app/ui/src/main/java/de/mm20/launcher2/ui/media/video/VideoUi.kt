package de.mm20.launcher2.ui.media.video

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.preferences.ui.PerformanceSettings
import de.mm20.launcher2.ui.R
import org.koin.compose.koinInject

/** True when the user asked for no animations */
@Composable
internal fun rememberReduceAnimations(): Boolean {
    val reduce by koinInject<PerformanceSettings>().reduceAnimations.collectAsState(false)
    return reduce
}

/** Scales the element to 0.97 while it is pressed; click and long click as given */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Modifier.pressScale(onClick: () -> Unit, onLongClick: (() -> Unit)? = null): Modifier {
    val reduce = rememberReduceAnimations()
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed && !reduce) 0.97f else 1f,
        if (reduce) snap() else spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "videoPress",
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .combinedClickable(interactionSource = source, indication = null, onClick = onClick, onLongClick = onLongClick)
}

/** A rounded placeholder box with a moving gradient (static when animations are reduced) */
@Composable
internal fun Shimmer(modifier: Modifier, shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(20.dp)) {
    val reduce = rememberReduceAnimations()
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val light = MaterialTheme.colorScheme.surfaceContainerHighest
    val shift = if (reduce) 0.5f else {
        val t = rememberInfiniteTransition(label = "videoShimmer")
        val v by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), label = "shift")
        v
    }
    Box(
        modifier.clip(shape).background(
            Brush.horizontalGradient(
                0f to base,
                (shift).coerceIn(0.01f, 0.99f) to light,
                1f to base,
            )
        )
    )
}

/** Skeleton of the library home while the videos are loading */
@Composable
internal fun VideoSkeleton() {
    Column(Modifier.fillMaxSize().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Shimmer(Modifier.padding(horizontal = 16.dp).fillMaxWidth().aspectRatio(16f / 9f), RoundedCornerShape(28.dp))
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(3) { Shimmer(Modifier.weight(1f).aspectRatio(2f / 3f)) }
        }
    }
}

/** A large tinted icon, a title and one line of text */
@Composable
internal fun VideoEmptyState(iconRes: Int, title: String, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(painterResource(iconRes), contentDescription = null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), modifier = Modifier.size(72.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp))
        if (text.isNotBlank()) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** A pill shaped, horizontally scrollable segmented row with an indicator that glides to the selected entry */
@Composable
internal fun PillTabs(titles: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val reduce = rememberReduceAnimations()
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val xs = remember(titles.size) { mutableStateMapOf<Int, Int>() }
    val ws = remember(titles.size) { mutableStateMapOf<Int, Int>() }
    val spec = if (reduce) snap<Int>() else spring(dampingRatio = 0.8f, stiffness = 400f)
    val x by animateIntAsState(xs[selected] ?: 0, spec, label = "pillX")
    val w by animateIntAsState(ws[selected] ?: 0, spec, label = "pillW")
    LaunchedEffect(selected, xs[selected], ws[selected]) {
        val px = xs[selected] ?: return@LaunchedEffect
        val target = (px - with(density) { 48.dp.roundToPx() }).coerceAtLeast(0)
        if (reduce) scroll.scrollTo(target) else scroll.animateScrollTo(target)
    }
    Box(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Box(
            Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f))
                .horizontalScroll(scroll).padding(4.dp)
        ) {
            Box(
                Modifier.offset { IntOffset(x, 0) }.width(with(density) { w.toDp() }).height(40.dp)
                    .clip(CircleShape).background(MaterialTheme.colorScheme.primary)
            )
            Row {
                titles.forEachIndexed { i, title ->
                    Box(
                        Modifier.height(40.dp).clip(CircleShape)
                            .onGloballyPositioned { xs[i] = it.positionInParent().x.toInt(); ws[i] = it.size.width }
                            .clickable { onSelect(i) }
                            .padding(horizontal = 18.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            title,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            color = if (i == selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** A small pill label on top of images */
@Composable
internal fun ImageChip(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = androidx.compose.ui.graphics.Color.White,
        modifier = modifier.clip(CircleShape).background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f)).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** A round check badge for watched videos */
@Composable
internal fun WatchedBadge(modifier: Modifier = Modifier) {
    Box(
        modifier.size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.check_24px), contentDescription = stringResource(R.string.au14_videoui_watched), tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
    }
}

/** A metadata chip of the detail page */
@Composable
internal fun MetaChip(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
