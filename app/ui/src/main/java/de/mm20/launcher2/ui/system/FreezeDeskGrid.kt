package de.mm20.launcher2.ui.system

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.base.R as BaseR
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.ui.component.telosTechCard
import de.mm20.launcher2.ui.store.AppIcon

/** One scrolling container keeps controls reachable in short windows and at large font scales. */
@Composable
internal fun FreezeDeskGrid(
    apps: List<InstalledSystemRow>,
    listView: Boolean,
    onSelect: (InstalledSystemRow) -> Unit,
    header: @Composable () -> Unit,
    icon: @Composable (InstalledSystemRow) -> Unit = { AppIcon(it.pkg, it.label, 72) },
) {
    val fontScale = LocalDensity.current.fontScale
    LazyVerticalGrid(
        columns =
            if (listView) GridCells.Fixed(1)
            else GridCells.Adaptive(100.dp * fontScale.coerceAtLeast(1f)),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp, top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "controls", span = { GridItemSpan(maxLineSpan) }) { header() }
        items(apps, key = { it.pkg }) { app ->
            val status =
                stringResource(
                    if (app.frozen) I18nR.string.hf_freeze_frozen
                    else if (app.running) I18nR.string.freeze_dashboard_running_now
                    else if (app.system) I18nR.string.netfw_filter_system
                    else I18nR.string.netfw_filter_user
                )
            val shape = RoundedCornerShape(28.dp)
            Surface(
                onClick = { onSelect(app) },
                modifier =
                    Modifier.fillMaxWidth()
                        .telosTechCard(
                            borderColor = MaterialTheme.colorScheme.primary,
                            shape = shape,
                            alpha = if (app.frozen) 0.6f else 0.15f,
                            backgroundColor = MaterialTheme.colorScheme.surfaceContainer,
                        )
                        .semantics { stateDescription = status },
                shape = shape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                if (listView) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        FreezeDeskIcon(app, icon)
                        Column(Modifier.weight(1f).padding(start = 16.dp)) {
                            Text(app.label, style = MaterialTheme.typography.titleMedium)
                            Text(
                                status,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    Column(
                        Modifier.padding(horizontal = 12.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        FreezeDeskIcon(app, icon)
                        Text(
                            app.label,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FreezeDeskIcon(
    app: InstalledSystemRow,
    icon: @Composable (InstalledSystemRow) -> Unit,
) {
    Box(Modifier.size(72.dp)) {
        icon(app)
        if (app.running) {
            Box(
                Modifier.align(Alignment.TopStart)
                    .size(14.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
        }
        if (app.frozen) {
            Surface(
                modifier = Modifier.align(Alignment.TopEnd),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(
                    painterResource(BaseR.drawable.ac_unit_24px),
                    contentDescription = null,
                    modifier = Modifier.padding(3.dp).size(18.dp),
                )
            }
        }
    }
}
