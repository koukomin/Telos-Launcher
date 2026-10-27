package de.mm20.launcher2.ui.system

import android.os.Build
import android.os.Process
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.launcher.search.listItemViewModel
import de.mm20.launcher2.applications.AppRepository
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.launcher.search.common.SearchableItemVM
import de.mm20.launcher2.ui.locals.LocalBackStack
import de.mm20.launcher2.ui.store.StoreDashboardRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/** Opens the complete upstream companion, including templates/history/monitoring; Store handles installation. */
@Composable
internal fun AppOpsNextButton() {
    val context = LocalContext.current
    val backStack = LocalBackStack.current
    val repository: AppRepository = koinInject()
    val launcher: SearchableItemVM = listItemViewModel(key = "system-appopsnext-launch")
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    OutlinedButton(enabled = Build.VERSION.SDK_INT >= 35 && !busy, onClick = {
        busy = true
        scope.launch {
            try {
                val app = withContext(Dispatchers.IO) {
                    repository.findOne("dev.izumi.appopsnext", Process.myUserHandle()).first()
                }
                if (app != null) launcher.launchTarget(context, app)
                else backStack.add(StoreDashboardRoute(addUrl = "https://github.com/1zumiii/AppOpsNext"))
            } finally { busy = false }
        }
    }) {
        Icon(painterResource(R.drawable.settings_24px), null, Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.system_appopsnext_name))
    }
}
