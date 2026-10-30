package de.mm20.launcher2.store.catalog

import androidx.annotation.DrawableRes
import de.mm20.launcher2.applock.SettingsDeepLinkContract
import de.mm20.launcher2.search.TelosAppKeywords

/**
 * One of the apps that are part of Telos itself. They are not installed from anywhere: "installing"
 * one shows its icon in the app grid and in search, "removing" it hides the icon again. The code
 * stays in Telos either way, so this costs nothing while an app is hidden.
 */
data class TelosApp(
    /** The key of the virtual app (see the VirtualApp classes), also what is stored when it is hidden */
    val key: String,
    val name: String,
    val description: String,
    val features: List<String>,
    @DrawableRes val iconRes: Int,
    /** Deep link route that opens the app, see [SettingsDeepLinkContract] */
    val route: String,
    /** Tab to open in the phone app (recents, messages), null for the other apps */
    val commsTab: String? = null,
    /** The Store itself cannot be removed, otherwise there would be no way back */
    val removable: Boolean = true,
) {
    fun localizedName(resources: android.content.res.Resources): String =
        de.mm20.launcher2.search.TelosAppLabels.forKey(resources, key) ?: name

    /** Extra search words (synonyms, Greek names) for the Store search */
    val keywords: List<String> get() = TelosAppKeywords.forKey(key)
}

object TelosApps {

