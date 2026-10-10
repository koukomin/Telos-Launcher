package de.mm20.launcher2.ui.media.tv

import androidx.annotation.StringRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.comms.tv.TvChannel
import de.mm20.launcher2.ui.R

/** Localized name of an iptv-org category id; the catalog's English name for unknown ids */
@StringRes
private fun categoryRes(id: String): Int? = when (id) {
    "animation" -> R.string.au14_tvhome_cat_animation
    "auto" -> R.string.au14_tvhome_cat_auto
    "business" -> R.string.au14_tvhome_cat_business
    "classic" -> R.string.au14_tvhome_cat_classic
    "comedy" -> R.string.au14_tvhome_cat_comedy
    "cooking" -> R.string.au14_tvhome_cat_cooking
    "culture" -> R.string.au14_tvhome_cat_culture
    "documentary" -> R.string.au14_tvhome_cat_documentary
    "education" -> R.string.au14_tvhome_cat_education
    "entertainment" -> R.string.au14_tvhome_cat_entertainment
    "family" -> R.string.au14_tvhome_cat_family
    "general" -> R.string.au14_tvhome_cat_general
    "interactive" -> R.string.au14_tvhome_cat_interactive
    "kids" -> R.string.au14_tvhome_cat_kids
    "legislative" -> R.string.au14_tvhome_cat_legislative
    "lifestyle" -> R.string.au14_tvhome_cat_lifestyle
    "movies" -> R.string.au14_tvhome_cat_movies
    "music" -> R.string.au14_tvhome_cat_music
    "news" -> R.string.au14_tvhome_cat_news
    "outdoor" -> R.string.au14_tvhome_cat_outdoor
    "public" -> R.string.au14_tvhome_cat_public
    "relax" -> R.string.au14_tvhome_cat_relax
    "religious" -> R.string.au14_tvhome_cat_religious
    "series" -> R.string.au14_tvhome_cat_series
    "science" -> R.string.au14_tvhome_cat_science
    "shop" -> R.string.au14_tvhome_cat_shop
    "sports" -> R.string.au14_tvhome_cat_sports
    "travel" -> R.string.au14_tvhome_cat_travel
    "weather" -> R.string.au14_tvhome_cat_weather
    else -> null
}

@Composable
fun tvCategoryName(id: String, fallback: String): String {
    val res = categoryRes(id) ?: return fallback
    return stringResource(res)
}

/** Scales to 0.97 with a spring while pressed (not when animations are reduced) */
@Composable
fun Modifier.tvPressScale(source: InteractionSource, reduceAnimations: Boolean): Modifier {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduceAnimations) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "tvPress",
    )
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
}

/** A stable, subtle tint per channel (the logo is a remote image, so the tint is derived from the channel) */
@Composable
fun tvTint(channel: TvChannel): Color {
    val hue = ((channel.id.hashCode() and 0x7fffffff) % 360).toFloat()
    val base = MaterialTheme.colorScheme.surfaceContainerLow
    return remember(hue, base) {
        val c = Color.hsv(hue, 0.55f, 0.85f)
        Color(
            red = base.red * 0.88f + c.red * 0.12f,
            green = base.green * 0.88f + c.green * 0.12f,
            blue = base.blue * 0.88f + c.blue * 0.12f,
            alpha = 1f,
        )
    }
}

/** Pill-shaped two-option control with an animated indicator */
@Composable
fun TvSegmentedControl(
    selected: Int,
    labels: List<String>,
    icons: List<Int>,
    reduceAnimations: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val fraction by animateFloatAsState(
        targetValue = selected.toFloat(),
        animationSpec = if (reduceAnimations) tween(0) else spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium),
        label = "tvSeg",
    )
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        val segment = maxWidth / labels.size
        Box(
            Modifier
                .offset(x = segment * fraction)
                .width(segment)
                .fillMaxHeight()
                .padding(4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
        )
        Row(Modifier.fillMaxSize()) {
            labels.forEachIndexed { i, label ->
                val active = i == selected
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .clickable(role = Role.Tab) { onSelect(i) },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painterResource(icons[i]), null,
                        tint = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
    @Suppress("UNUSED_EXPRESSION") density
}

/** Large 16:9 "Continue watching" card */
@Composable
fun TvHeroCard(
    channel: TvChannel,
    nowTitle: String?,
    reduceAnimations: Boolean,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val tint = tvTint(channel)
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier
            .fillMaxWidth()
            .tvPressScale(source, reduceAnimations)
            .shadow(3.dp, shape)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(tint, MaterialTheme.colorScheme.surfaceContainerHigh)))
            .aspectRatio(16f / 9f)
            .clickable(interactionSource = source, indication = androidx.compose.foundation.LocalIndication.current, onClick = onPlay),
    ) {
        TvLogo(
            channel,
            Modifier.align(Alignment.Center).padding(bottom = 36.dp).size(96.dp),
            corner = 24.dp,
        )
        Row(
            Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.errorContainer)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
            Text(
                stringResource(R.string.au12_tvui_live),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        Row(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.au14_tvhome_continue_watching),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    channel.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (nowTitle != null) {
                    Text(
                        stringResource(R.string.au13_tvextraui_now_line, nowTitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box(
                Modifier
                    .padding(start = 12.dp)
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.play_arrow_24px),
                    contentDescription = stringResource(R.string.au14_tvhome_play),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(30.dp),
                )
            }
        }
    }
}

/** Pulsing placeholder tiles while the catalog loads */
@Composable
fun TvSkeleton(reduceAnimations: Boolean, modifier: Modifier = Modifier) {
    val alpha = if (reduceAnimations) 0.6f else {
        val t = rememberInfiniteTransition(label = "tvSkel")
        val a by t.animateFloat(
            initialValue = 0.35f, targetValue = 0.8f,
            animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
            label = "tvSkelA",
        )
        a
    }
    val color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = alpha)
    Column(modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(24.dp)).background(color))
        for (r in 0 until 2) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (c in 0 until 3) {
                    Box(Modifier.weight(1f).height(96.dp).clip(RoundedCornerShape(20.dp)).background(color))
                }
            }
        }
    }
}

/** Illustration-like empty state: big icon, title, text */
@Composable
fun TvEmptyState(icon: Int, title: String, text: String?, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(icon), null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(48.dp),
            )
        }
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        if (text != null) {
            Text(
                text, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
        }
    }
}

/** Gentle hint shown in Browse while there are no favorites */
@Composable
fun TvStarHint(modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(R.drawable.star_24px_outlined), null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(24.dp),
        )
        Text(
            stringResource(R.string.au14_tvhome_hint_star),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}
