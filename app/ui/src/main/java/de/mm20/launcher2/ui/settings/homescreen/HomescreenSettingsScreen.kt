package de.mm20.launcher2.ui.settings.homescreen

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.preferences.SearchBarColors
import de.mm20.launcher2.preferences.SearchBarStyle
import de.mm20.launcher2.preferences.SystemBarColors
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.Banner
import de.mm20.launcher2.ui.component.DismissableBottomSheet
import de.mm20.launcher2.ui.component.SearchBar
import de.mm20.launcher2.ui.component.SearchBarLevel
import de.mm20.launcher2.ui.component.preferences.ColorPreference
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SliderPreference
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.launcher.scaffold.components.EXTRA_HOME_SCREENS_ENABLED
import de.mm20.launcher2.ui.launcher.widgets.clock.ConfigureClockWidgetSheet
import de.mm20.launcher2.ui.locals.LocalDarkTheme
import de.mm20.launcher2.ui.locals.LocalPreferDarkContentOverWallpaper
import de.mm20.launcher2.ui.settings.desktopmode.DesktopModeSettingsRoute
import de.mm20.launcher2.ui.settings.homescreen.dock.DockSettingsRoute
import de.mm20.launcher2.ui.settings.homescreen.wallpaper.WallpaperSettingsRoute
import de.mm20.launcher2.ui.locals.LocalBackStack
import kotlinx.serialization.Serializable

@Serializable
data object HomescreenSettingsRoute : NavKey

