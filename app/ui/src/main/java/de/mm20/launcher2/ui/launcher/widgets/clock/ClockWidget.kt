package de.mm20.launcher2.ui.launcher.widgets.clock
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import de.mm20.launcher2.ui.theme.LocalOnWallpaper
import de.mm20.launcher2.ui.theme.rememberWallpaperContentStyle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.preferences.BatteryStatusVisibility
import de.mm20.launcher2.preferences.ClockWidgetAlignment
import de.mm20.launcher2.preferences.ClockWidgetColors
import de.mm20.launcher2.preferences.ClockWidgetStyle
import de.mm20.launcher2.preferences.ui.ClockWidgetSettings
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.base.LocalTime
import de.mm20.launcher2.ui.component.Banner
import de.mm20.launcher2.ui.component.DismissableBottomSheet
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.launcher.widgets.clock.clocks.AnalogClock
import de.mm20.launcher2.ui.launcher.widgets.clock.clocks.BinaryClock
import de.mm20.launcher2.ui.launcher.widgets.clock.clocks.CustomClock
import de.mm20.launcher2.ui.launcher.widgets.clock.clocks.DigitalClock1
import de.mm20.launcher2.ui.launcher.widgets.clock.clocks.DigitalClock2
import de.mm20.launcher2.ui.launcher.widgets.clock.clocks.OrbitClock
import de.mm20.launcher2.ui.launcher.widgets.clock.clocks.SegmentClock
import de.mm20.launcher2.ui.launcher.widgets.clock.parts.PartProvider
import de.mm20.launcher2.ui.locals.LocalTimeFormat
import de.mm20.launcher2.ui.settings.clockwidget.ClockWidgetSettingsScreenVM
import de.mm20.launcher2.ui.utils.isTwentyFourHours
import org.koin.compose.koinInject

@Composable
fun ClockWidget(
    modifier: Modifier = Modifier,
    fillScreenHeight: Boolean,
    editMode: Boolean = false,
) {
    val viewModel: ClockWidgetVM = viewModel()
    val context = LocalContext.current
    val compact by viewModel.compactLayout.collectAsState()
    val clockStyle by viewModel.clockStyle.collectAsState()
    val color by viewModel.color.collectAsState()
    val alignment by viewModel.alignment.collectAsState()
    val time = LocalTime.current

    val readability = rememberWallpaperContentStyle(
        when (color) {
            ClockWidgetColors.Dark -> true
            ClockWidgetColors.Light -> false
            else -> null
        },
        backing = false,
    )
    val darkColors = readability.darkText

    val contentColor =
        if (darkColors) {
            Color.Black
        } else {
            Color.White
        }

    LaunchedEffect(time) {
        viewModel.updateTime(time)
    }

    val partProvider by remember { viewModel.getActivePart(context) }.collectAsStateWithLifecycle(
        null
    )

    AnimatedContent(editMode, label = "ClockWidget") {
        if (it) {
            var configure by remember { mutableStateOf(false) }
            Column {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shadowElevation = 2.dp,
                    tonalElevation = 2.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(24.dp))
                        Text(
                            text = stringResource(id = I18nR.string.preference_screen_clockwidget),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1
                        )
                        IconButton(onClick = {
                            configure = true
                        }) {
                            Icon(
                                painterResource(BaseR.drawable.tune_24px),
                                contentDescription = stringResource(I18nR.string.settings)
                            )
                        }
                    }
                }
                HorizontalDivider()
                ConfigureClockWidgetSheet(expanded = configure, onDismiss = { configure = false })
            }
        } else {
            var configure by remember { mutableStateOf(false) }
            CompositionLocalProvider(LocalOnWallpaper provides true) {
                Column(modifier = modifier) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .then(if (fillScreenHeight) Modifier.weight(1f) else Modifier)
                            .fillMaxWidth()
                            .padding(horizontal = if (compact == true) 0.dp else 24.dp),
                        contentAlignment = when (alignment) {
                            ClockWidgetAlignment.Center -> Alignment.Center
                            ClockWidgetAlignment.Top -> Alignment.TopCenter
                            else -> Alignment.BottomCenter
                        }
                    ) {
                        // Landscape / split screen / desktop windows: when the area is too short for
                        // the large clock, use the single-line variant of the selected face.
                        val shrinkClock = shouldUseCompactClock(maxHeight.value)
                        CompositionLocalProvider(
                            LocalContentColor provides contentColor,
                            de.mm20.launcher2.ui.theme.LocalWallpaperSamples provides
                                if (readability.scrimAlpha > 0f) null else de.mm20.launcher2.ui.theme.LocalWallpaperSamples.current
                        ) {
                            if (compact == false) {
                                Column(
                                    modifier = readability.modifier,
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Box(
                                        modifier = Modifier.combinedClickable(
                                            enabled = clockStyle !is ClockWidgetStyle.Empty,
                                            indication = null,
                                            interactionSource = remember { MutableInteractionSource() },
                                            onLongClick = { configure = true },
                                        ) {
                                            viewModel.launchClockApp(context)
                                        }
                                    ) {
                                        Clock(clockStyle, shrinkClock, darkColors)
                                    }

                                    if (partProvider != null) {
                                        DynamicZone(
                                            modifier = Modifier.padding(bottom = 8.dp),
                                            compact = false,
                                            provider = partProvider,
                                        )
                                    }
                                }
                            }
                            if (compact == true) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(readability.modifier)
                                        .padding(end = 8.dp, bottom = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    if (partProvider != null) {
                                        DynamicZone(
                                            modifier = Modifier.weight(1f),
                                            compact = true,
                                            provider = partProvider,
                                        )
                                    }
                                    if (clockStyle !is ClockWidgetStyle.Empty) {
                                        Box(
                                            modifier = Modifier
                                                .padding(horizontal = 16.dp)
                                                .height(56.dp)
                                                .width(2.dp)
                                                .background(
                                                    LocalContentColor.current
                                                ),
                                        )
                                    }
                                    Box(
                                        modifier = Modifier.combinedClickable(
                                            enabled = clockStyle !is ClockWidgetStyle.Empty,
                                            indication = null,
                                            interactionSource = remember { MutableInteractionSource() },
                                            onLongClick = { configure = true },
                                        ) {
                                            viewModel.launchClockApp(context)
                                        }
                                    ) {
                                        Clock(clockStyle, true, darkColors)
                                    }
                                }
                            }
                        }
                    }
                    val dockProvider by viewModel.dockProvider.collectAsState()
                    if (dockProvider != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                        ) {
                            dockProvider?.Component(false)
                        }
                    }
                }
            }
            ConfigureClockWidgetSheet(expanded = configure, onDismiss = { configure = false })
        }
    }
}

