package de.mm20.launcher2.ui.settings

import android.content.Intent
import de.mm20.launcher2.applock.SettingsDeepLinkContract
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ui.settings.appearance.AppearanceSettingsRoute
import de.mm20.launcher2.ui.settings.crashreporter.CrashReportRoute
import de.mm20.launcher2.ui.settings.applock.IntruderPhotosRoute
import de.mm20.launcher2.ui.settings.desktopmode.DesktopModeSettingsRoute
import de.mm20.launcher2.ui.settings.floating.FloatingLauncherSettingsRoute
import de.mm20.launcher2.ui.settings.homescreen.wallpaper.WallpaperSettingsRoute
import de.mm20.launcher2.ui.settings.webapps.WebAppsSettingsRoute
import de.mm20.launcher2.ui.settings.gestures.GesturesSettingsRoute
import de.mm20.launcher2.ui.settings.hiddenitems.HiddenItemsSettingsRoute
import de.mm20.launcher2.ui.settings.homescreen.HomescreenSettingsRoute
import de.mm20.launcher2.ui.comms.CommsDashboardRoute
import de.mm20.launcher2.ui.store.StoreDashboardRoute
import de.mm20.launcher2.ui.settings.media.MediaIntegrationSettingsRoute
import de.mm20.launcher2.ui.settings.search.SearchSettingsRoute
import de.mm20.launcher2.ui.settings.searchactions.SearchActionsSettingsRoute
import de.mm20.launcher2.ui.settings.weather.WeatherIntegrationSettingsRoute

