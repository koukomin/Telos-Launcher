package de.mm20.launcher2.ui.launcher.sheets

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import de.mm20.launcher2.ui.R
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.ui.component.DismissableBottomSheet
import de.mm20.launcher2.ui.launcher.search.common.grid.SearchResultGrid

@Composable
fun HiddenItemsSheet(
    expanded: Boolean,
    items: List<SavableSearchable>,
    onDismiss: () -> Unit
) {
    DismissableBottomSheet(expanded = expanded, onDismissRequest = onDismiss) {
        if (items.isEmpty()) {
            Text(
                text = stringResource(R.string.au3_launcher3_hidden_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp)
                    .navigationBarsPadding(),
                textAlign = TextAlign.Center,
            )
        } else {
            SearchResultGrid(
                items,
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
                    .navigationBarsPadding()
            )
        }
    }
}