/**
 * @param darkColors: use dark content color / suited for light backgrounds
 */
@Composable
fun Clock(
    style: ClockWidgetStyle?,
    compact: Boolean,
    darkColors: Boolean = false
) {
    val time = LocalTime.current
    val timeFormat = LocalTimeFormat.current
    val context = LocalContext.current
    val clockSettings: ClockWidgetSettings = koinInject()
    val showSeconds by clockSettings.showSeconds.collectAsState(initial = false)
    val monospaced by clockSettings.monospaced.collectAsState(initial = false)
    val useThemeColor by clockSettings.useThemeColor.collectAsState(initial = false)


    val isTwentyFourHours = timeFormat.isTwentyFourHours(context)

    // Graphical watch faces have no text, so describe the time for screen readers
    val describeTime = style is ClockWidgetStyle.Binary ||
            style is ClockWidgetStyle.Analog ||
            style is ClockWidgetStyle.Orbit ||
            style is ClockWidgetStyle.Segment
    val timeDescription = if (describeTime) {
        android.text.format.DateFormat.format(
            if (isTwentyFourHours) "HH:mm" else "h:mm a",
            time
        ).toString()
    } else ""

    Box(
        modifier = if (describeTime) {
            Modifier.semantics(mergeDescendants = true) { contentDescription = timeDescription }
        } else Modifier
    ) {
        when (style) {
            is ClockWidgetStyle.Digital1 -> DigitalClock1(
                time = time,
                compact = compact,
                showSeconds = showSeconds,
                twentyFourHours = isTwentyFourHours,
                monospaced = monospaced,
                useThemeColor = useThemeColor,
                darkColors = darkColors,
                style = style,
            )

            is ClockWidgetStyle.Digital2 -> DigitalClock2(
                time = time,
                compact = compact,
                showSeconds = showSeconds,
                twentyFourHours = isTwentyFourHours,
                monospaced = monospaced,
                useThemeColor = useThemeColor,
                darkColors = darkColors,
            )

            is ClockWidgetStyle.Binary -> BinaryClock(
                time = time,
                compact = compact,
                showSeconds = showSeconds,
                twentyFourHours = isTwentyFourHours,
                useThemeColor = useThemeColor,
                darkColors = darkColors,
            )

            is ClockWidgetStyle.Analog -> AnalogClock(
                time,
                compact,
                showSeconds,
                useThemeColor,
                darkColors,
                style
            )

            is ClockWidgetStyle.Orbit -> OrbitClock(
                time = time,
                compact = compact,
                showSeconds = showSeconds,
                twentyFourHours = isTwentyFourHours,
                monospaced = monospaced,
                useThemeColor = useThemeColor,
                darkColors = darkColors,
            )

            is ClockWidgetStyle.Segment -> SegmentClock(
                time = time,
                compact = compact,
                showSeconds = showSeconds,
                twentyFourHours = isTwentyFourHours,
                useThemeColor = useThemeColor,
                darkColors = darkColors,
            )

            is ClockWidgetStyle.Custom -> CustomClock(style, compact, useThemeColor, darkColors)
            is ClockWidgetStyle.Empty -> {}
            else -> {}
        }
    }
}

