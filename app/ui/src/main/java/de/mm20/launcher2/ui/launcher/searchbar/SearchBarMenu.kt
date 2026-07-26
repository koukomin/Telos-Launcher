package de.mm20.launcher2.ui.launcher.searchbar

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.preferences.WidgetScreenTarget
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.base.LocalAppWidgetHost
import de.mm20.launcher2.ui.launcher.scaffold.components.currentHomeScreenWidgetScopeId
import de.mm20.launcher2.ui.launcher.sheets.WidgetPickerSheet
import de.mm20.launcher2.ui.launcher.widgets.WidgetsVM
import de.mm20.launcher2.ui.settings.SettingsActivity
import de.mm20.launcher2.widgets.AppWidget
import de.mm20.launcher2.widgets.AppWidgetConfig
import java.util.UUID

@Composable
fun RowScope.SearchBarMenu(
    searchBarValue: String,
    onInputClear: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var showOverflowMenu by remember { mutableStateOf(false) }
    val rightIcon = AnimatedImageVector.animatedVectorResource(R.drawable.anim_ic_menu_clear)

    // Same "whichever home screen page is currently visible" targeting as the long-press home
    // screen menu's own "Add widget" entry - see currentHomeScreenWidgetScopeId's kdoc.
    val widgetScopeId = currentHomeScreenWidgetScopeId()
    val widgetsViewModel: WidgetsVM = viewModel(
        key = "widgets-column-$widgetScopeId",
        factory = WidgetsVM.Factory((widgetScopeId ?: WidgetScreenTarget.Default.id).toString()),
    )
    val widgetHost = LocalAppWidgetHost.current
    var showWidgetPicker by remember { mutableStateOf(false) }
    var pendingAppWidgetId by remember { mutableStateOf<Int?>(null) }

    val pickAppWidget = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val widgetId = pendingAppWidgetId
        pendingAppWidgetId = null
        if (widgetId == null) return@rememberLauncherForActivityResult
        if (result.resultCode != Activity.RESULT_OK) {
            widgetHost.deleteAppWidgetId(widgetId)
            showWidgetPicker = true
            return@rememberLauncherForActivityResult
        }
        val info = AppWidgetManager.getInstance(context).getAppWidgetInfo(widgetId)
        if (info == null || info.configure != null) {
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
    }

    Box(contentAlignment = Alignment.TopEnd) {
        IconButton(
            onClick = {
                if (searchBarValue.isNotBlank()) onInputClear()
                else showOverflowMenu = true
            },
        ) {
            Icon(
                painter = rememberAnimatedVectorPainter(
                    rightIcon,
                    atEnd = searchBarValue.isNotEmpty()
                ),
                contentDescription = stringResource(if (searchBarValue.isNotBlank()) R.string.action_clear else R.string.action_more_actions),
                tint = LocalContentColor.current
            )
        }
        DropdownMenuPopup(
            expanded = showOverflowMenu,
            onDismissRequest = { showOverflowMenu = false },
        ) {
            DropdownMenuGroup(
                shapes = MenuDefaults.groupShapes(),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Start)
            ) {
                DropdownMenuItem(
                    shape = MenuDefaults.leadingItemShape,
                    onClick = {
                        context.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SET_WALLPAPER),
                                null
                            )
                        )
                        showOverflowMenu = false
                    },
                    text = {
                        Text(stringResource(R.string.wallpaper))
                    },
                    leadingIcon = {
                        Icon(painterResource(R.drawable.wallpaper_24px), contentDescription = null)
                    }
                )
                DropdownMenuItem(
                    shape = MenuDefaults.middleItemShape,
                    onClick = {
                        context.startActivity(
                            Intent(context, SettingsActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                        showOverflowMenu = false
                    },
                    text = {
                        Text(stringResource(R.string.settings))
                    },
                    leadingIcon = {
                        Icon(painterResource(R.drawable.settings_24px), contentDescription = null)
                    }
                )
                if (widgetScopeId != null) {
                    DropdownMenuItem(
                        shape = MenuDefaults.middleItemShape,
                        onClick = {
                            showOverflowMenu = false
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
                        text = {
                            Text(stringResource(R.string.widget_add_widget))
                        },
                        leadingIcon = {
                            Icon(painterResource(R.drawable.widgets_24px), contentDescription = null)
                        }
                    )
                }
                val colorScheme = MaterialTheme.colorScheme
                DropdownMenuItem(
                    shape = MenuDefaults.trailingItemShape,
                    onClick = {
                        CustomTabsIntent.Builder()
                            .setDefaultColorSchemeParams(
                                CustomTabColorSchemeParams.Builder()
                                    .setToolbarColor(colorScheme.primaryContainer.toArgb())
                                    .setSecondaryToolbarColor(colorScheme.secondaryContainer.toArgb())
                                    .build()
                            )
                            .build()
                            .launchUrl(
                                context,
                                Uri.parse("https://kvaesitso.mm20.de/docs/user-guide")
                            )
                        showOverflowMenu = false
                    },
                    text = {
                        Text(stringResource(R.string.help))
                    },
                    leadingIcon = {
                        Icon(painterResource(R.drawable.help_24px), contentDescription = null)
                    }
                )
            }
        }
    }

    WidgetPickerSheet(
        expanded = showWidgetPicker,
        onDismiss = { showWidgetPicker = false },
        onWidgetSelected = {
            widgetsViewModel.addWidget(it)
            showWidgetPicker = false
        },
    )
}