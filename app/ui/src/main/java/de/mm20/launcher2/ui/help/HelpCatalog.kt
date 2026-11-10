package de.mm20.launcher2.ui.help

import de.mm20.launcher2.applock.SettingsDeepLinkContract
import de.mm20.launcher2.base.R as BaseR
import de.mm20.launcher2.i18n.R as I18nR

internal const val HELP_TOPIC_LAUNCHER = "launcher"
internal const val HELP_TOPIC_SETTINGS = "settings"

internal data class HelpStep(
    val titleRes: Int,
    val bodyRes: Int,
    val tryRoute: String? = null,
)

internal data class HelpAppLesson(
    val key: String,
    val iconRes: Int,
    val nameRes: Int,
    val descRes: Int,
    val bullets: List<Int>,
    val caveatRes: Int,
    val route: String?,
    val commsTab: String? = null,
)

internal object HelpCatalog {
    val launcherSteps = listOf(
        HelpStep(I18nR.string.telos_help_l_home_title, I18nR.string.telos_help_l_home_body),
        HelpStep(I18nR.string.telos_help_l_search_title, I18nR.string.telos_help_l_search_body),
        HelpStep(I18nR.string.telos_help_l_drawer_title, I18nR.string.telos_help_l_drawer_body),
        HelpStep(I18nR.string.telos_help_l_dock_title, I18nR.string.telos_help_l_dock_body),
        HelpStep(I18nR.string.telos_help_l_widgets_title, I18nR.string.telos_help_l_widgets_body),
        HelpStep(
            I18nR.string.telos_help_l_gestures_title,
            I18nR.string.telos_help_l_gestures_body,
            "settings/gestures",
        ),
        HelpStep(I18nR.string.telos_help_l_lock_title, I18nR.string.telos_help_l_lock_body),
    )

    val settingsSteps = listOf(
        HelpStep(
            I18nR.string.telos_help_s_theme_title,
            I18nR.string.telos_help_s_theme_body,
            "settings/appearance",
        ),
        HelpStep(
            I18nR.string.telos_help_s_search_title,
            I18nR.string.telos_help_s_search_body,
            "settings/search",
        ),
        HelpStep(
            I18nR.string.telos_help_s_gestures_title,
            I18nR.string.telos_help_s_gestures_body,
            "settings/gestures",
        ),
        HelpStep(
            I18nR.string.telos_help_s_widgets_title,
            I18nR.string.telos_help_s_widgets_body,
            "settings/homescreen",
        ),
        HelpStep(
            I18nR.string.telos_help_s_system_title,
            I18nR.string.telos_help_s_system_body,
            SettingsDeepLinkContract.ROUTE_SYSTEM,
        ),
    )

