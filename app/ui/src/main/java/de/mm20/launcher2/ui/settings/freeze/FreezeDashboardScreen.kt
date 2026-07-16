package de.mm20.launcher2.ui.settings.freeze

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import kotlinx.serialization.Serializable

@Serializable
data object FreezeDashboardRoute : NavKey

@Composable
fun FreezeDashboardScreen() {
    val viewModel: FreezeDashboardScreenVM = viewModel()
    val rows by viewModel.rows.collectAsStateWithLifecycle()

    val frozen = rows.filter { it.isFrozen }

    PreferenceScreen(
        title = stringResource(R.string.preference_freeze_dashboard),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item {
            PreferenceCategory(title = stringResource(R.string.freeze_dashboard_currently_frozen)) {
                if (frozen.isEmpty()) {
                    Text(
                        text = stringResource(R.string.freeze_dashboard_none_frozen),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        itemsIndexed(frozen, key = { _, it -> "frozen_" + it.app.key }) { _, row ->
            val icon by viewModel.getIcon(row.app, 32.dp.value.toInt()).collectAsStateWithLifecycle(null)
            Preference(
                title = row.app.label,
                icon = { ShapedLauncherIcon(size = 32.dp, icon = { icon }) },
                summary = stringResource(R.string.freeze_dashboard_frozen_badge),
            )
        }
        item {
            PreferenceCategory(title = stringResource(R.string.freeze_dashboard_history)) {
                if (rows.isEmpty()) {
                    Text(
                        text = stringResource(R.string.freeze_dashboard_no_history),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        itemsIndexed(rows, key = { _, it -> "history_" + it.app.key }) { _, row ->
            val icon by viewModel.getIcon(row.app, 32.dp.value.toInt()).collectAsStateWithLifecycle(null)
            val lastFrozenAt = row.lastFrozenAt ?: 0L
            val lastUnfrozenAt = row.lastUnfrozenAt ?: 0L
            val lastAt = maxOf(lastFrozenAt, lastUnfrozenAt)
            val countsLine = stringResource(R.string.freeze_dashboard_counts, row.freezeCount, row.unfreezeCount)
            val summary = if (lastAt > 0L) {
                val relativeTime = DateUtils.getRelativeTimeSpanString(lastAt).toString()
                val lastActionLine = if (lastFrozenAt >= lastUnfrozenAt) {
                    stringResource(R.string.freeze_dashboard_last_frozen, relativeTime)
                } else {
                    stringResource(R.string.freeze_dashboard_last_unfrozen, relativeTime)
                }
                "$countsLine\n$lastActionLine"
            } else {
                countsLine
            }
            Preference(
                title = row.app.label,
                icon = { ShapedLauncherIcon(size = 32.dp, icon = { icon }) },
                summary = summary,
            )
        }
    }
}