    val all: List<TelosApp> = listOf(
        TelosApp(
            key = "telos_clock_app://clock",
            name = "Clock",
            description = "World clock with capital timezones, alarms with stream fallback, countdown timer, stopwatch and bedside night clock.",
            features = listOf("World clock", "Alarms with stream fallback", "Countdown timer", "Stopwatch", "Night clock"),
            iconRes = de.mm20.launcher2.base.R.drawable.schedule_24px,
            route = "settings/clock",
        ),
        TelosApp(
            key = "telos_akou_app://akou",
            name = "Akou",
            description = "Music recognition using a real microphone sample and your own AudD API token, with local history and YouTube Music search.",
            features = listOf("AudD recognition (own API token required)", "10-second microphone sample", "Local recognition history", "YouTube Music search"),
            iconRes = de.mm20.launcher2.base.R.drawable.ic_app_youtube_music_full,
            route = "settings/akou",
        ),
        TelosApp(
            key = "telos_magnifier_app://magnifier",
            name = "Magnifier",
            description = "Camera magnifier with high contrast readability filters and freeze frame.",
            features = listOf("Camera2 zoom", "Inverted, B&W, Yellow/Black, Blue/Yellow filters", "Freeze frame"),
            iconRes = de.mm20.launcher2.base.R.drawable.search_24px,
            route = "settings/magnifier",
        ),
        TelosApp(
            key = "telos_flashlight_app://flashlight",
            name = "Flashlight",
            description = "Flashlight app with camera LED and screen light, Morse translator and emergency siren.",
            features = listOf("Camera LED & Screen light", "Hardware dimming", "Morse code generator", "Police siren alarm"),
            iconRes = de.mm20.launcher2.base.R.drawable.light_mode_24px,
            route = "settings/flashlight",
        ),
        TelosApp(
            key = "telos_trash_app://trash",
            name = "Trash",
            description = "Trash bin for deleted items with security unlock.",
            features = listOf("App Lock security", "Restore and permanent deletion"),
            iconRes = de.mm20.launcher2.base.R.drawable.delete_24px,
            route = SettingsDeepLinkContract.ROUTE_TRASH,
        ),
        TelosApp(
            key = "telos_store_app://store",
            name = "Store",
            description = "Installs and updates apps from GitHub, GitLab, Codeberg, F-Droid, IzzyOnDroid, SourceForge and web pages, checks for updates in the background and manages the Telos apps.",
            features = listOf("Update notifications", "Silent updates with Shizuku or root", "Import and export (Obtainium format)"),
            iconRes = de.mm20.launcher2.base.R.drawable.ic_app_store_full,
            route = SettingsDeepLinkContract.ROUTE_STORE,
            removable = false,
        ),
        TelosApp(
            key = "telos_phone_app://phone",
            name = "Phone",
            description = "A complete phone app: dialer, recents, contacts and favorites, call screens and call settings.",
            features = listOf(
                "T9 search (Latin, Greek, Cyrillic)", "Dual SIM routing", "Call recording and call screening",
                "Hidden contacts and app lock", "SIP / VoIP account", "Caller names from a FRITZ!Box",
            ),
            iconRes = de.mm20.launcher2.base.R.drawable.ic_app_phone_full,
            route = SettingsDeepLinkContract.ROUTE_COMMS,
            commsTab = "recents",
        ),
        TelosApp(
            key = "telos_messages_app://messages",
            name = "Messages",
            description = "Your text and picture messages (MMS): conversations, replies, quick replies and scheduled messages. Can be the default SMS app. Conversations with hidden contacts are only shown while the hidden contacts are unlocked.",
            features = listOf("SMS and MMS", "Default SMS app", "Quick replies", "Scheduled messages", "Hidden contacts stay out of the list"),
            iconRes = de.mm20.launcher2.base.R.drawable.ic_app_messages_full,
            route = SettingsDeepLinkContract.ROUTE_COMMS,
            commsTab = "messages",
        ),
        TelosApp(
            key = "telos_media_app://media",
            name = "Media",
            description = "Music, radio and video in one app: three spaces that you switch with a pill at the top, and one mini player for music and radio. Telos Music, Telos Radio and Telos Video also open here.",
            features = listOf("Music, Radio and Video spaces", "Remembers the last space", "Shared mini player for music and radio", "Needs no extra permission of its own"),
            iconRes = de.mm20.launcher2.base.R.drawable.ic_app_media_full,
            route = SettingsDeepLinkContract.ROUTE_MEDIA,
        ),
        TelosApp(
            key = "telos_youtube_app://youtube",
            name = "YouTube",
            description = "YouTube search and native playback inside Telos Media.",
            features = listOf("Background playback", "Shared audio and video player", "Video links"),
            iconRes = de.mm20.launcher2.base.R.drawable.ic_app_youtube_full,
            route = SettingsDeepLinkContract.ROUTE_YOUTUBE,
        ),
        TelosApp(
            key = "telos_youtube_music_app://youtube_music",
            name = "YouTube Music",
            description = "YouTube search and native playback inside Telos Media.",
            features = listOf("Background playback", "Shared audio and video player", "Video links"),
            iconRes = de.mm20.launcher2.base.R.drawable.ic_app_youtube_music_full,
            route = SettingsDeepLinkContract.ROUTE_YOUTUBE_MUSIC,
        ),
        TelosApp(
            key = "telos_radio_app://radio",
            name = "Radio",
            description = "Internet radio with a station search, your own collection and a sleep timer.",
            features = listOf("Radio-Browser search", "M3U / PLS import and export", "Track history", "Next / previous station"),
            iconRes = de.mm20.launcher2.base.R.drawable.ic_app_radio_full,
            route = SettingsDeepLinkContract.ROUTE_RADIO,
        ),
        TelosApp(
            key = "telos_music_app://music",
            name = "Music",
            description = "A player for the music on your phone, with lyrics and a tag editor.",
            features = listOf("Songs, albums, artists", "Synchronized lyrics", "Tag editor", "Scrobbling to Last.fm, Libre.fm and ListenBrainz", "Sleep timer"),
            iconRes = de.mm20.launcher2.base.R.drawable.ic_app_music_full,
            route = SettingsDeepLinkContract.ROUTE_MUSIC,
        ),
        TelosApp(
            key = "telos_video_app://video",
            name = "Video",
            description = "A video player and library for movies and series, which also plays web streams and torrents.",
            features = listOf(
                "Movies and series with posters", "Web streams and torrents (magnet links)", "Subtitle search and download",
                "Gestures, speed, tracks, FFmpeg decoders", "Trakt.tv",
            ),
            iconRes = de.mm20.launcher2.base.R.drawable.ic_app_video_full,
            route = SettingsDeepLinkContract.ROUTE_VIDEO,
        ),
        TelosApp(
            key = "telos_viewer_app://viewer",
            name = "Viewer",
            description = "Your pictures and your documents in one app, as two tabs: a gallery and photo editor with a privacy focus, and a library and viewer for PDF, Word, Excel, PowerPoint, OpenDocument, text and e-books.",
            features = listOf(
                "Photos: albums, date timeline, EXIF viewer, editor and remover, crop, rotate, filters",
                "Documents: categories, recent files and search", "PDF, Word, Excel and PowerPoint viewing and editing", "20 PDF tools",
            ),
            iconRes = de.mm20.launcher2.base.R.drawable.ic_app_viewer_full,
            route = SettingsDeepLinkContract.ROUTE_VIEWER,
        ),
        TelosApp(
            key = "telos_files_app://files",
            name = "Files",
            description = "A file manager: browse and manage the files on your phone, with an optional root explorer for system files.",
            features = listOf(
                "Storage, SD cards and USB", "Copy, move, rename, delete, zip", "Grid and list, sorting, search", "Favorites and properties with checksums",
                "Root explorer with safety warnings",
            ),
            iconRes = de.mm20.launcher2.base.R.drawable.ic_app_files_full,
            route = SettingsDeepLinkContract.ROUTE_FILES,
        ),
        TelosApp(
            key = "telos_calculator_app://calculator",
            name = "Calculator",
            description = "A standard and scientific calculator with VAT, unit and currency conversion and a dated history.",
            features = listOf(
                "Standard and scientific mode", "VAT: add or remove, any rate", "Length, mass, area, volume, speed, temperature, currency",
                "History with date and time", "Quick Settings tile",
            ),
            iconRes = de.mm20.launcher2.base.R.drawable.ic_app_calculator_full,
            route = SettingsDeepLinkContract.ROUTE_CALCULATOR,
        ),
        TelosApp(
            key = "telos_notes_app://notes",
            name = "Notes",
            description = "Notes that stay on the phone and can sync to a Markdown folder or Nextcloud Notes, with import from Google Keep, Evernote and Markdown.",
            features = listOf("Colours, pins, labels, archive and trash", "Sync with an Obsidian or Logseq folder, Syncthing or Nextcloud Notes", "Import Google Keep, Evernote, Markdown and text", "Part of the Telos backup"),
            iconRes = de.mm20.launcher2.base.R.drawable.ic_app_notes_full,
            route = SettingsDeepLinkContract.ROUTE_NOTES,
        ),
        TelosApp(
            key = "telos_ermis_app://ermis",
            name = "Ermis",
            description = "Email client with a Gmail-style inbox. IMAP/SMTP accounts, conversation list, compose, and per-message notes that sync to Gmail via an Ermis-Notes folder.",
            features = listOf(
                "Gmail-style inbox with blur theme",
                "IMAP and SMTP (app password for Gmail)",
                "Conversation threading",
                "Notes on Gmail messages, stored locally and in IMAP folder Ermis-Notes",
            ),
            iconRes = de.mm20.launcher2.base.R.drawable.ic_app_messages_full,
            route = SettingsDeepLinkContract.ROUTE_ERMIS,
        ),
        TelosApp(
            key = "telos_calendar_app://calendar",
            name = "Calendar",
            description = "Month and agenda calendar on the calendars of the phone, with a local calendar and sync through your Google, CalDAV or Exchange account.",
            features = listOf("Month view and agenda", "Local calendar without an account", "Google, CalDAV (DAVx5) and Exchange calendars", "Repeating events and reminders", "Import and export .ics", "Part of the Telos backup"),
            iconRes = de.mm20.launcher2.base.R.drawable.ic_app_calendar_full,
            route = SettingsDeepLinkContract.ROUTE_CALENDAR,
        ),
        TelosApp(
            key = "telos_downloads_app://downloads",
            name = "Downloads",
            description = "A download manager for links: several connections per file, resume after a restart or lost network, a queue with retry, and the folder of your choice.",
            features = listOf(
                "HTTP and HTTPS with up to 16 connections", "Resume, queue, retry, Wi-Fi only and battery rules", "Speed limit, headers, cookies, proxy, checksum",
                "Folder of your choice or Downloads/Telos", "Share a link to Telos Downloads",
            ),
            iconRes = de.mm20.launcher2.base.R.drawable.ic_app_downloads_full,
            route = SettingsDeepLinkContract.ROUTE_DOWNLOADS,
        ),
        TelosApp(
            key = "telos_sync_app://sync",
            name = "Sync",
            description = "Decentralized file synchronization across devices in real-time, inspired by Syncthing. Includes battery-saving SleepSync for night-time transfers.",
            features = listOf("Decentralized peer-to-peer sync", "SleepSync night-time optimization", "Wi-Fi & Charging constraints", "Do Not Disturb (DND) integration"),
            iconRes = de.mm20.launcher2.base.R.drawable.ic_app_sync_full,
            route = SettingsDeepLinkContract.ROUTE_SYNC,
        ),
        TelosApp(
            key = "telos_backup_app://backup",
            name = "Backup",
            description = "Encrypted and password-protected backup of Telos settings, Telos Apps, and system data (SMS, Call Logs). Supports app data backups via Shizuku or Root, similar to Swift Backup.",
            features = listOf("Zero-Trust encrypted backups", "App APK & Data backup (Root/Shizuku)", "System data sync", "Batch operations"),
            iconRes = de.mm20.launcher2.base.R.drawable.ic_app_backup_full,
            route = SettingsDeepLinkContract.ROUTE_BACKUP,
        ),
        TelosApp(
            key = "telos_network_app://network",
            name = "Network",
            description = "A VPN based firewall and DNS filter, built on the engine of RethinkDNS. It stays off until you turn it on, and if it stops the internet works as before. Early version, not yet tested on many devices.",
            features = listOf(
                "DNS over HTTPS, TLS, DNSCrypt and your own servers", "Firewall per app and connection type: Wi-Fi, mobile, roaming, LAN",
                "Universal rules, IP and domain rules", "Blocklists with exceptions", "WireGuard tunnels, also per app",
                "Connection and DNS logs", "No account, no subscription",
            ),
            iconRes = de.mm20.launcher2.base.R.drawable.ic_app_network_full,
            route = SettingsDeepLinkContract.ROUTE_NETWORK,
        ),
        TelosApp(
            key = "telos_system_app://system",
            name = "System",
            description = "Live OS status: CPU, RAM, IPs, running processes, and saved Wi-Fi passwords (root or Shizuku) with QR share.",
            features = listOf("CPU and RAM meters", "Local IPs", "Saved Wi-Fi manager", "Password reveal with lock, root or Shizuku", "Notes, import/export, connect and forget", "QR share"),
            iconRes = de.mm20.launcher2.base.R.drawable.ic_app_network_full,
            route = SettingsDeepLinkContract.ROUTE_SYSTEM,
        ),
        TelosApp(
            key = "telos_voice_recorder_app://voice_recorder",
            name = "Voice Recorder",
            description = "A voice recorder with a list of recordings, search, pause and a recording service that keeps going with the screen off.",
            features = listOf("AAC and Opus", "Standard and voice mode, three qualities", "Pause and resume", "Rename, share, delete", "Call recordings of Telos Phone"),
            iconRes = de.mm20.launcher2.base.R.drawable.ic_app_voice_recorder_full,
            route = SettingsDeepLinkContract.ROUTE_VOICE_RECORDER,
        ),
        TelosApp(
            key = "telos_screen_recorder_app://screen_recorder",
            name = "Screen Recorder",
            description = "Records the screen to a video, with the microphone if you want, and keeps the recordings in Movies/Telos.",
            features = listOf("Resolution up to the screen size, 30 or 60 fps", "Microphone", "Pause and resume from the notification", "Countdown, show touches", "Stops when the screen turns off"),
            iconRes = de.mm20.launcher2.base.R.drawable.ic_app_screen_recorder_full,
            route = SettingsDeepLinkContract.ROUTE_SCREEN_RECORDER,
        ),
        TelosApp(
            key = "telos_screenshot_app://screenshot",
            name = "Screenshot",
            description = "Takes full, partial and scrolling screenshots and edits them: crop, hide with pixelate or blur, and draw.",
            features = listOf("Full, partial (rectangle, oval, free shape) and scrolling screenshots", "Pixelate and blur to hide private parts", "Notification with share, edit and delete", "Delay timer"),
            iconRes = de.mm20.launcher2.base.R.drawable.ic_sidebar_screenshot,
            route = SettingsDeepLinkContract.ROUTE_SCREENSHOT,
        ),
    )
}
