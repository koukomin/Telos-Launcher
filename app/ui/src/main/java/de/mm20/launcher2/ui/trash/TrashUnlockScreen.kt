package de.mm20.launcher2.ui.trash

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.comms.AuthManager

import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.locals.LocalBackStack
import kotlinx.serialization.Serializable

@Serializable
data object TrashUnlockRoute : NavKey

@Composable
fun TrashUnlockScreen() {
    val activity = LocalActivity.current as? FragmentActivity
    val backStack = LocalBackStack.current
    val title = stringResource(R.string.telos_trash_unlock)
    var unlocked by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val ok = if (activity != null) AuthManager().authenticateNative(activity, title) else false
        if (!ok) {
            backStack.removeLastOrNull()
            return@LaunchedEffect
        }
        unlocked = true
    }
    if (!unlocked) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        TrashScreen()
    }
}
