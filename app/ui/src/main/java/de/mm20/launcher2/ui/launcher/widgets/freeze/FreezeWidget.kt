package de.mm20.launcher2.ui.launcher.widgets.freeze

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ui.R

@Composable
fun FreezeWidget(widget: de.mm20.launcher2.widgets.FreezeWidget) {
    val viewModel: FreezeWidgetVM = viewModel()

    val candidates by viewModel.candidates.collectAsStateWithLifecycle()
    val frozenCount by viewModel.frozenCount.collectAsStateWithLifecycle()

    LaunchedEffect(candidates) {
        viewModel.refresh()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ac_unit_24px),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
            )
            Column(modifier = Modifier.padding(start = 16.dp)) {
                Text(
                    text = stringResource(R.string.widget_name_freeze),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = if (candidates.isEmpty()) {
                        stringResource(R.string.freeze_widget_no_candidates)
                    } else {
                        stringResource(R.string.freeze_widget_frozen_now, frozenCount, candidates.size)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            enabled = candidates.isNotEmpty(),
            onClick = { viewModel.freezeAllNow() },
        ) {
            Text(stringResource(R.string.freeze_widget_freeze_now))
        }
    }
}