@Composable
fun HomescreenSettingsScreen() {
    val viewModel: HomescreenSettingsScreenVM =
        viewModel(factory = HomescreenSettingsScreenVM.Factory)

    val context = LocalContext.current
    val backStack = LocalBackStack.current

    val fixedRotation by viewModel.fixedRotation.collectAsStateWithLifecycle(null)
    val widgetsOnHomeScreen by viewModel.widgetsOnHomeScreen.collectAsStateWithLifecycle(null)
    val editButton by viewModel.widgetEditButton.collectAsStateWithLifecycle(null)
    val searchBarStyle by viewModel.searchBarStyle.collectAsStateWithLifecycle(null)
    val searchBarColor by viewModel.searchBarColor.collectAsStateWithLifecycle(null)
    val searchBarColorDrawer by viewModel.searchBarColorDrawer.collectAsStateWithLifecycle(null)
    val bottomSearchBar by viewModel.bottomSearchBar.collectAsStateWithLifecycle(null)
    val fixedSearchBar by viewModel.fixedSearchBar.collectAsStateWithLifecycle(null)
    val lightStatusBar by viewModel.statusBarIcons.collectAsStateWithLifecycle(null)
    val lightNavBar by viewModel.navBarIcons.collectAsStateWithLifecycle(null)
    val hideStatusBar by viewModel.hideStatusBar.collectAsStateWithLifecycle(null)
    val hideNavBar by viewModel.hideNavBar.collectAsStateWithLifecycle(null)
    val chargingAnimation by viewModel.chargingAnimation.collectAsStateWithLifecycle(null)
    val homeScreenPageCount by viewModel.homeScreenPageCount.collectAsStateWithLifecycle(null)
    val desktopLocked by viewModel.desktopLocked.collectAsStateWithLifecycle(false)

    PreferenceScreen(title = stringResource(id = R.string.preference_screen_homescreen)) {
        item {
            PreferenceCategory {
                Preference(
                    icon = R.drawable.wallpaper_24px,
                    title = stringResource(id = R.string.wallpaper),
                    summary = stringResource(id = R.string.preference_wallpaper_summary),
                    onClick = {
                        backStack.add(WallpaperSettingsRoute)
                    }
                )
                Preference(
                    icon = R.drawable.splitscreen_right_20px,
                    title = stringResource(id = R.string.preference_screen_desktop_mode),
                    summary = stringResource(id = R.string.preference_screen_desktop_mode_summary),
                    onClick = {
                        backStack.add(DesktopModeSettingsRoute)
                    }
                )
                Preference(
                    icon = R.drawable.today_24px,
                    title = stringResource(R.string.preference_clockwidget_favorites_part),
                    summary = stringResource(R.string.preference_clockwidget_favorites_part_summary),
                    onClick = {
                        backStack.add(DockSettingsRoute)
                    }
                )
                SwitchPreference(
                    title = stringResource(R.string.preference_desktop_lock),
                    summary = stringResource(R.string.preference_desktop_lock_summary),
                    icon = R.drawable.lock_24px,
                    value = desktopLocked,
                    onValueChanged = { viewModel.setDesktopLocked(it) }
                )
                SwitchPreference(
                    title = stringResource(R.string.preference_layout_fixed_rotation),
                    summary = stringResource(R.string.preference_layout_fixed_rotation_summary),
                    value = fixedRotation == true,
                    onValueChanged = {
                        viewModel.setFixedRotation(it)
                    },
                )
                // Extra home screens feature is disabled for this build (see
                // EXTRA_HOME_SCREENS_ENABLED in HomeScreenPager.kt) - hidden here so there's no
                // way to turn it on, rather than left visible but non-functional.
                if (EXTRA_HOME_SCREENS_ENABLED) {
                    SliderPreference(
                        title = stringResource(R.string.preference_home_screen_page_count),
                        value = homeScreenPageCount ?: 1,
                        min = 1,
                        max = 9,
                        onValueChanged = {
                            viewModel.setHomeScreenPageCount(it)
                        },
                    )
                    AnimatedVisibility((homeScreenPageCount ?: 1) > 1) {
                        Banner(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            text = stringResource(R.string.preference_home_screen_page_count_warning),
                            icon = R.drawable.info_24px,
                        )
                    }
                }
            }
        }
        item {
            PreferenceCategory(
                title = stringResource(id = R.string.preference_category_widgets)
            ) {
                Preference(
                    title = stringResource(R.string.preference_screen_clockwidget),
                    summary = stringResource(R.string.preference_screen_clockwidget_summary),
                    onClick = {
                        viewModel.showClockWidgetSheet = true
                    }
                )
                SwitchPreference(
                    title = stringResource(R.string.preference_widgets_on_home_screen),
                    summary = stringResource(R.string.preference_widgets_on_home_screen_summary),
                    value = widgetsOnHomeScreen == true,
                    onValueChanged = {
                        viewModel.setWidgetsOnHomeScreen(it)
                    })
                SwitchPreference(
                    title = stringResource(id = R.string.preference_edit_button),
                    summary = stringResource(id = R.string.preference_widgets_edit_button_summary),
                    value = editButton == true,
                    onValueChanged = {
                        viewModel.setWidgetEditButton(it)
                    })
                Preference(
                    title = stringResource(R.string.preference_reset_widgets_tutorial),
                    summary = stringResource(R.string.preference_reset_widgets_tutorial_summary),
                    onClick = {
                        viewModel.resetWidgetsTutorial()
                    }
                )
            }

        }
        item {
            PreferenceCategory(stringResource(R.string.preference_category_searchbar)) {
                SearchBarStylePreference(
                    title = stringResource(R.string.preference_search_bar_style),
                    summary = stringResource(R.string.preference_search_bar_style_summary),
                    value = searchBarStyle,
                    colors = searchBarColor,
                    onStyleChanged = {
                        viewModel.setSearchBarStyle(it)
                    },
                    onColorsChanged = {
                        viewModel.setSearchBarColor(it)
                    }
                )
                SwitchPreference(
                    title = stringResource(R.string.preference_search_bar_colors_drawer_override),
                    summary = stringResource(R.string.preference_search_bar_colors_drawer_override_summary),
                    value = searchBarColorDrawer != null,
                    onValueChanged = {
                        viewModel.setSearchBarColorDrawer(if (it) SearchBarColors.Auto else null)
                    }
                )
                AnimatedVisibility(searchBarColorDrawer != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
                    ) {
                        ToggleButton(
                            modifier = Modifier.weight(1f),
                            checked = searchBarColorDrawer == SearchBarColors.Auto,
                            onCheckedChange = { viewModel.setSearchBarColorDrawer(SearchBarColors.Auto) },
                            shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                        ) {
                            Icon(painterResource(R.drawable.auto_awesome_20dp), contentDescription = null)
                        }
                        ToggleButton(
                            modifier = Modifier.weight(1f),
                            checked = searchBarColorDrawer == SearchBarColors.Light,
                            onCheckedChange = { viewModel.setSearchBarColorDrawer(SearchBarColors.Light) },
                            shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
                        ) {
                            Icon(painterResource(R.drawable.light_mode_24px), contentDescription = null)
                        }
                        ToggleButton(
                            modifier = Modifier.weight(1f),
                            checked = searchBarColorDrawer == SearchBarColors.Dark,
                            onCheckedChange = { viewModel.setSearchBarColorDrawer(SearchBarColors.Dark) },
                            shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                        ) {
                            Icon(painterResource(R.drawable.dark_mode_24px), contentDescription = null)
                        }
                    }
                }

                ListPreference(
                    title = stringResource(R.string.preference_layout_search_bar_position),
                    items = listOf(
                        stringResource(R.string.search_bar_position_top) to false,
                        stringResource(R.string.search_bar_position_bottom) to true,
                    ),
                    value = bottomSearchBar,
                    onValueChanged = {
                        if (it != null) viewModel.setBottomSearchBar(it)
                    },
                )
                SwitchPreference(
                    title = stringResource(R.string.preference_layout_fixed_search_bar),
                    summary = stringResource(R.string.preference_layout_fixed_search_bar_summary),
                    value = fixedSearchBar == true,
                    onValueChanged = {
                        viewModel.setFixedSearchBar(it)
                    },
                )
            }
        }
        item {
            PreferenceCategory(stringResource(R.string.preference_category_animations)) {
                SwitchPreference(
                    title = stringResource(R.string.preference_charging_animation),
                    summary = stringResource(R.string.preference_charging_animation_summary),
                    value = chargingAnimation == true,
                    onValueChanged = {
                        viewModel.setChargingAnimation(it)
                    }
                )
            }
        }
        item {

            PreferenceCategory(stringResource(R.string.preference_category_system_bars)) {
                ListPreference(
                    title = stringResource(R.string.preference_status_bar_icons),
                    value = lightStatusBar,
                    items = listOf(
                        stringResource(R.string.preference_system_bar_icons_auto) to SystemBarColors.Auto,
                        stringResource(R.string.preference_system_bar_icons_light) to SystemBarColors.Light,
                        stringResource(R.string.preference_system_bar_icons_dark) to SystemBarColors.Dark,
                    ),
                    onValueChanged = {
                        if (it != null) viewModel.setLightStatusBar(it)
                    }
                )
                ListPreference(
                    title = stringResource(R.string.preference_nav_bar_icons),
                    value = lightNavBar,
                    items = listOf(
                        stringResource(R.string.preference_system_bar_icons_auto) to SystemBarColors.Auto,
                        stringResource(R.string.preference_system_bar_icons_light) to SystemBarColors.Light,
                        stringResource(R.string.preference_system_bar_icons_dark) to SystemBarColors.Dark,
                    ),
                    onValueChanged = {
                        if (it != null) viewModel.setLightNavBar(it)
                    }
                )
                SwitchPreference(
                    title = stringResource(R.string.preference_hide_status_bar),
                    value = hideStatusBar == true,
                    onValueChanged = {
                        viewModel.setHideStatusBar(it)
                    }
                )
                SwitchPreference(
                    title = stringResource(R.string.preference_hide_nav_bar),
                    value = hideNavBar == true,
                    onValueChanged = {
                        viewModel.setHideNavBar(it)
                    }
                )
            }
        }
    }

    ConfigureClockWidgetSheet(
        expanded = viewModel.showClockWidgetSheet,
        onDismiss = { viewModel.showClockWidgetSheet = false }
    )
}

