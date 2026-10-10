package de.mm20.launcher2.ui.launcher.search.tv

import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import de.mm20.launcher2.applock.SettingsDeepLinkContract
import de.mm20.launcher2.comms.tv.TvChannel
import de.mm20.launcher2.comms.tv.TvIndex
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.launcher.search.common.list.ListItemSurface

/**
 * Favorite, custom and recent TV channels of Telos Media that match the search. A tap starts the
 * channel through [onPlay] (the shared TV player) and opens Telos Media on the TV space.
 */
fun LazyListScope.TvResults(channels: List<TvChannel>, reverse: Boolean, onPlay: (TvChannel) -> Unit) {
    channels.forEachIndexed { index, channel ->
        item(key = "tv-${channel.id}") {
            val context = LocalContext.current
            val country = if (channel.country.isNotEmpty()) TvIndex.countryName(channel.country) else ""
            val secondary = listOf(country, channel.group).filter { it.isNotBlank() }.joinToString(" · ")
            ListItemSurface(isFirst = index == 0, isLast = index == channels.lastIndex, reverse = reverse) {
                Row(
                    Modifier.fillMaxWidth().clickable {
                        onPlay(channel)
                        runCatching {
                            context.startActivity(
                                Intent().apply {
                                    setClassName(context.packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
                                    putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_MEDIA)
                                    putExtra(SettingsDeepLinkContract.EXTRA_MEDIA_SPACE, "tv")
                                    putExtra(SettingsDeepLinkContract.EXTRA_MEDIA_CHANNEL, channel.id)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        }
                    }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(44.dp)) {
                        Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (channel.logoUrl.isNotBlank()) {
                                AsyncImage(model = channel.logoUrl, contentDescription = null, modifier = Modifier.fillMaxSize().padding(4.dp))
                            } else {
                                Text(
                                    channel.name.trim().take(1).uppercase(),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        MediaTypeIcon(R.drawable.tv_24px, Modifier.align(Alignment.BottomEnd))
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                channel.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            MediaTypeLabel(stringResource(R.string.au12_tvsearch_badge_tv), Modifier.padding(start = 8.dp))
                        }
                        if (secondary.isNotEmpty()) {
                            Text(
                                secondary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Small rounded chip with the icon of the media type, drawn at the corner of a logo tile */
@Composable
fun MediaTypeIcon(@DrawableRes icon: Int, modifier: Modifier = Modifier) {
    Box(
        modifier.size(18.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(12.dp),
        )
    }
}

/** Small rounded text chip ("TV", "Radio") shown next to a name; its text is read by screen readers */
@Composable
fun MediaTypeLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 6.dp, vertical = 1.dp),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        maxLines = 1,
    )
}