internal fun resolveSettingsStartRoute(intent: Intent): NavKey? {
    val routeName = if (intent.data?.host == "kvaesitso.mm20.de") {
        intent.data?.getQueryParameter("route") ?: return null
    } else {
        intent.getStringExtra(SettingsActivity.EXTRA_ROUTE) ?: return null
    }
    return when(routeName) {
        SettingsActivity.ROUTE_WEATHER_INTEGRATION -> WeatherIntegrationSettingsRoute
        SettingsActivity.ROUTE_DESKTOP_MODE -> DesktopModeSettingsRoute
        SettingsActivity.ROUTE_MEDIA_INTEGRATION -> MediaIntegrationSettingsRoute
        SettingsActivity.ROUTE_SEARCH_ACTIONS -> SearchActionsSettingsRoute
        SettingsActivity.ROUTE_HIDDEN_ITEMS -> HiddenItemsSettingsRoute
        SettingsActivity.ROUTE_FLOATING_LAUNCHER -> FloatingLauncherSettingsRoute
        SettingsActivity.ROUTE_SECURITY_HUB -> de.mm20.launcher2.ui.system.SystemDashboardRoute("shield")
        SettingsActivity.ROUTE_WALLPAPER -> WallpaperSettingsRoute
        SettingsActivity.ROUTE_WEB_APPS -> WebAppsSettingsRoute
        SettingsActivity.ROUTE_INTRUDER_PHOTOS -> IntruderPhotosRoute
        SettingsActivity.ROUTE_STORE -> StoreDashboardRoute(addUrl = intent.getStringExtra(SettingsDeepLinkContract.EXTRA_STORE_URL).orEmpty())
        SettingsActivity.ROUTE_COMMS -> {
            val tab = intent.getStringExtra(SettingsDeepLinkContract.EXTRA_COMMS_TAB) ?: "recents"
            val number = intent.getStringExtra(SettingsDeepLinkContract.EXTRA_DIAL_NUMBER).orEmpty()
            CommsDashboardRoute(
                initialTab = tab,
                initialNumber = number,
                initialBody = intent.getStringExtra(SettingsDeepLinkContract.EXTRA_SMS_BODY).orEmpty(),
                initialAttachments = intent.getStringArrayListExtra(SettingsDeepLinkContract.EXTRA_SMS_ATTACHMENTS).orEmpty(),
            )
        }
        SettingsActivity.ROUTE_RADIO -> de.mm20.launcher2.ui.media.hub.MediaHubRoute("radio")
        SettingsActivity.ROUTE_MUSIC -> de.mm20.launcher2.ui.media.hub.MediaHubRoute("music")
        SettingsDeepLinkContract.ROUTE_YOUTUBE -> de.mm20.launcher2.ui.media.hub.MediaHubRoute(
            "youtube", intent.getStringExtra(SettingsDeepLinkContract.EXTRA_YOUTUBE_LINK)?.takeIf {
                de.mm20.launcher2.comms.youtube.YouTubeWebLinks.musicTarget(it) != null
            }, intent.getLongExtra(SettingsDeepLinkContract.EXTRA_YOUTUBE_LINK_REQUEST, 0L),
        )
        SettingsDeepLinkContract.ROUTE_YOUTUBE_MUSIC -> de.mm20.launcher2.ui.media.hub.MediaHubRoute(
            "youtube_music", intent.getStringExtra(SettingsDeepLinkContract.EXTRA_YOUTUBE_LINK)?.takeIf {
                de.mm20.launcher2.comms.youtube.YouTubeWebLinks.musicTarget(it) != null
            }, intent.getLongExtra(SettingsDeepLinkContract.EXTRA_YOUTUBE_LINK_REQUEST, 0L),
        )
        SettingsActivity.ROUTE_VIDEO -> de.mm20.launcher2.ui.media.hub.MediaHubRoute("video")
        SettingsDeepLinkContract.ROUTE_TV -> de.mm20.launcher2.ui.media.hub.MediaHubRoute("tv")
        SettingsDeepLinkContract.ROUTE_STREMIO -> de.mm20.launcher2.ui.media.hub.MediaHubRoute(
            "stremio",
            intent.getStringExtra(SettingsDeepLinkContract.EXTRA_STREMIO_ADDON),
            intent.getLongExtra(SettingsDeepLinkContract.EXTRA_YOUTUBE_LINK_REQUEST, 0L),
            intent.getStringExtra(SettingsDeepLinkContract.EXTRA_STREMIO_DETAIL_TYPE),
            intent.getStringExtra(SettingsDeepLinkContract.EXTRA_STREMIO_DETAIL_ID),
        )
        SettingsActivity.ROUTE_MEDIA -> de.mm20.launcher2.ui.media.hub.MediaHubRoute(
            intent.getStringExtra(SettingsDeepLinkContract.EXTRA_MEDIA_SPACE).orEmpty()
        )
        SettingsActivity.ROUTE_PHOTOS -> de.mm20.launcher2.ui.media.docs.ViewerRoute
        SettingsDeepLinkContract.ROUTE_VIEWER -> de.mm20.launcher2.ui.media.docs.ViewerRoute
        SettingsActivity.ROUTE_FILES -> de.mm20.launcher2.ui.files.FilesRoute
        "settings/akou" -> de.mm20.launcher2.ui.media.akou.AkouRoute
        "settings/clock" -> de.mm20.launcher2.ui.clock.ClockRoute
        SettingsDeepLinkContract.ROUTE_CAMERA -> de.mm20.launcher2.ui.camera.CameraRoute
        SettingsDeepLinkContract.ROUTE_ERMIS -> de.mm20.launcher2.ui.ermis.ErmisRoute
        "settings/magnifier" -> de.mm20.launcher2.ui.tools.MagnifierRoute
        "settings/flashlight" -> de.mm20.launcher2.ui.tools.FlashlightRoute
        SettingsActivity.ROUTE_NETWORK -> de.mm20.launcher2.ui.network.NetworkRoute
        SettingsDeepLinkContract.ROUTE_NETWORK_SPEED_TEST -> de.mm20.launcher2.ui.network.NetworkSpeedTestRoute
        SettingsDeepLinkContract.ROUTE_NETWORK_PRIVATE_DNS -> de.mm20.launcher2.ui.network.PrivateDnsRoute
        SettingsDeepLinkContract.ROUTE_SYSTEM -> de.mm20.launcher2.ui.system.SystemDashboardRoute(
            intent.getStringExtra(SettingsDeepLinkContract.EXTRA_SYSTEM_TAB).orEmpty(),
        )
        SettingsDeepLinkContract.ROUTE_WEATHER -> de.mm20.launcher2.ui.weather.WeatherRoute
        SettingsDeepLinkContract.ROUTE_TRASH -> de.mm20.launcher2.ui.trash.TrashUnlockRoute
        SettingsActivity.ROUTE_CALCULATOR -> de.mm20.launcher2.ui.calculator.CalculatorRoute
        SettingsActivity.ROUTE_VOICE_RECORDER -> de.mm20.launcher2.ui.voice.VoiceRecorderRoute
        SettingsDeepLinkContract.ROUTE_KEYBOARD -> de.mm20.launcher2.ui.keyboard.TelosKeyboardRoute
        SettingsDeepLinkContract.ROUTE_KEEPASS -> de.mm20.launcher2.ui.keepass.KeePassDashboardRoute
        SettingsDeepLinkContract.ROUTE_AETHER -> de.mm20.launcher2.ui.aether.AetherDashboardRoute()
        SettingsDeepLinkContract.ROUTE_BACKUP -> de.mm20.launcher2.ui.backup.BackupRoute
        SettingsDeepLinkContract.ROUTE_SYNC -> de.mm20.launcher2.ui.sync.SyncRoute
        SettingsDeepLinkContract.ROUTE_FIT -> de.mm20.launcher2.ui.fit.FitDashboardRoute
        SettingsActivity.ROUTE_SCREEN_RECORDER -> de.mm20.launcher2.ui.screenrec.ScreenRecorderRoute
        SettingsActivity.ROUTE_SCREENSHOT -> de.mm20.launcher2.ui.screenshot.ScreenshotRoute
        SettingsActivity.ROUTE_NOTES -> de.mm20.launcher2.ui.notes.NotesRoute
        SettingsActivity.ROUTE_CALENDAR -> de.mm20.launcher2.ui.calendar.CalendarRoute
        SettingsActivity.ROUTE_DOWNLOADS -> de.mm20.launcher2.ui.downloads.DownloadsRoute(
            initialUrls = intent.getStringArrayListExtra(SettingsDeepLinkContract.EXTRA_DOWNLOAD_URLS).orEmpty(),
        )
        SettingsDeepLinkContract.ROUTE_TELOS_PAGE -> {
            val topic = intent.getStringExtra(SettingsDeepLinkContract.EXTRA_HELP_TOPIC)
            if (topic.isNullOrEmpty()) de.mm20.launcher2.ui.help.HelpDashboardRoute
            else de.mm20.launcher2.ui.help.HelpLessonRoute(topic)
        }
        "settings/appearance" -> AppearanceSettingsRoute
        "settings/search" -> SearchSettingsRoute
        "settings/gestures" -> GesturesSettingsRoute
        "settings/homescreen" -> HomescreenSettingsRoute
        SettingsActivity.ROUTE_CRASH_REPORT if (intent.hasExtra(SettingsActivity.EXTRA_CRASH_REPORT_PATH)) -> {
            CrashReportRoute(intent.getStringExtra(SettingsActivity.EXTRA_CRASH_REPORT_PATH)!!)
        }
        else -> null
    }
}
