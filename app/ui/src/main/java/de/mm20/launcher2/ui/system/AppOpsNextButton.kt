package de.mm20.launcher2.ui.system

import android.os.Build
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.locals.LocalBackStack

/** Opens the embedded AppOps suite inside System, optionally at one installed package. */
@Composable
internal fun AppOpsNextButton(packageName: String? = null) {
    val backStack = LocalBackStack.current
    OutlinedButton(enabled = Build.VERSION.SDK_INT >= 35,
        onClick = { backStack.add(SystemAppOpsRoute(packageName)) }) {
        Text(stringResource(R.string.system_appopsnext_name))
    }
}
