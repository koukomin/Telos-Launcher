package de.mm20.launcher2.search

import android.content.Context
import android.content.res.Resources
import de.mm20.launcher2.i18n.R

/** Display labels are localized; domains and persisted keys never change with language. */
object TelosAppLabels {
    fun resourceForDomain(domain: String): Int? = when (domain) {
        "telos_clock_app" -> R.string.preference_screen_clockwidget
        "telos_akou_app" -> R.string.telos_akou_app_label
        "telos_magnifier_app" -> R.string.telos_magnifier_app_label
        "telos_flashlight_app" -> R.string.telos_flashlight_app_label
        "telos_ironewolf_app" -> R.string.ironewolf_name
        "telos_phone_app" -> R.string.virtual_app_phone
        "telos_messages_app" -> R.string.hc_messages
        "telos_radio_app" -> R.string.hc_radio
        "telos_music_app" -> R.string.au_music_title
        "telos_youtube_app" -> R.string.telos_youtube_label
        "telos_youtube_music_app" -> R.string.telos_youtube_music_label
        "telos_video_app" -> R.string.au9_mediahub_space_video
        "telos_photos_app" -> R.string.hc_photos
        "telos_viewer_app" -> R.string.virtual_app_viewer
        "telos_files_app" -> R.string.pdft_files
        "telos_calculator_app" -> R.string.calculator_title
        "telos_voice_recorder_app" -> R.string.voice_title
        "telos_camera_app" -> R.string.camera_title
        "telos_screen_recorder_app" -> R.string.screenrec_title
        "telos_screenshot_app" -> R.string.screenshot_title
        "telos_notes_app" -> R.string.notes_title
        "telos_ermis_app" -> R.string.ermis_title
        "telos_calendar_app" -> R.string.backup_group_calendar
        "telos_downloads_app" -> R.string.dl_title
        "telos_media_app" -> R.string.dl_m_tab
        "telos_network_app" -> R.string.hc_network
        "telos_store_app" -> R.string.hf_store_title
        "telos_system_app" -> R.string.telos_system_label
        "telos_weather_app" -> R.string.widget_name_weather
        "telos_trash_app" -> R.string.telos_trash_title
        else -> null
    }

    fun forDomain(context: Context, domain: String): String =
        resourceForDomain(domain)?.let { context.getString(it) } ?: domain

    fun forKey(resources: Resources, key: String): String? =
        resourceForDomain(key.substringBefore("://"))?.let { resources.getString(it) }
}
