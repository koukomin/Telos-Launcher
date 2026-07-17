package de.mm20.launcher2.ui.launcher.shutters

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import de.mm20.launcher2.preferences.ShutterWidgetRef
import de.mm20.launcher2.preferences.ui.ShutterSettings
import de.mm20.launcher2.ui.launcher.sheets.WidgetPickerSheet
import de.mm20.launcher2.widgets.AppWidget
import de.mm20.launcher2.widgets.Widget
import org.koin.compose.koinInject

/**
 * Persists [widget] as the shutter assigned to [packageName], if it's a real bound app widget
 * (built-in launcher widgets aren't backed by an [AppWidgetProviderInfo] and can't be hosted
 * from a swipe-up popup).
 */
fun assignShutterWidget(
    context: Context,
    shutterSettings: ShutterSettings,
    packageName: String,
    widget: Widget,
) {
    if (widget !is AppWidget) return
    val providerInfo = AppWidgetManager.getInstance(context)
        .getAppWidgetInfo(widget.config.widgetId) ?: return
    shutterSettings.setWidget(
        packageName,
        ShutterWidgetRef(
            widgetId = widget.config.widgetId,
            providerPackage = providerInfo.provider.packageName,
            providerClassName = providerInfo.provider.className,
        )
    )
}

/**
 * Shows either the app's existing shutter widget, or (if none is assigned yet) the widget
 * picker so the user can assign one on first swipe-up. Reuses the same widget-binding flow
 * as home screen widgets ([WidgetPickerSheet]) rather than a dedicated per-app picker.
 */
@Composable
fun ShutterGate(
    packageName: String,
    label: String,
    widgetRef: ShutterWidgetRef?,
    onDismiss: () -> Unit,
) {
    val shutterSettings = koinInject<ShutterSettings>()
    val context = LocalContext.current

    if (widgetRef != null) {
        ShutterOverlay(
            ref = widgetRef,
            label = label,
            onDismiss = onDismiss,
            onProviderMissing = {
                shutterSettings.setWidget(packageName, null)
            },
        )
    } else {
        WidgetPickerSheet(
            expanded = true,
            includeBuiltinWidgets = false,
            onWidgetSelected = { widget ->
                assignShutterWidget(context, shutterSettings, packageName, widget)
            },
            onDismiss = onDismiss,
        )
    }
}
