package de.mm20.launcher2.search

import androidx.compose.ui.graphics.Color

/**
 * Cyber-Tech Neon Accent Colors for each Telos Virtual App domain.
 */
object TelosAppAccents {

    fun forDomain(domain: String): Color = when (domain) {
        "telos_keepass_app" -> Color(0xFF10B981)       // Cyber Emerald
        "telos_weather_app" -> Color(0xFF00F2FE)       // Atmospheric Cyan
        "telos_media_app",
        "telos_music_app",
        "telos_radio_app",
        "telos_youtube_app",
        "telos_youtube_music_app",
        "telos_video_app",
        "telos_tv_app",
        "telos_stremio_app" -> Color(0xFFA855F7)      // Electric Purple
        "telos_system_app",
        "telos_network_app",
        "telos_trash_app" -> Color(0xFFF59E0B)        // Cyber Amber
        "telos_keyboard_app" -> Color(0xFF3F51B5)      // Deep Indigo
        "telos_camera_app",
        "telos_screen_recorder_app",
        "telos_screenshot_app" -> Color(0xFFFF007F)   // Neon Pink
        "telos_notes_app",
        "telos_ermis_app" -> Color(0xFF06B6D4)         // Cyber Cyan
        "telos_calendar_app" -> Color(0xFF3B82F6)      // Electric Blue
        "telos_calculator_app" -> Color(0xFF8B5CF6)    // Violet
        "telos_files_app",
        "telos_downloads_app",
        "telos_viewer_app" -> Color(0xFF10B981)        // Teal
        "telos_store_app" -> Color(0xFFEC4899)         // Neon Rose
        "telos_sync_app",
        "telos_backup_app" -> Color(0xFF6366F1)        // Indigo
        else -> Color(0xFF00F2FE)                      // Default Cyan Accent
    }
}
