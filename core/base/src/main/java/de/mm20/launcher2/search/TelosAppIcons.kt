package de.mm20.launcher2.search

import de.mm20.launcher2.base.R

/** Explicit resource references survive release shrinking, unlike constructed drawable names. */
object TelosAppIcons {
    fun forDomain(domain: String): Int? = when (domain) {
        "telos_camera_app" -> R.drawable.ic_app_camera_full
        "telos_phone_app" -> R.drawable.ic_app_phone_full
        "telos_messages_app" -> R.drawable.ic_app_messages_full
        "telos_radio_app" -> R.drawable.ic_app_radio_full
        "telos_music_app" -> R.drawable.ic_app_music_full
        "telos_youtube_app" -> R.drawable.ic_app_youtube_full
        "telos_youtube_music_app" -> R.drawable.ic_app_youtube_music_full
        "telos_video_app" -> R.drawable.ic_app_video_full
        "telos_photos_app" -> R.drawable.ic_app_photos_full
        "telos_viewer_app" -> R.drawable.ic_app_viewer_full
        "telos_files_app" -> R.drawable.ic_app_files_full
        "telos_calculator_app" -> R.drawable.ic_app_calculator_full
        "telos_voice_recorder_app" -> R.drawable.ic_app_voice_recorder_full
        "telos_screen_recorder_app" -> R.drawable.ic_app_screen_recorder_full
        "telos_screenshot_app" -> R.drawable.ic_app_screenshot_full
        "telos_notes_app" -> R.drawable.ic_app_notes_full
        "telos_ermis_app" -> R.drawable.ic_app_ermis_full
        "telos_calendar_app" -> R.drawable.ic_app_calendar_full
        "telos_downloads_app" -> R.drawable.ic_app_downloads_full
        "telos_network_app" -> R.drawable.ic_app_network_full
        "telos_media_app" -> R.drawable.ic_app_media_full
        "telos_store_app" -> R.drawable.ic_app_store_full
        "telos_system_app" -> R.drawable.ic_app_system_full
        "telos_trash_app" -> R.drawable.ic_app_trash_full
        "telos_clock_app" -> R.drawable.ic_app_clock_full
        "telos_akou_app" -> R.drawable.ic_app_akou_full
        "telos_sync_app" -> R.drawable.ic_app_sync_full
        "telos_backup_app" -> R.drawable.ic_app_backup_full
        "telos_magnifier_app" -> R.drawable.ic_app_magnifier_full
        "telos_flashlight_app" -> R.drawable.ic_app_flashlight_full
        "telos_weather_app" -> R.drawable.ic_app_weather_full
        "telos_tv_app" -> R.drawable.ic_app_tv_full
        else -> null
    }
}
