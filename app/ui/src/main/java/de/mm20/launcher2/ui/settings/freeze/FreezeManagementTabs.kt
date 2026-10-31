package de.mm20.launcher2.ui.settings.freeze
import de.mm20.launcher2.i18n.R as I18nR

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FreezeManagementTabs(selected: Int, select: (Int) -> Unit) {
    PrimaryScrollableTabRow(selectedTabIndex = selected, edgePadding = 0.dp,
        modifier = Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        listOf(I18nR.string.preference_freeze_apps_category, I18nR.string.preference_freeze_auto_category,
            I18nR.string.settings).forEachIndexed { index, label ->
            Tab(selected = selected == index, onClick = { select(index) },
                text = { Text(stringResource(label), modifier = Modifier.padding(vertical = 8.dp)) })
        }
    }
}
