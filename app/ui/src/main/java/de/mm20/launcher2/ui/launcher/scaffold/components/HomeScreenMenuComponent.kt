package de.mm20.launcher2.ui.launcher.scaffold.components

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.preferences.WidgetScreenTarget
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.base.LocalAppWidgetHost
import de.mm20.launcher2.ui.launcher.scaffold.LauncherScaffoldState
import de.mm20.launcher2.ui.launcher.sheets.WidgetPickerSheet
import de.mm20.launcher2.ui.launcher.widgets.WidgetsVM
import de.mm20.launcher2.ui.settings.SettingsActivity
import de.mm20.launcher2.widgets.AppWidget
import de.mm20.launcher2.widgets.AppWidgetConfig
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * The menu shown on a long-press on an empty area of the home screen, reached via whichever
 * gesture slot has [de.mm20.launcher2.preferences.GestureAction.HomeScreenMenu] assigned - like
 * most stock launchers' "change wallpaper / add widget" menu.
 *
 * "Add widget" attempts the raw system [AppWidgetManager.ACTION_APPWIDGET_PICK] picker first.
 * That intent has had no AOSP handler since Google removed the built-in picker Activity, so on
 * stock Android this will almost always fall straight through to [WidgetPickerSheet] (the
 * launcher's own already-existing widget picker) - kept as a fallback rather than the primary
 * path so the feature still works even though the literally-requested system intent is
 * unavailable in practice. Either way, the resulting widget is added to the same widget list the
 * main home screen's [de.mm20.launcher2.ui.launcher.widgets.WidgetColumn] renders, so it can be
 * combined into a stack there using the existing stacking UI.
 */
internal object HomeScreenMenuComponent : ScaffoldComponent() {

    override val showSearchBar: Boolean = false

    @Composable
    override fun Component(
        modifier: Modifier,
        insets: PaddingValues,
        state: LauncherScaffoldState,
    ) {
        val context = LocalContext.current
        val density = LocalDensity.current
        val scope = rememberCoroutineScope()
        val widgetHost = LocalAppWidgetHost.current

        val widgetsViewModel: WidgetsVM = viewModel(
            key = "widgets-column-${WidgetScreenTarget.Default.id}",
            factory = WidgetsVM.Factory(WidgetScreenTarget.Default.id.toString()),
        )

        var showWidgetPicker by remember { mutableStateOf(false) }
        var pendingAppWidgetId by remember { mutableStateOf<Int?>(null) }

        fun dismiss() {
            scope.launch { state.navigateBack() }
        }

        val pickAppWidget = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val widgetId = pendingAppWidgetId
            pendingAppWidgetId = null
            if (widgetId == null) return@rememberLauncherForActivityResult
            if (result.resultCode != Activity.RESULT_OK) {
                // The raw picker can resolve to a real system activity (e.g. Android Settings'
                // own AppWidgetPickActivity) that then fails or cancels immediately for a caller
                // it doesn't recognize as a proper widget host - "resolvable" is not the same as
                // "will actually work". Fall back to our own picker instead of just giving up, so
                // the user still gets a working flow rather than the menu silently closing.
                widgetHost.deleteAppWidgetId(widgetId)
                showWidgetPicker = true
                return@rememberLauncherForActivityResult
            }
            val info = AppWidgetManager.getInstance(context).getAppWidgetInfo(widgetId)
            if (info == null || info.configure != null) {
                // Either the bind silently failed, or the provider needs a configuration step -
                // driving that requires a dedicated Activity (see WidgetPickerSheet's
                // BindAndConfigureAppWidgetActivity), which the raw picker intent doesn't give us
                // a hook for. Fall back to our own picker, which already handles both.
                widgetHost.deleteAppWidgetId(widgetId)
                showWidgetPicker = true
                return@rememberLauncherForActivityResult
            }
            widgetsViewModel.addWidget(
                AppWidget(
                    id = UUID.randomUUID(),
                    config = AppWidgetConfig(
                        widgetId = widgetId,
                        height = with(density) { info.minHeight.toDp() }.value.toInt(),
                        width = with(density) { info.minWidth.toDp() }.value.toInt(),
                    ),
                )
            )
            dismiss()
        }

        Box(
            modifier = modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { dismiss() },
        ) {
            Card(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(insets)
                    .padding(32.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {},
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    MenuItem(
                        icon = R.drawable.wallpaper_24px,
                        label = stringResource(R.string.home_screen_menu_change_wallpaper),
                        onClick = {
                            context.startActivity(
                                Intent(context, SettingsActivity::class.java).apply {
                                    putExtra(
                                        SettingsActivity.EXTRA_ROUTE,
                                        SettingsActivity.ROUTE_WALLPAPER,
                                    )
                                }
                            )
                            dismiss()
                        },
                    )
                    MenuItem(
                        icon = R.drawable.widgets_24px,
                        label = stringResource(R.string.widget_add_widget),
                        onClick = {
                            val pickIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK)
                            val resolvable = context.packageManager.resolveActivity(
                                pickIntent,
                                PackageManager.MATCH_DEFAULT_ONLY,
                            ) != null
                            if (!resolvable) {
                                showWidgetPicker = true
                            } else {
                                val widgetId = widgetHost.allocateAppWidgetId()
                                pendingAppWidgetId = widgetId
                                pickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                                pickAppWidget.launch(pickIntent)
                            }
                        },
                    )
                }
            }
        }

        WidgetPickerSheet(
            expanded = showWidgetPicker,
            onDismiss = {
                showWidgetPicker = false
                dismiss()
            },
            onWidgetSelected = {
                widgetsViewModel.addWidget(it)
                showWidgetPicker = false
                dismiss()
            },
        )
    }
}

@Composable
private fun MenuItem(
    icon: Int,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
