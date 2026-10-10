package de.mm20.launcher2.ui.media.tv

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import de.mm20.launcher2.comms.tv.TvChannel
import de.mm20.launcher2.comms.tv.TvProgrammes
import de.mm20.launcher2.ui.R

/** Actions of the long-press menu of a channel; null entries are not shown */
class TvCardActions(
    val onFavorite: (Boolean) -> Unit,
    val onShare: () -> Unit,
    val onEdit: (() -> Unit)? = null,
    val onDelete: (() -> Unit)? = null,
    val onMoveBefore: (() -> Unit)? = null,
    val onMoveAfter: (() -> Unit)? = null,
)

/**
 * What the channel cards need besides the channel: the programme guide lookup, a key that changes when the
 * guide or the minute changed (so remembered values are recomputed), and the URLs of streams that fail.
 */
class TvCardInfo(
    val key: Any = 0,
    val nowNext: (String) -> TvProgrammes? = { null },
    val badUrls: Set<String> = emptySet(),
)

val LocalTvCardInfo = compositionLocalOf { TvCardInfo() }

/** Rounded tile with the channel logo; the first letter of the name when there is no logo or it fails to load */
@Composable
fun TvLogo(channel: TvChannel, modifier: Modifier = Modifier, corner: Dp = 16.dp) {
    val context = LocalContext.current
    var failed by remember(channel.logoUrl) { mutableStateOf(false) }
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (channel.logoUrl.isBlank() || failed) {
            Text(
                channel.name.trim().take(1).uppercase(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val request = remember(channel.logoUrl) {
                ImageRequest.Builder(context)
                    .data(channel.logoUrl)
                    .size(256)
                    .memoryCacheKey(channel.logoUrl)
                    .crossfade(false)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                onError = { failed = true },
                modifier = Modifier.fillMaxSize().padding(10.dp),
            )
        }
    }
}

/** Three bars that move while the channel plays (still when animations are reduced) */
@Composable
fun TvEqualizer(reduceAnimations: Boolean, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onPrimary
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .height(14.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        val transition = if (reduceAnimations) null else rememberInfiniteTransition(label = "tvEq")
        for (i in 0 until 3) {
            val fraction = if (transition == null) 0.3f + 0.25f * i else {
                val f by transition.animateFloat(
                    initialValue = 0.25f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(420 + i * 140, easing = LinearEasing), RepeatMode.Reverse),
                    label = "tvEqBar$i",
                )
                f
            }
            Box(Modifier.width(3.dp).fillMaxHeight(fraction).background(color))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvChannelCard(
    channel: TvChannel,
    playing: Boolean,
    favorite: Boolean,
    reduceAnimations: Boolean,
    actions: TvCardActions,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    logoHeight: Dp = 72.dp,
) {
    var menu by remember { mutableStateOf(false) }
    val pressSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val cardShape = RoundedCornerShape(20.dp)
    val info = LocalTvCardInfo.current
    val nowTitle = remember(info.key, channel.id) { info.nowNext(channel.id)?.current?.title }
    val offline = remember(info.badUrls, channel.id) {
        channel.streams.isNotEmpty() && channel.streams.all { it.url in info.badUrls }
    }
    Box(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .tvPressScale(pressSource, reduceAnimations)
                .shadow(2.dp, cardShape)
                .clip(cardShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .combinedClickable(
                    interactionSource = pressSource,
                    indication = androidx.compose.foundation.LocalIndication.current,
                    onClick = onClick,
                    onLongClick = { menu = true },
                )
                .padding(6.dp),
        ) {
            Box(Modifier.fillMaxWidth().height(logoHeight)) {
                TvLogo(channel, Modifier.fillMaxSize())
                if (offline) {
                    TvStaticMini(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).alpha(0.6f))
                }
                if (channel.isExtra) {
                    val desc = stringResource(R.string.au13_tvextra_marker_description)
                    Text(
                        stringResource(R.string.au13_tvextra_marker),
                        fontSize = 9.sp,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                            .semantics { contentDescription = desc },
                    )
                }
                if (playing) {
                    TvEqualizer(reduceAnimations, Modifier.align(Alignment.BottomStart).padding(6.dp))
                }
                IconButton(
                    onClick = { actions.onFavorite(!favorite) },
                    modifier = Modifier.align(Alignment.TopEnd).size(32.dp),
                ) {
                    Icon(
                        painterResource(if (favorite) R.drawable.star_24px_filled else R.drawable.star_24px_outlined),
                        contentDescription = stringResource(
                            if (favorite) R.string.au12_tvui_favorite_remove else R.string.au12_tvui_favorite_add
                        ),
                        tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Text(
                channel.name,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
            if (nowTitle != null) {
                Text(
                    stringResource(R.string.au13_tvextraui_now_line, nowTitle),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = {
                    Text(stringResource(if (favorite) R.string.au12_tvui_favorite_remove else R.string.au12_tvui_favorite_add))
                },
                onClick = { menu = false; actions.onFavorite(!favorite) },
            )
            actions.onMoveBefore?.let {
                DropdownMenuItem(text = { Text(stringResource(R.string.au12_tvui_move_up)) }, onClick = { menu = false; it() })
            }
            actions.onMoveAfter?.let {
                DropdownMenuItem(text = { Text(stringResource(R.string.au12_tvui_move_down)) }, onClick = { menu = false; it() })
            }
            DropdownMenuItem(text = { Text(stringResource(R.string.hc_share)) }, onClick = { menu = false; actions.onShare() })
            actions.onEdit?.let {
                DropdownMenuItem(text = { Text(stringResource(R.string.hc_edit)) }, onClick = { menu = false; it() })
            }
            actions.onDelete?.let {
                DropdownMenuItem(text = { Text(stringResource(R.string.hc_delete)) }, onClick = { menu = false; it() })
            }
        }
    }
}
