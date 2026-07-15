package de.mm20.launcher2.ui.launcher.widgets.reminders

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.Banner
import de.mm20.launcher2.ui.component.MissingPermissionBanner
import de.mm20.launcher2.ui.launcher.search.common.list.SearchResultList
import de.mm20.launcher2.widgets.RemindersWidget

@Composable
fun RemindersWidget(widget: RemindersWidget) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val viewModel: RemindersWidgetVM =
        viewModel(key = "reminders-widget-${widget.id}")

    LaunchedEffect(widget) {
        viewModel.updateWidget(widget)
    }
    LaunchedEffect(Unit) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.onActive()
        }
    }

    val tasks by viewModel.tasks
    val hasPermission by viewModel.hasPermission.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp)
    ) {
        if (hasPermission == false) {
            MissingPermissionBanner(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                text = stringResource(R.string.missing_permission_reminders_widget),
                onClick = { viewModel.requestTasksPermission(context as AppCompatActivity) }
            )
        } else if (tasks.isEmpty()) {
            Banner(
                modifier = Modifier.padding(vertical = 4.dp),
                text = stringResource(R.string.reminders_widget_empty),
                icon = R.drawable.task_alt_24px,
            )
        } else {
            SearchResultList(
                tasks,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