    val apps = listOf(
        lesson("telos_phone_app", BaseR.drawable.ic_app_phone_full, I18nR.string.virtual_app_phone, I18nR.string.telos_phone_help_desc, I18nR.string.telos_help_phone_1, I18nR.string.telos_help_phone_2, I18nR.string.telos_help_phone_3, I18nR.string.telos_help_phone_not, SettingsDeepLinkContract.ROUTE_COMMS),
        lesson("telos_messages_app", BaseR.drawable.ic_app_messages_full, I18nR.string.hc_messages, I18nR.string.telos_messages_help_desc, I18nR.string.telos_help_messages_1, I18nR.string.telos_help_messages_2, I18nR.string.telos_help_messages_3, I18nR.string.telos_help_messages_not, SettingsDeepLinkContract.ROUTE_COMMS, "messages"),
        lesson("telos_files_app", BaseR.drawable.ic_app_files_full, I18nR.string.pdft_files, I18nR.string.telos_files_help_desc, I18nR.string.telos_help_files_1, I18nR.string.telos_help_files_2, I18nR.string.telos_help_files_3, I18nR.string.telos_help_files_not, SettingsDeepLinkContract.ROUTE_FILES),
        lesson("telos_media_app", BaseR.drawable.ic_app_media_full, I18nR.string.dl_m_tab, I18nR.string.telos_media_help_desc, I18nR.string.telos_help_media_1, I18nR.string.telos_help_media_2, I18nR.string.telos_help_media_3, I18nR.string.telos_help_media_not, SettingsDeepLinkContract.ROUTE_MEDIA),
        lesson("telos_store_app", BaseR.drawable.ic_app_store_full, I18nR.string.hf_store_title, I18nR.string.telos_store_help_desc, I18nR.string.telos_help_store_1, I18nR.string.telos_help_store_2, I18nR.string.telos_help_store_3, I18nR.string.telos_help_store_not, SettingsDeepLinkContract.ROUTE_STORE),
        lesson("telos_system_app", BaseR.drawable.ic_app_system_full, I18nR.string.telos_system_label, I18nR.string.telos_system_help_desc, I18nR.string.telos_help_system_1, I18nR.string.telos_help_system_2, I18nR.string.telos_help_system_3, I18nR.string.telos_help_system_not, SettingsDeepLinkContract.ROUTE_SYSTEM),
        lesson("telos_network_app", BaseR.drawable.ic_app_network_full, I18nR.string.hc_network, I18nR.string.telos_network_help_desc, I18nR.string.telos_help_network_1, I18nR.string.telos_help_network_2, I18nR.string.telos_help_network_3, I18nR.string.telos_help_network_not, SettingsDeepLinkContract.ROUTE_NETWORK),
        lesson("telos_notes_app", BaseR.drawable.ic_app_notes_full, I18nR.string.notes_title, I18nR.string.telos_notes_help_desc, I18nR.string.telos_help_notes_1, I18nR.string.telos_help_notes_2, I18nR.string.telos_help_notes_3, I18nR.string.telos_help_notes_not, SettingsDeepLinkContract.ROUTE_NOTES),
        lesson("telos_calculator_app", BaseR.drawable.ic_app_calculator_full, I18nR.string.calculator_title, I18nR.string.telos_calculator_help_desc, I18nR.string.telos_help_calculator_1, I18nR.string.telos_help_calculator_2, I18nR.string.telos_help_calculator_3, I18nR.string.telos_help_calculator_not, SettingsDeepLinkContract.ROUTE_CALCULATOR),
        lesson("telos_backup_app", BaseR.drawable.ic_app_backup_full, I18nR.string.preference_backup, I18nR.string.telos_backup_help_desc, I18nR.string.telos_help_backup_1, I18nR.string.telos_help_backup_2, I18nR.string.telos_help_backup_3, I18nR.string.telos_help_backup_not, SettingsDeepLinkContract.ROUTE_BACKUP),
        lesson("telos_sync_app", BaseR.drawable.ic_app_sync_full, I18nR.string.notes_sync_settings, I18nR.string.telos_sync_help_desc, I18nR.string.telos_help_sync_1, I18nR.string.telos_help_sync_2, I18nR.string.telos_help_sync_3, I18nR.string.telos_help_sync_not, SettingsDeepLinkContract.ROUTE_SYNC),
        lesson("telos_ermis_app", BaseR.drawable.ic_app_ermis_full, I18nR.string.ermis_title, I18nR.string.telos_ermis_help_desc, I18nR.string.telos_help_ermis_1, I18nR.string.telos_help_ermis_2, I18nR.string.telos_help_ermis_3, I18nR.string.telos_help_ermis_not, SettingsDeepLinkContract.ROUTE_ERMIS),
        lesson("telos_camera_app", BaseR.drawable.ic_app_camera_full, I18nR.string.camera_title, I18nR.string.telos_camera_help_desc, I18nR.string.telos_help_camera_1, I18nR.string.telos_help_camera_2, I18nR.string.telos_help_camera_3, I18nR.string.telos_help_camera_not, SettingsDeepLinkContract.ROUTE_CAMERA),
        lesson("telos_weather_app", BaseR.drawable.ic_app_weather_full, I18nR.string.widget_name_weather, I18nR.string.telos_weather_help_desc, I18nR.string.telos_help_weather_1, I18nR.string.telos_help_weather_2, I18nR.string.telos_help_weather_3, I18nR.string.telos_help_weather_not, SettingsDeepLinkContract.ROUTE_WEATHER),
        lesson("telos_trash_app", BaseR.drawable.ic_app_trash_full, I18nR.string.telos_trash_title, I18nR.string.telos_trash_help_desc, I18nR.string.telos_help_trash_1, I18nR.string.telos_help_trash_2, I18nR.string.telos_help_trash_3, I18nR.string.telos_help_trash_not, SettingsDeepLinkContract.ROUTE_TRASH),
        lesson("telos_clock_app", BaseR.drawable.ic_app_clock_full, I18nR.string.preference_screen_clockwidget, I18nR.string.telos_clock_help_desc, I18nR.string.telos_help_clock_1, I18nR.string.telos_help_clock_2, I18nR.string.telos_help_clock_3, I18nR.string.telos_help_clock_not, "settings/clock"),
        lesson("telos_akou_app", BaseR.drawable.ic_app_akou_full, I18nR.string.telos_akou_app_label, I18nR.string.telos_akou_help_desc, I18nR.string.telos_help_akou_1, I18nR.string.telos_help_akou_2, I18nR.string.telos_help_akou_3, I18nR.string.telos_help_akou_not, "settings/akou"),
    )

    fun stepsFor(topicId: String): List<HelpStep> = when (topicId) {
        HELP_TOPIC_LAUNCHER -> launcherSteps
        HELP_TOPIC_SETTINGS -> settingsSteps
        else -> emptyList()
    }

    private fun lesson(
        key: String,
        icon: Int,
        name: Int,
        desc: Int,
        b1: Int,
        b2: Int,
        b3: Int,
        caveat: Int,
        route: String?,
        commsTab: String? = null,
    ) = HelpAppLesson(key, icon, name, desc, listOf(b1, b2, b3), caveat, route, commsTab)
}
