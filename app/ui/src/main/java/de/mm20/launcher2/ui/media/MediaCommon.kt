package de.mm20.launcher2.ui.media

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.locals.LocalBackStack

/**
 * Frame for the Telos media screens (music, video, photos, radio): a top bar with back button and
 * a surface that sets the text color, and keeps the content clear of the status and navigation bars.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaFrame(
    title: String,
    askNotifications: Boolean = false,
    guardKey: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    val backStack = LocalBackStack.current
    if (guardKey != null) VirtualAppGuardEffect(guardKey)
    if (askNotifications) RequestNotificationPermission()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { backStack.removeLastOrNull() }) {
                        Icon(painterResource(R.drawable.arrow_back_24px), contentDescription = stringResource(R.string.hc_back))
                    }
                },
                actions = actions,
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) { content() }
    }
}

/** Rounded search field with readable text in light and dark mode (see [TelosSearchBar]). */
@Composable
fun MediaSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    de.mm20.launcher2.ui.component.TelosSearchBar(value, onValueChange, placeholder, modifier)
}

/** Playback notifications need the notification permission on Android 13 and newer. */
@Composable
private fun RequestNotificationPermission() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/** Marks [key] as the app on screen while the activity is started, for the crash guard */
@Composable
fun VirtualAppGuardEffect(guardKey: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.DisposableEffect(guardKey, lifecycle) {
        // on screen only while the activity is started, so a launcher that was sent to the
        // background is not blamed for a later crash
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_START) de.mm20.launcher2.base.VirtualAppGuard.enter(context, guardKey)
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) de.mm20.launcher2.base.VirtualAppGuard.leave(context, guardKey)
        }
        if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
            de.mm20.launcher2.base.VirtualAppGuard.enter(context, guardKey)
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            de.mm20.launcher2.base.VirtualAppGuard.leave(context, guardKey)
        }
    }
}
