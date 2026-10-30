package com.telos.desktop.apps

import com.telos.desktop.icons.CandyIcon

/**
 * Desktop-hosted Telos virtual apps. Names match the Android launcher domains
 * without depending on `:app:ui` or Android resources.
 */
data class TelosVirtualApp(
    val domain: String,
    val label: String,
    val category: TelosAppCategory,
    val icon: CandyIcon,
    val windowTitle: String,
)

object TelosVirtualApps {
    val all: List<TelosVirtualApp> = listOf(
        TelosVirtualApp("telos_files_app", "Files", TelosAppCategory.Utility, CandyIcon.Folder, "Files"),
        TelosVirtualApp("telos_photos_app", "Photos", TelosAppCategory.Graphics, CandyIcon.Folder, "Photos"),
        TelosVirtualApp("telos_music_app", "Music", TelosAppCategory.AudioVideo, CandyIcon.Volume, "Media Player"),
        TelosVirtualApp("telos_radio_app", "Radio", TelosAppCategory.AudioVideo, CandyIcon.Volume, "Media Player"),
        TelosVirtualApp("telos_video_app", "Video", TelosAppCategory.AudioVideo, CandyIcon.Monitor, "Media Player"),
        TelosVirtualApp("telos_media_app", "Media", TelosAppCategory.AudioVideo, CandyIcon.Volume, "Media Player"),
        TelosVirtualApp("telos_notes_app", "Notes", TelosAppCategory.Office, CandyIcon.Settings, "Notes"),
        TelosVirtualApp("telos_calendar_app", "Calendar", TelosAppCategory.Office, CandyIcon.Settings, "Calendar"),
        TelosVirtualApp("telos_calculator_app", "Calculator", TelosAppCategory.Utility, CandyIcon.Settings, "Calculator"),
        TelosVirtualApp("telos_downloads_app", "Downloads", TelosAppCategory.Network, CandyIcon.Folder, "Downloads"),
        TelosVirtualApp("telos_network_app", "Network", TelosAppCategory.Network, CandyIcon.Network, "Network"),
        TelosVirtualApp("telos_store_app", "Store", TelosAppCategory.Utility, CandyIcon.AppGrid, "Store"),
        TelosVirtualApp("telos_system_app", "System Optimizer", TelosAppCategory.System, CandyIcon.Monitor, "System Optimizer"),
        TelosVirtualApp("telos_clock_app", "Clock", TelosAppCategory.Utility, CandyIcon.Settings, "Clock"),
        TelosVirtualApp("telos_terminal", "Terminal", TelosAppCategory.System, CandyIcon.Terminal, "Quake"),
        TelosVirtualApp("telos_settings", "Settings", TelosAppCategory.Settings, CandyIcon.Settings, "Settings"),
        TelosVirtualApp("telos_monitor", "System Monitor", TelosAppCategory.System, CandyIcon.Monitor, "System Monitor"),
        TelosVirtualApp("telos_trash_app", "Trash", TelosAppCategory.Utility, CandyIcon.Folder, "Trash"),
    )

    val pinnedDefaults = listOf("telos_files_app", "telos_music_app", "telos_system_app", "telos_settings")
}