@Composable
fun DynamicZone(
    modifier: Modifier = Modifier,
    compact: Boolean,
    provider: PartProvider?,
) {
    Column(
        modifier = modifier
    ) {
        provider?.Component(compact)
    }
}

@Composable
fun ConfigureClockWidgetSheet(
    expanded: Boolean,
    onDismiss: () -> Unit,
) {
    DismissableBottomSheet(expanded = expanded, onDismissRequest = onDismiss) {
        val context = LocalContext.current
        val viewModel: ClockWidgetSettingsScreenVM = viewModel()
        val compact by viewModel.compact.collectAsState()
        val color by viewModel.color.collectAsState()
        val style by viewModel.clockStyle.collectAsState()
        val fillHeight by viewModel.fillHeight.collectAsState()
        val widgetsOnHome by viewModel.widgetsOnHome.collectAsState()
        val alignment by viewModel.alignment.collectAsState()
        val showSeconds by viewModel.showSeconds.collectAsState()
        val monospaced by viewModel.monospaced.collectAsState()
        val useAccentColor by viewModel.useThemeColor.collectAsState()
        val parts by viewModel.parts.collectAsState()
        val smartspacer by viewModel.useSmartspacer.collectAsState()

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .fillMaxWidth()
                .padding(16.dp)
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
            ) {
                ToggleButton(
                    modifier = Modifier.weight(1f),
                    checked = compact == false,
                    onCheckedChange = {
                        if (it) viewModel.setCompact(false)
                    },
                    shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                ) {
                    Icon(
                        painterResource(if (compact == false) BaseR.drawable.check_20px else BaseR.drawable.splitscreen_top_20px),
                        null,
                        modifier = Modifier
                            .padding(end = ToggleButtonDefaults.IconSpacing)
                            .size(ToggleButtonDefaults.IconSize)
                    )
                    Text(text = stringResource(I18nR.string.preference_clockwidget_layout_vertical))
                }
                ToggleButton(
                    modifier = Modifier.weight(1f),
                    checked = compact == true,
                    onCheckedChange = {
                        if (it) viewModel.setCompact(true)
                    },
                    shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                ) {
                    Icon(
                        painterResource(if (compact == true) BaseR.drawable.check_20px else BaseR.drawable.splitscreen_right_20px),
                        null,
                        modifier = Modifier
                            .padding(end = ToggleButtonDefaults.IconSpacing)
                            .size(ToggleButtonDefaults.IconSize)
                    )
                    Text(text = stringResource(I18nR.string.preference_clockwidget_layout_horizontal))
                }
            }

            val availableStyles by viewModel.availableClockStyles.collectAsState()

            if (color != null && compact != null && availableStyles.isNotEmpty()) {
                WatchFaceSelector(
                    styles = availableStyles,
                    compact = compact!!,
                    colors = color!!,
                    themeColors = useAccentColor,
                    selected = style,
                    onSelect = {
                        viewModel.setClockStyle(it)
                    })
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
            ) {
                ToggleButton(
                    modifier = Modifier.weight(1f),
                    checked = color == ClockWidgetColors.Auto,
                    onCheckedChange = {
                        if (it) viewModel.setColor(ClockWidgetColors.Auto)
                    },
                    shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                ) {
                    Icon(
                        painterResource(BaseR.drawable.auto_awesome_24dp),
                        contentDescription = stringResource(I18nR.string.preference_system_bar_icons_auto),
                    )
                }
                ToggleButton(
                    modifier = Modifier.weight(1f),
                    checked = color == ClockWidgetColors.Dark,
                    onCheckedChange = {
                        if (it) viewModel.setColor(ClockWidgetColors.Dark)
                    },
                    shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
                ) {
                    Icon(
                        painterResource(BaseR.drawable.light_mode_24px),
                        contentDescription = stringResource(I18nR.string.preference_system_bar_icons_dark),
                    )
                }
                ToggleButton(
                    modifier = Modifier.weight(1f),
                    checked = color == ClockWidgetColors.Light,
                    onCheckedChange = {
                        if (it) viewModel.setColor(ClockWidgetColors.Light)
                    },
                    shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                ) {
                    Icon(
                        painterResource(BaseR.drawable.dark_mode_24px),
                        contentDescription = stringResource(I18nR.string.preference_system_bar_icons_light),
                    )
                }
            }
            OutlinedCard(
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SwitchPreference(
                        title = stringResource(I18nR.string.widget_use_theme_colors),
                        icon = BaseR.drawable.palette_24px,
                        value = useAccentColor,
                        onValueChanged = {
                            viewModel.setUseThemeColor(it)
                        }
                    )
                    AnimatedVisibility(compact == false && style !is ClockWidgetStyle.Custom) {
                        SwitchPreference(
                            title = stringResource(I18nR.string.preference_clock_widget_show_seconds),
                            icon = BaseR.drawable.timer_24px,
                            value = showSeconds,
                            onValueChanged = {
                                viewModel.setShowSeconds(it)
                            }
                        )
                    }
                    AnimatedVisibility(
                        style is ClockWidgetStyle.Digital1 ||
                                style is ClockWidgetStyle.Digital2 ||
                                style is ClockWidgetStyle.Orbit
                    ) {
                        SwitchPreference(
                            title = stringResource(I18nR.string.preference_clock_widget_monospaced),
                            icon = BaseR.drawable._123_24px,
                            value = monospaced,
                            onValueChanged = {
                                viewModel.setMonospaced(it)
                            }
                        )
                    }
                }
            }
            OutlinedCard(
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SwitchPreference(
                        title = stringResource(I18nR.string.preference_clock_widget_fill_height),
                        icon = BaseR.drawable.fit_page_height_24px,
                        value = fillHeight == true || widgetsOnHome == false,
                        onValueChanged = {
                            viewModel.setFillHeight(it)
                        },
                        enabled = widgetsOnHome == true,
                    )
                    var showDropdown by remember { mutableStateOf(false) }
                    Preference(
                        title = stringResource(I18nR.string.preference_clock_widget_alignment),
                        summary = when (alignment) {
                            ClockWidgetAlignment.Top -> stringResource(I18nR.string.preference_clock_widget_alignment_top)
                            ClockWidgetAlignment.Center -> stringResource(I18nR.string.preference_clock_widget_alignment_center)
                            else -> stringResource(I18nR.string.preference_clock_widget_alignment_bottom)
                        },
                        icon = when (alignment) {
                            ClockWidgetAlignment.Top -> BaseR.drawable.align_vertical_top_24px
                            ClockWidgetAlignment.Center -> BaseR.drawable.align_vertical_center_24px
                            else -> BaseR.drawable.align_vertical_bottom_24px
                        },
                        onClick = {
                            showDropdown = true
                        },
                        enabled = fillHeight == true || widgetsOnHome == false,
                    )
                    DropdownMenuPopup(
                        expanded = showDropdown,
                        onDismissRequest = { showDropdown = false }) {
                        DropdownMenuGroup(
                            shapes = MenuDefaults.groupShapes()
                        ) {
                            SelectableDropdownMenuItem(
                                shapes = MenuDefaults.itemShape(0, 3),
                                selected = alignment == ClockWidgetAlignment.Top,
                                selectedLeadingIcon = {
                                    Icon(
                                        painterResource(BaseR.drawable.check_24px),
                                        null
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        painterResource(BaseR.drawable.align_vertical_top_24px),
                                        null
                                    )
                                },
                                text = { Text(stringResource(I18nR.string.preference_clock_widget_alignment_top)) },
                                onClick = {
                                    viewModel.setAlignment(ClockWidgetAlignment.Top)
                                    showDropdown = false
                                }
                            )
                            SelectableDropdownMenuItem(
                                shapes = MenuDefaults.itemShape(1, 3),
                                selected = alignment == ClockWidgetAlignment.Center,
                                selectedLeadingIcon = {
                                    Icon(
                                        painterResource(BaseR.drawable.check_24px),
                                        null
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        painterResource(BaseR.drawable.align_vertical_center_24px),
                                        null
                                    )
                                },
                                text = { Text(stringResource(I18nR.string.preference_clock_widget_alignment_center)) },
                                onClick = {
                                    viewModel.setAlignment(ClockWidgetAlignment.Center)
                                    showDropdown = false
                                })
                            SelectableDropdownMenuItem(
                                shapes = MenuDefaults.itemShape(2, 3),
                                selected = alignment == ClockWidgetAlignment.Bottom,
                                selectedLeadingIcon = {
                                    Icon(
                                        painterResource(BaseR.drawable.check_24px),
                                        null
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        painterResource(BaseR.drawable.align_vertical_bottom_24px),
                                        null
                                    )
                                },
                                text = { Text(stringResource(I18nR.string.preference_clock_widget_alignment_bottom)) },
                                onClick = {
                                    viewModel.setAlignment(ClockWidgetAlignment.Bottom)
                                    showDropdown = false
                                })
                        }
                    }
                }
            }
            Text(
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.secondary,
                text = stringResource(I18nR.string.preference_clockwidget_dynamic_zone)
            )
            OutlinedCard(
                modifier = Modifier,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (smartspacer == true) {
                        Banner(
                            modifier = Modifier.padding(16.dp),
                            text = stringResource(I18nR.string.preference_clockwidget_smartspacer),
                            icon = BaseR.drawable.info_24px,
                            primaryAction = {
                                Button(
                                    onClick = {
                                        viewModel.disableSmartspacer()
                                    }
                                ) {
                                    Text(stringResource(I18nR.string.turn_off))
                                }
                            }
                        )
                    }
                    if (smartspacer == false) {
                        SwitchPreference(
                            title = stringResource(I18nR.string.preference_clockwidget_date_part),
                            summary = stringResource(I18nR.string.preference_clockwidget_date_part_summary),
                            icon = BaseR.drawable.today_24px,
                            value = parts?.date == true,
                            onValueChanged = {
                                viewModel.setDatePart(it)
                            }
                        )
                        SwitchPreference(
                            title = stringResource(I18nR.string.preference_clockwidget_weather_part),
                            summary = stringResource(I18nR.string.preference_clockwidget_weather_part_summary),
                            icon = BaseR.drawable.light_mode_24px,
                            value = parts?.weather == true,
                            onValueChanged = {
                                val activity = context as? AppCompatActivity
                                if (activity != null) {
                                    viewModel.setWeatherPart(it, activity)
                                }
                            }
                        )
                        SwitchPreference(
                            title = stringResource(I18nR.string.preference_clockwidget_music_part),
                            summary = stringResource(I18nR.string.preference_clockwidget_music_part_summary),
                            icon = BaseR.drawable.music_note_24px,
                            value = parts?.music == true,
                            onValueChanged = {
                                viewModel.setMusicPart(it)
                            }
                        )
                        SwitchPreference(
                            title = stringResource(I18nR.string.preference_clockwidget_alarm_part),
                            summary = stringResource(I18nR.string.preference_clockwidget_alarm_part_summary),
                            icon = BaseR.drawable.alarm_24px,
                            value = parts?.alarm == true,
                            onValueChanged = {
                                viewModel.setAlarmPart(it)
                            }
                        )
                        ListPreference(
                            title = stringResource(I18nR.string.preference_clockwidget_battery_part),
                            icon = BaseR.drawable.battery_full_24px,
                            value = parts?.battery,
                            onValueChanged = {
                                    if (it != null) viewModel.setBatteryPart(it)
                            },
                            items = listOf(
                                stringResource(I18nR.string.preference_clockwidget_battery_part_hide) to BatteryStatusVisibility.Hide,
                                stringResource(I18nR.string.preference_clockwidget_battery_part_show) to BatteryStatusVisibility.Show,
                                stringResource(I18nR.string.preference_clockwidget_battery_part_always_show) to BatteryStatusVisibility.Always
                            )
                        )
                    }
                }
            }
        }
    }
}
