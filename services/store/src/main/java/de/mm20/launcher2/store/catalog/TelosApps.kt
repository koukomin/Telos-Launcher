package de.mm20.launcher2.store.catalog

import androidx.annotation.DrawableRes
import de.mm20.launcher2.applock.SettingsDeepLinkContract

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
)

object TelosApps {

    val all: List<TelosApp> = listOf(
        TelosApp(
            key = "telos_store_app://store",
            name = "Telos Store",
            description = "Installs and updates apps from GitHub, GitLab, Codeberg, F-Droid, IzzyOnDroid, SourceForge and web pages, checks for updates in the background and manages the Telos apps.",
            features = listOf("Update notifications", "Silent updates with Shizuku or root", "Import and export (Obtainium format)"),
            iconRes = de.mm20.launcher2.base.R.drawable.ic_launcher_monochrome,
            route = SettingsDeepLinkContract.ROUTE_STORE,
            removable = false,
        ),
        TelosApp(
            key = "telos_phone_app://phone",
            name = "Telos Phone",
            description = "A complete phone app: dialer, recents, contacts and favorites, call screens and call settings.",
            features = listOf(
                "T9 search (Latin, Greek, Cyrillic)", "Dual SIM routing", "Call recording and call screening",
                "Hidden contacts and app lock", "SIP / VoIP account", "Caller names from a FRITZ!Box",
            ),
            iconRes = de.mm20.launcher2.base.R.drawable.ic_glyph_phone,
            route = SettingsDeepLinkContract.ROUTE_COMMS,
            commsTab = "recents",
        ),
        TelosApp(
            key = "telos_messages_app://messages",
            name = "Telos Messages",
            description = "Your text and picture messages (MMS): conversations, replies, quick replies and scheduled messages. Can be the default SMS app. Conversations with hidden contacts are only shown while the hidden contacts are unlocked.",
            features = listOf("SMS and MMS", "Default SMS app", "Quick replies", "Scheduled messages", "Hidden contacts stay out of the list"),
            iconRes = de.mm20.launcher2.base.R.drawable.ic_glyph_messages,
            route = SettingsDeepLinkContract.ROUTE_COMMS,
            commsTab = "messages",
        ),
        TelosApp(
            key = "telos_radio_app://radio",
            name = "Telos Radio",
            description = "Internet radio with a station search, your own collection and a sleep timer.",
            features = listOf("Radio-Browser search", "M3U / PLS import and export", "Track history", "Next / previous station"),
            iconRes = de.mm20.launcher2.base.R.drawable.ic_glyph_radio,
            route = SettingsDeepLinkContract.ROUTE_RADIO,
        ),
        TelosApp(
            key = "telos_music_app://music",
            name = "Telos Music",
            description = "A player for the music on your phone, with lyrics and a tag editor.",
            features = listOf("Songs, albums, artists", "Synchronized lyrics", "Tag editor", "Scrobbling to Last.fm, Libre.fm and ListenBrainz", "Sleep timer"),
            iconRes = de.mm20.launcher2.base.R.drawable.ic_glyph_music,
            route = SettingsDeepLinkContract.ROUTE_MUSIC,
        ),
        TelosApp(
            key = "telos_video_app://video",
            name = "Telos Video",
            description = "A video player and library for movies and series, which also plays web streams and torrents.",
            features = listOf(
                "Movies and series with posters", "Web streams and torrents (magnet links)", "Subtitle search and download",
                "Gestures, speed, tracks, FFmpeg decoders", "Trakt.tv",
            ),
            iconRes = de.mm20.launcher2.base.R.drawable.ic_glyph_video,
            route = SettingsDeepLinkContract.ROUTE_VIDEO,
        ),
        TelosApp(
            key = "telos_photos_app://photos",
            name = "Telos Photos",
            description = "A gallery and photo editor with a privacy focus.",
            features = listOf("Albums and a date timeline", "EXIF viewer, editor and remover", "Share without metadata", "Crop, rotate, filters"),
            iconRes = de.mm20.launcher2.base.R.drawable.ic_glyph_photos,
            route = SettingsDeepLinkContract.ROUTE_PHOTOS,
        ),
        TelosApp(
            key = "telos_files_app://files",
            name = "Telos Files",
            description = "A file manager: browse and manage the files on your phone, with an optional root explorer for system files.",
            features = listOf(
                "Storage, SD cards and USB", "Copy, move, rename, delete, zip", "Grid and list, sorting, search", "Favorites and properties with checksums",
                "Root explorer with safety warnings",
            ),
            iconRes = de.mm20.launcher2.base.R.drawable.ic_glyph_files,
            route = SettingsDeepLinkContract.ROUTE_FILES,
        ),
    )
}
