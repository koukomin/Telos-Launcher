package de.mm20.launcher2.ui.settings.search

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.plugin.PluginType
import de.mm20.launcher2.preferences.SettingsLockMethod
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.DismissableBottomSheet
import de.mm20.launcher2.ui.component.SmallMessage
import de.mm20.launcher2.ui.component.preferences.GuardedPreference
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.ListPreferenceItem
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.PreferenceWithSwitch
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.launcher.search.filters.SearchFilters
import de.mm20.launcher2.ui.launcher.search.recommendations.RecommendationInfoSheet
import de.mm20.launcher2.ui.locals.LocalBackStack
import de.mm20.launcher2.ui.settings.apps.AppSearchSettingsRoute
import de.mm20.launcher2.ui.settings.calendarsearch.CalendarProviderSettingsRoute
import de.mm20.launcher2.ui.settings.calendarsearch.CalendarSearchSettingsRoute
import de.mm20.launcher2.ui.settings.contacts.ContactsSettingsRoute
import de.mm20.launcher2.ui.settings.favorites.FavoritesSettingsRoute
import de.mm20.launcher2.ui.settings.filesearch.FileSearchSettingsRoute
import de.mm20.launcher2.ui.settings.filterbar.FilterBarSettingsRoute
import de.mm20.launcher2.ui.settings.hiddenitems.HiddenItemsSettingsRoute
import de.mm20.launcher2.ui.settings.locations.LocationsSettingsRoute
import de.mm20.launcher2.ui.settings.protection.SettingsLockVM
import de.mm20.launcher2.ui.settings.protection.authenticateSettings
import de.mm20.launcher2.ui.settings.searchactions.SearchActionsSettingsRoute
import de.mm20.launcher2.ui.settings.tags.TagsSettingsRoute
import de.mm20.launcher2.ui.settings.unitconverter.UnitConverterSettingsRoute
import de.mm20.launcher2.ui.settings.wikipedia.WikipediaSettingsRoute
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data object SearchSettingsRoute : NavKey

