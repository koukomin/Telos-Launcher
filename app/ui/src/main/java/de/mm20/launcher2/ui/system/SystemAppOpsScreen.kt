package de.mm20.launcher2.ui.system
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.locals.LocalBackStack
import kotlinx.serialization.Serializable

@Serializable
data class SystemAppOpsRoute(val packageName: String? = null) : NavKey

@Composable
fun SystemAppOpsScreen(packageName: String? = null) {
    val backStack = LocalBackStack.current
    Column(Modifier.fillMaxSize()) {
        androidx.compose.material3.IconButton(onClick = { backStack.removeLastOrNull() }) {
            androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(BaseR.drawable.arrow_back_24px), stringResource(I18nR.string.hc_back))
        }
        if (Build.VERSION.SDK_INT >= 35) {
            Box(Modifier.weight(1f)) { dev.izumi.appopsnext.EmbeddedAppOpsScreen(packageName) }
        }
    }
}

/** Called off Main; background policy recovery is enabled only after this feature was explicitly opened. */
suspend fun restoreSystemAppOps(context: android.content.Context) {
    if (android.os.Build.VERSION.SDK_INT >= 35 && context.getSharedPreferences("appopsnext_host", android.content.Context.MODE_PRIVATE).getBoolean("enabled", false)) {
        dev.izumi.appopsnext.AppOpsNextApplication.get(context)
    }
}
