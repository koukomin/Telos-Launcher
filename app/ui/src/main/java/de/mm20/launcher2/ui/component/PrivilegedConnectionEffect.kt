package de.mm20.launcher2.ui.component

import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

/** Refresh surfaces after late binder delivery, authorization changes or server death. */
@Composable
fun PrivilegedConnectionEffect(onChanged: () -> Unit) {
    val current by rememberUpdatedState(onChanged)
    val scope = rememberCoroutineScope()
    DisposableEffect(Unit) {
        val received = Shizuku.OnBinderReceivedListener { scope.launch { current() } }
        val dead = Shizuku.OnBinderDeadListener { scope.launch { current() } }
        val permission = Shizuku.OnRequestPermissionResultListener { _, _ -> scope.launch { current() } }
        Shizuku.addBinderReceivedListenerSticky(received)
        Shizuku.addBinderDeadListener(dead)
        Shizuku.addRequestPermissionResultListener(permission)
        onDispose {
            Shizuku.removeBinderReceivedListener(received)
            Shizuku.removeBinderDeadListener(dead)
            Shizuku.removeRequestPermissionResultListener(permission)
        }
    }
}