@Composable
fun SearchBarStylePreference(
    title: String,
    summary: String? = null,
    value: SearchBarStyle?,
    colors: SearchBarColors?,
    onStyleChanged: (SearchBarStyle) -> Unit,
    onColorsChanged: (SearchBarColors) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    Preference(title = title, summary = summary, onClick = { showDialog = true })

    DismissableBottomSheet(
        expanded = showDialog,
        onDismissRequest = {
            showDialog = false
        }
    ) {
        val styles = SearchBarStyle.entries

        val darkColors =
            LocalPreferDarkContentOverWallpaper.current && colors == SearchBarColors.Auto || colors == SearchBarColors.Dark

        Box(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .navigationBarsPadding(),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                for (style in styles) {
                    Column {
                        Row(
                            modifier = Modifier
                                .padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(
                                    when (style) {
                                        SearchBarStyle.Transparent -> R.string.preference_search_bar_style_transparent
                                        SearchBarStyle.Solid -> R.string.preference_search_bar_style_solid
                                        SearchBarStyle.Hidden -> R.string.preference_search_bar_style_hidden
                                    }
                                ),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = {
                                    onStyleChanged(style)
                                }
                            ) {
                                Icon(
                                    painterResource(
                                        if (style == value) R.drawable.check_circle_24px_filled
                                        else R.drawable.circle_24px
                                    ),
                                    contentDescription = null,
                                    tint = if (style == value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .border(
                                    if (style == value) 4.dp else 2.dp,
                                    if (style == value) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    MaterialTheme.shapes.medium,
                                )
                                .clip(MaterialTheme.shapes.medium)
                                .background(
                                    when {
                                        style != SearchBarStyle.Transparent -> MaterialTheme.colorScheme.inverseSurface
                                        LocalDarkTheme.current != darkColors -> MaterialTheme.colorScheme.surfaceContainer
                                        else -> MaterialTheme.colorScheme.inverseSurface
                                    }
                                )
                                .height(IntrinsicSize.Min)
                        ) {
                            SearchBar(
                                modifier = Modifier
                                    .padding(16.dp),
                                level = SearchBarLevel.Resting,
                                style = style,
                                value = "",
                                onValueChange = {},
                                readOnly = true,
                                darkColors = darkColors,
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable {
                                        onStyleChanged(style)
                                    }
                            )
                        }
                        if (style == SearchBarStyle.Transparent) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
                            ) {
                                ToggleButton(
                                    modifier = Modifier.weight(1f),
                                    checked = colors == SearchBarColors.Auto,
                                    onCheckedChange = {
                                        onColorsChanged(SearchBarColors.Auto)
                                    },
                                    shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                                ) {
                                    Icon(
                                        painterResource(R.drawable.auto_awesome_20dp),
                                        contentDescription = null,
                                    )
                                }
                                ToggleButton(
                                    modifier = Modifier.weight(1f),
                                    checked = colors == SearchBarColors.Light,
                                    onCheckedChange = {
                                        onColorsChanged(SearchBarColors.Light)
                                    },
                                    shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
                                ) {
                                    Icon(
                                        painterResource(R.drawable.light_mode_24px),
                                        contentDescription = null,
                                    )
                                }
                                ToggleButton(
                                    modifier = Modifier.weight(1f),
                                    checked = colors == SearchBarColors.Dark,
                                    onCheckedChange = {
                                        onColorsChanged(SearchBarColors.Dark)
                                    },
                                    shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                                ) {
                                    Icon(
                                        painterResource(R.drawable.dark_mode_24px),
                                        contentDescription = null,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
