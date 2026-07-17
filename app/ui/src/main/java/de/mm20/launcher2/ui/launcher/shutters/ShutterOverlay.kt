package de.mm20.launcher2.ui.launcher.shutters

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.preferences.ShutterWidgetRef
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.DismissableBottomSheet
import de.mm20.launcher2.ui.launcher.widgets.external.AppWidgetHost

/**
 * The transient popup shown for an app's assigned "shutter" widget. Resolves the live
 * AppWidgetProviderInfo by component name at render time (dimensions etc. may have changed
 * since binding); if the provider is no longer installed, dismisses and reports that via
 * [onProviderMissing] so the caller can clear the stale reference.
 */
@Composable
fun ShutterOverlay(
    ref: ShutterWidgetRef,
    label: String,
    onDismiss: () -> Unit,
    onProviderMissing: () -> Unit,
) {
    val context = LocalContext.current
    val appWidgetManager = remember { AppWidgetManager.getInstance(context) }
    val providerInfo = remember(ref) {
        appWidgetManager.installedProviders.firstOrNull {
            it.provider == ComponentName(ref.providerPackage, ref.providerClassName)
        }
    }

    if (providerInfo == null) {
        LaunchedEffect(ref) {
            onProviderMissing()
            onDismiss()
        }
        return
    }

    DismissableBottomSheet(
        expanded = true,
        onDismissRequest = onDismiss,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 8.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        painterResource(R.drawable.close_24px),
                        contentDescription = stringResource(R.string.close),
                    )
                }
            }
            val density = LocalDensity.current
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(with(density) { providerInfo.minHeight.toDp() })
                    .padding(16.dp),
            ) {
                AppWidgetHost(
                    widgetInfo = providerInfo,
                    widgetId = ref.widgetId,
                )
            }
        }
    }
}
