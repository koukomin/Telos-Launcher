package de.mm20.launcher2.ui.launcher.search.recommendations

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.DismissableBottomSheet

/**
 * Explains the "Recommended" card shown in the drawer/search results: what it is, that there's
 * no affiliate/advertising relationship behind it, and where to turn it off.
 */
@Composable
fun RecommendationInfoSheet(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
) {
    DismissableBottomSheet(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = stringResource(R.string.recommendation_info_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.recommendation_info_body),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