@Composable
fun SearchSettingsScreen() {

    val viewModel: SearchSettingsScreenVM = viewModel()
    val lockViewModel: SettingsLockVM = viewModel()
    val context = LocalContext.current
    val activity = LocalContext.current as? FragmentActivity
    val promptTitle = stringResource(R.string.settings_lock_prompt_title)
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val backStack = LocalBackStack.current

    var showFilterEditor by remember { mutableStateOf(false) }
    var showRecommendationInfo by remember { mutableStateOf(false) }
    var onUnlockAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showSetPinDialog by remember { mutableStateOf(false) }

    val lockSettings by lockViewModel.lockSensitiveSettings.collectAsStateWithLifecycle()
    val lockMethod by lockViewModel.lockMethod.collectAsStateWithLifecycle()
    val useCustomLock by lockViewModel.useCustomLock.collectAsStateWithLifecycle()
    val customLockHashed by lockViewModel.customLockHashed.collectAsStateWithLifecycle()

    val plugins by viewModel.plugins.collectAsStateWithLifecycle(null)
    val hasCalendarPlugins by remember { derivedStateOf { plugins?.any { it.plugin.type == PluginType.Calendar } } }
    val hasLocationPlugins by remember { derivedStateOf { plugins?.any { it.plugin.type == PluginType.LocationSearch } } }
    val hasContactPlugins by remember { derivedStateOf { plugins?.any { it.plugin.type == PluginType.ContactSearch } } }
    val isTasksAppInstalled by viewModel.isTasksAppInstalled.collectAsStateWithLifecycle()

    val hasAppShortcutsPermission by viewModel.hasAppShortcutPermission.collectAsStateWithLifecycle(
        null
    )
    val hasContactsPermission by viewModel.hasContactsPermission.collectAsStateWithLifecycle(null)
    val hasCalendarPermission by viewModel.hasCalendarPermission.collectAsStateWithLifecycle(null)
    val hasLocationPermission by viewModel.hasLocationPermission.collectAsStateWithLifecycle(null)

    val favorites by viewModel.favorites.collectAsStateWithLifecycle(null)
    val allApps by viewModel.allApps.collectAsStateWithLifecycle(null)
    val appShortcuts by viewModel.appShortcuts.collectAsStateWithLifecycle(null)
    val calendar by viewModel.calendarSearch.collectAsStateWithLifecycle(null)
    val reminders by viewModel.remindersSearch.collectAsStateWithLifecycle(null)
    val places by viewModel.placesSearch.collectAsStateWithLifecycle(null)
    val contacts by viewModel.contacts.collectAsStateWithLifecycle(null)
    val calculator by viewModel.calculator.collectAsStateWithLifecycle(null)
    val unitConverter by viewModel.unitConverter.collectAsStateWithLifecycle(null)
    val wikipedia by viewModel.wikipedia.collectAsStateWithLifecycle(null)
    val websites by viewModel.websites.collectAsStateWithLifecycle(null)


    val autoFocus by viewModel.autoFocus.collectAsStateWithLifecycle(null)
    val launchOnEnter by viewModel.launchOnEnter.collectAsStateWithLifecycle(null)
    val privateKeyboard by viewModel.privateKeyboard.collectAsStateWithLifecycle(null)
    val showAppRecommendations by viewModel.showAppRecommendations.collectAsStateWithLifecycle(null)
    val reverseSearchResults by viewModel.reverseSearchResults.collectAsStateWithLifecycle(null)
    val filterBar by viewModel.filterBar.collectAsStateWithLifecycle(null)

    PreferenceScreen(title = stringResource(R.string.preference_screen_search)) {
        item {
            PreferenceCategory {
                PreferenceWithSwitch(
                    title = stringResource(R.string.preference_search_favorites),
                    summary = stringResource(R.string.preference_search_favorites_summary),
                    icon = R.drawable.star_24px,
                    switchValue = favorites == true,
                    onSwitchChanged = {
                        viewModel.setFavorites(it)
                    },
                    onClick = {
                        backStack.add(FavoritesSettingsRoute)
                    }
                )
                PreferenceWithSwitch(
                    title = stringResource(R.string.preference_search_apps),
                    summary = stringResource(R.string.preference_search_apps_summary),
                    icon = R.drawable.apps_24px,
                    switchValue = allApps == true,
                    onSwitchChanged = {
                        viewModel.setAllApps(it)
                    },
                    onClick = {
                        backStack.add(AppSearchSettingsRoute)
                    }
                )
            }
        }
        item {
            PreferenceCategory {

                Preference(
                    title = stringResource(R.string.preference_search_files),
                    summary = stringResource(R.string.preference_search_files_summary),
                    icon = R.drawable.description_24px,
                    onClick = {
                        backStack.add(FileSearchSettingsRoute)
                    }
                )

                if (hasContactPlugins != false) {
                    Preference(
                        title = stringResource(R.string.preference_search_contacts),
                        summary = stringResource(R.string.preference_search_contacts_summary),
                        icon = R.drawable.person_24px,
                        onClick = {
                            backStack.add(ContactsSettingsRoute)
                        },
                    )
                } else {
                    GuardedPreference(
                        locked = hasContactsPermission == false,
                        onUnlock = {
                            viewModel.requestContactsPermission(context as AppCompatActivity)
                        },
                        description = stringResource(R.string.missing_permission_contact_search_settings),
                    ) {
                        PreferenceWithSwitch(
                            title = stringResource(R.string.preference_search_contacts),
                            summary = stringResource(R.string.preference_search_contacts_summary),
                            icon = R.drawable.person_24px,
                            switchValue = contacts == true && hasContactsPermission == true,
                            onSwitchChanged = {
                                viewModel.setContacts(it)
                            },
                            onClick = {
                                backStack.add(ContactsSettingsRoute)
                            },
                            enabled = hasContactsPermission == true
                        )
                    }
                }

                if (hasCalendarPlugins != false || isTasksAppInstalled != false) {
                    Preference(
                        title = stringResource(R.string.preference_search_calendar),
                        summary = stringResource(R.string.preference_search_calendar_summary),
                        icon = R.drawable.today_24px,
                        onClick = {
                            backStack.add(CalendarSearchSettingsRoute)
                        },
                    )
                } else {

                    GuardedPreference(
                        locked = hasCalendarPermission == false,
                        onUnlock = {
                            viewModel.requestCalendarPermission(context as AppCompatActivity)
                        },
                        description = stringResource(R.string.missing_permission_calendar_search_settings),
                    ) {
                        PreferenceWithSwitch(
                            title = stringResource(R.string.preference_search_calendar),
                            summary = stringResource(R.string.preference_search_calendar_summary),
                            switchValue = calendar == true,
                            onSwitchChanged = {
                                viewModel.setCalendarSearch(it)
                            },
                            icon = R.drawable.today_24px,
                            enabled = hasCalendarPermission == true,
                            onClick = {
                                backStack.add(CalendarProviderSettingsRoute(providerId = "local"))
                            }
                        )
                    }
                }

                SwitchPreference(
                    title = stringResource(R.string.preference_search_reminders),
                    summary = stringResource(R.string.preference_search_reminders_summary),
                    icon = R.drawable.task_alt_24px,
                    value = reminders == true,
                    onValueChanged = { viewModel.setRemindersSearch(it) }
                )

                GuardedPreference(
                    locked = hasAppShortcutsPermission == false,
                    onUnlock = {
                        viewModel.requestAppShortcutsPermission(context as AppCompatActivity)
                    },
                    description = stringResource(
                        R.string.missing_permission_appshortcuts_search_settings,
                        stringResource(R.string.app_name),
                    ),
                ) {
                    SwitchPreference(
                        title = stringResource(R.string.preference_search_appshortcuts),
                        summary = stringResource(R.string.preference_search_appshortcuts_summary),
                        icon = R.drawable.mobile_arrow_up_right_24px,
                        value = appShortcuts == true && hasAppShortcutsPermission == true,
                        onValueChanged = {
                            viewModel.setAppShortcuts(it)
                        },
                        enabled = hasAppShortcutsPermission == true
                    )
                }

                SwitchPreference(
                    title = stringResource(R.string.preference_search_calculator),
                    summary = stringResource(R.string.preference_search_calculator_summary),
                    icon = R.drawable.calculate_24px,
                    value = calculator == true,
                    onValueChanged = {
                        viewModel.setCalculator(it)
                    }
                )

                PreferenceWithSwitch(
                    title = stringResource(R.string.preference_search_unitconverter),
                    summary = stringResource(R.string.preference_search_unitconverter_summary),
                    icon = R.drawable.autorenew_24px,
                    switchValue = unitConverter == true,
                    onSwitchChanged = {
                        viewModel.setUnitConverter(it)
                    },
                    onClick = {
                        backStack.add(UnitConverterSettingsRoute)
                    }
                )

                PreferenceWithSwitch(
                    title = stringResource(R.string.preference_search_wikipedia),
                    summary = stringResource(R.string.preference_search_wikipedia_summary),
                    icon = R.drawable.wikipedia,
                    switchValue = wikipedia == true,
                    onSwitchChanged = {
                        viewModel.setWikipedia(it)
                    },
                    onClick = {
                        backStack.add(WikipediaSettingsRoute)
                    }
                )

                SwitchPreference(
                    title = stringResource(R.string.preference_search_websites),
                    summary = stringResource(R.string.preference_search_websites_summary),
                    icon = R.drawable.public_24px,
                    value = websites == true,
                    onValueChanged = {
                        viewModel.setWebsites(it)
                    }
                )
                GuardedPreference(
                    locked = hasLocationPermission == false,
                    onUnlock = {
                        viewModel.requestLocationPermission(context as AppCompatActivity)
                    },
                    description = stringResource(R.string.missing_permission_location_search),
                ) {
                    if (hasLocationPlugins != false) {
                        Preference(
                            title = stringResource(R.string.preference_search_locations),
                            summary = stringResource(R.string.preference_search_locations_summary),
                            icon = R.drawable.location_on_24px,
                            enabled = hasLocationPermission == true,
                            onClick = {
                                backStack.add(LocationsSettingsRoute)
                            }
                        )
                    } else {
                        PreferenceWithSwitch(
                            title = stringResource(R.string.preference_search_locations),
                            summary = stringResource(R.string.preference_search_locations_summary),
                            icon = R.drawable.location_on_24px,
                            onClick = {
                                backStack.add(LocationsSettingsRoute)
                            },
                            switchValue = places == true,
                            onSwitchChanged = {
                                viewModel.setPlacesSearch(it)
                            },
                            enabled = hasLocationPermission == true,
                        )
                    }
                }

                Preference(
                    title = stringResource(R.string.preference_screen_search_actions),
                    summary = stringResource(R.string.preference_search_search_actions_summary),
                    icon = R.drawable.arrow_outward_24px,
                    onClick = {
                        backStack.add(SearchActionsSettingsRoute)
                    }
                )
            }
        }
        item {
            PreferenceCategory {
                Preference(
                    title = stringResource(R.string.preference_hidden_items),
                    summary = stringResource(R.string.preference_hidden_items_summary),
                    icon = R.drawable.visibility_off_24px,
                    onClick = {
                        backStack.add(HiddenItemsSettingsRoute)
                    }
                )
                Preference(
                    title = stringResource(R.string.preference_screen_tags),
                    summary = stringResource(R.string.preference_screen_tags_summary),
                    icon = R.drawable.tag_24px,
                    onClick = {
                        backStack.add(TagsSettingsRoute)
                    }
                )
            }
        }
        item {
            PreferenceCategory(title = stringResource(R.string.preference_category_protection)) {
                val lockViewModel: SettingsLockVM = viewModel()
                val lockSettings by lockViewModel.lockSensitiveSettings.collectAsStateWithLifecycle()
                val lockMethod by lockViewModel.lockMethod.collectAsStateWithLifecycle()
                val useCustomLock by lockViewModel.useCustomLock.collectAsStateWithLifecycle()
                val customLockHashed by lockViewModel.customLockHashed.collectAsStateWithLifecycle()
                val activity = LocalContext.current as? FragmentActivity
                val promptTitle = stringResource(R.string.settings_lock_prompt_title)
                val scope = androidx.compose.runtime.rememberCoroutineScope()

                var onUnlockAction by remember { mutableStateOf<(() -> Unit)?>(null) }
                var showSetPinDialog by remember { mutableStateOf(false) }

                if (onUnlockAction != null) {
                    de.mm20.launcher2.ui.settings.protection.CustomLockDialog(
                        title = stringResource(R.string.custom_lock_dialog_title),
                        onConfirm = { pin ->
                            scope.launch {
                                if (lockViewModel.verifyCustomLock(pin)) {
                                    onUnlockAction?.invoke()
                                    onUnlockAction = null
                                }
                            }
                        },
                        onDismiss = { onUnlockAction = null }
                    )
                }

                if (showSetPinDialog) {
                    de.mm20.launcher2.ui.settings.protection.CustomLockDialog(
                        title = stringResource(R.string.custom_lock_dialog_title),
                        confirmTitle = stringResource(R.string.custom_lock_dialog_confirm_title),
                        onConfirm = { pin ->
                            lockViewModel.setCustomLock(pin)
                            lockViewModel.setUseCustomLock(true)
                            showSetPinDialog = false
                        },
                        onDismiss = { showSetPinDialog = false }
                    )
                }

                SwitchPreference(
                    title = stringResource(R.string.preference_lock_sensitive_settings),
                    summary = stringResource(R.string.preference_lock_sensitive_settings_summary),
                    icon = R.drawable.lock_24px,
                    value = lockSettings == true,
                    onValueChanged = { newValue ->
                        if (newValue) {
                            lockViewModel.setLockSensitiveSettings(true)
                        } else if (activity != null) {
                            if (useCustomLock == true) {
                                onUnlockAction = { lockViewModel.setLockSensitiveSettings(false) }
                            } else {
                                val method = lockMethod ?: SettingsLockMethod.DeviceCredential
                                authenticateSettings(activity, method, promptTitle) { ok ->
                                    if (ok) lockViewModel.setLockSensitiveSettings(false)
                                }
                            }
                        }
                    }
                )
                AnimatedVisibility(lockSettings == true) {
                    Column {
                        SwitchPreference(
                            title = stringResource(R.string.preference_use_custom_lock),
                            summary = stringResource(R.string.preference_use_custom_lock_summary),
                            iconPadding = true,
                            value = useCustomLock == true,
                            onValueChanged = { use ->
                                if (use) {
                                    showSetPinDialog = true
                                } else if (activity != null) {
                                    onUnlockAction = { lockViewModel.setUseCustomLock(false) }
                                }
                            }
                        )
                        if (useCustomLock != true) {
                            ListPreference(
                                title = stringResource(R.string.preference_settings_lock_method),
                                iconPadding = true,
                                items = listOf(
                                    ListPreferenceItem(
                                        stringResource(R.string.settings_lock_method_device_credential),
                                        SettingsLockMethod.DeviceCredential,
                                    ),
                                    ListPreferenceItem(
                                        stringResource(R.string.settings_lock_method_biometrics_only),
                                        SettingsLockMethod.BiometricsOnly,
                                    ),
                                ),
                                value = lockMethod ?: SettingsLockMethod.DeviceCredential,
                                onValueChanged = { newMethod ->
                                    val current = lockMethod ?: return@ListPreference
                                    if (newMethod == current) return@ListPreference
                                    if (activity != null) {
                                        authenticateSettings(activity, current, promptTitle) { ok ->
                                            if (ok) lockViewModel.setLockMethod(newMethod)
                                        }
                                    }
                                }
                            )
                        } else {
                            Preference(
                                title = stringResource(R.string.preference_set_custom_lock),
                                summary = if (customLockHashed != null) "PIN is set" else "No PIN set",
                                iconPadding = true,
                                onClick = { showSetPinDialog = true }
                            )
                        }
                    }
                }

                val lockLauncher by lockViewModel.lockLauncher.collectAsStateWithLifecycle()
                SwitchPreference(
                    title = stringResource(R.string.preference_lock_launcher),
                    summary = stringResource(
                        R.string.preference_lock_launcher_summary,
                        stringResource(R.string.app_name),
                    ),
                    icon = R.drawable.lock_24px,
                    value = lockLauncher == true,
                    onValueChanged = { newValue ->
                        if (newValue) {
                            lockViewModel.setLockLauncher(true)
                        } else if (activity != null) {
                            if (useCustomLock == true) {
                                onUnlockAction = { lockViewModel.setLockLauncher(false) }
                            } else {
                                val method = lockMethod ?: SettingsLockMethod.DeviceCredential
                                authenticateSettings(activity, method, promptTitle) { ok ->
                                    if (ok) lockViewModel.setLockLauncher(false)
                                }
                            }
                        }
                    }
                )
            }
        }
        item {
            PreferenceCategory {
                Preference(
                    title = stringResource(R.string.preference_default_filter),
                    summary = stringResource(R.string.preference_default_filter_summary),
                    icon = R.drawable.filter_alt_24px,
                    onClick = {
                        if (lockSettings == true && activity != null) {
                            if (useCustomLock == true) {
                                onUnlockAction = { showFilterEditor = true }
                            } else {
                                authenticateSettings(activity, lockMethod ?: SettingsLockMethod.DeviceCredential, promptTitle) { ok ->
                                    if (ok) showFilterEditor = true
                                }
                            }
                        } else {
                            showFilterEditor = true
                        }
                    },
                )
                SwitchPreference(
                    title = stringResource(R.string.preference_filter_bar),
                    iconPadding = true,
                    summary = stringResource(R.string.preference_filter_bar_summary),
                    value = filterBar == true,
                    onValueChanged = {
                        viewModel.setFilterBar(it)
                    }
                )
                AnimatedVisibility(filterBar == true) {
                    Preference(
                        title = stringResource(R.string.preference_customize_filter_bar),
                        iconPadding = true,
                        summary = stringResource(R.string.preference_customize_filter_bar_summary),
                        onClick = {
                            backStack.add(FilterBarSettingsRoute)
                        }
                    )
                }
            }
        }
        item {
            PreferenceCategory {
                SwitchPreference(
                    title = stringResource(R.string.preference_search_bar_auto_focus),
                    summary = stringResource(R.string.preference_search_bar_auto_focus_summary),
                    icon = R.drawable.keyboard_24px,
                    value = autoFocus == true,
                    onValueChanged = {
                        viewModel.setAutoFocus(it)
                    }
                )
                SwitchPreference(
                    title = stringResource(R.string.preference_search_bar_launch_on_enter),
                    iconPadding = true,
                    summary = stringResource(R.string.preference_search_bar_launch_on_enter_summary),
                    value = launchOnEnter == true,
                    onValueChanged = {
                        viewModel.setLaunchOnEnter(it)
                    }
                )
                SwitchPreference(
                    title = stringResource(R.string.preference_private_keyboard),
                    iconPadding = true,
                    summary = stringResource(R.string.preference_private_keyboard_summary),
                    value = privateKeyboard == true,
                    onValueChanged = {
                        viewModel.setPrivateKeyboard(it)
                    }
                )
            }
        }
        item {
            PreferenceCategory {
                SwitchPreference(
                    title = stringResource(R.string.preference_show_app_recommendations),
                    iconPadding = true,
                    summary = stringResource(R.string.preference_show_app_recommendations_summary),
                    value = showAppRecommendations == true,
                    onValueChanged = {
                        viewModel.setShowAppRecommendations(it)
                    }
                )
                Preference(
                    title = stringResource(R.string.recommendation_why_am_i_seeing_this),
                    iconPadding = true,
                    onClick = {
                        showRecommendationInfo = true
                    }
                )
            }
        }
        item {
            PreferenceCategory {
                ListPreference(
                    title = stringResource(R.string.preference_layout_search_results),
                    items = listOf(
                        stringResource(R.string.search_results_order_top_down) to false,
                        stringResource(R.string.search_results_order_bottom_up) to true,
                    ),
                    value = reverseSearchResults,
                    onValueChanged = {
                        if (it != null) viewModel.setReverseSearchResults(it)
                    },
                    icon = R.drawable.sort_24px
                )
            }
        }
    }

    DismissableBottomSheet(
        expanded = showFilterEditor,
        onDismissRequest = { showFilterEditor = false }) {
        val filters by viewModel.searchFilters.collectAsStateWithLifecycle()
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .navigationBarsPadding()
        ) {
            AnimatedVisibility(filters.allowNetwork) {
                SmallMessage(
                    modifier = Modifier.padding(bottom = 16.dp),
                    icon = R.drawable.warning_24px,
                    text = stringResource(R.string.filter_settings_network_warning)
                )
            }
            SearchFilters(
                filters = filters,
                onFiltersChange = {
                    viewModel.setSearchFilters(it)
                },
                settings = true,
            )
        }
    }

    RecommendationInfoSheet(
        expanded = showRecommendationInfo,
        onDismissRequest = { showRecommendationInfo = false },
    )
}