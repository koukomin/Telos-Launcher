package de.mm20.launcher2.ui.launcher.search.recommendations

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.R

/**
 * Full browsable list of [AppRecommendations], grouped by category. Shown as its own tab in the
 * app drawer, alongside Favorites - separate from the single rotating/query-matched card shown by
 * [RecommendationResults] elsewhere in search results.
 */
@Composable
fun RecommendedTabContent(
    onHideTab: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.recommendation_tab_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
            )
            TextButton(onClick = onHideTab) {
                Text(stringResource(R.string.recommendation_tab_hide))
            }
        }
        for (category in RecommendationCategory.entries) {
            val apps = AppRecommendations.all.filter { it.category == category }
            if (apps.isEmpty()) continue
            Text(
                text = stringResource(category.labelRes),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
            )
            RecommendedCategoryCarousel(category, apps)
        }
    }
}

private val CardWidth = 200.dp
/** Fraction of a card width kept visible past the viewport edge, so the next category item
 * always shows a partial "peek" - the visual cue that there's more to scroll to. */
private const val PeekFraction = 0.35f

@Composable
private fun RecommendedCategoryCarousel(
    category: RecommendationCategory,
    apps: List<AppRecommendation>,
) {
    val listState = rememberLazyListState()
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp + CardWidth * PeekFraction,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(apps, key = { it.name }) { app ->
            RecommendedAppCard(app, modifier = Modifier.width(CardWidth))
        }
    }
}

@Composable
private fun RecommendedAppCard(recommendation: AppRecommendation, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var showInfo by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable {
                try {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(recommendation.storeUrl))
                    )
                } catch (e: ActivityNotFoundException) {
                    // No Play Store / browser available to handle the link - nothing we can do.
                }
            }
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Box(
                    modifier = Modifier.size(40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(recommendation.category.iconRes),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
            IconButton(
                modifier = Modifier.size(24.dp),
                onClick = { showInfo = true },
            ) {
                Icon(
                    painter = painterResource(R.drawable.info_24px),
                    contentDescription = stringResource(R.string.recommendation_why_am_i_seeing_this),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Text(
            text = stringResource(R.string.recommendation_card_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = recommendation.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(recommendation.descriptionRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }

    RecommendationInfoSheet(
        expanded = showInfo,
        onDismissRequest = { showInfo = false },
    )
}
