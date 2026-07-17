package de.mm20.launcher2

import android.app.Application
import android.content.Intent
import android.provider.Settings
import androidx.core.content.ContextCompat
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import de.mm20.launcher2.accounts.accountsModule
import de.mm20.launcher2.applications.applicationsModule
import de.mm20.launcher2.appshortcuts.appShortcutsModule
import de.mm20.launcher2.backup.backupModule
import de.mm20.launcher2.badges.badgesModule
import de.mm20.launcher2.calculator.calculatorModule
import de.mm20.launcher2.calendar.calendarModule
import de.mm20.launcher2.contacts.contactsModule
import de.mm20.launcher2.data.customattrs.customAttrsModule
import de.mm20.launcher2.data.i18nDataModule
import de.mm20.launcher2.searchable.searchableModule
import de.mm20.launcher2.files.filesModule
import de.mm20.launcher2.icons.iconsModule
import de.mm20.launcher2.music.musicModule
import de.mm20.launcher2.search.searchModule
import de.mm20.launcher2.unitconverter.unitConverterModule
import de.mm20.launcher2.websites.websitesModule
import de.mm20.launcher2.widgets.widgetsModule
import de.mm20.launcher2.wikipedia.wikipediaModule
import de.mm20.launcher2.database.databaseModule
import de.mm20.launcher2.debug.initDebugMode
import de.mm20.launcher2.globalactions.globalActionsModule
import de.mm20.launcher2.notifications.notificationsModule
import de.mm20.launcher2.locations.locationsModule
import de.mm20.launcher2.permissions.permissionsModule
import de.mm20.launcher2.data.plugins.dataPluginsModule
import de.mm20.launcher2.devicepose.devicePoseModule
import de.mm20.launcher2.feed.feedModule
import de.mm20.launcher2.freeze.freezeModule
import de.mm20.launcher2.plugins.servicesPluginsModule
import de.mm20.launcher2.preferences.preferencesModule
import de.mm20.launcher2.preferences.ui.FloatingLauncherSettings
import de.mm20.launcher2.profiles.profilesModule
import de.mm20.launcher2.ui.floating.FloatingLauncherService
import de.mm20.launcher2.searchactions.searchActionsModule
import de.mm20.launcher2.services.favorites.favoritesModule
import de.mm20.launcher2.services.tags.servicesTagsModule
import de.mm20.launcher2.services.widgets.widgetsServiceModule
import de.mm20.launcher2.themes.themesModule
import de.mm20.launcher2.wallpapers.wallpapersModule
import de.mm20.launcher2.weather.weatherModule
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import kotlin.coroutines.CoroutineContext

class LauncherApplication : Application(), CoroutineScope, ImageLoaderFactory {

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + SupervisorJob()

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.BUILD_TYPE == "debug") initDebugMode()

        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.ERROR else Level.NONE)
            androidContext(this@LauncherApplication)
            modules(
                listOf(
                    accountsModule,
                    applicationsModule,
                    appShortcutsModule,
                    baseModule,
                    calculatorModule,
                    badgesModule,
                    calendarModule,
                    contactsModule,
                    customAttrsModule,
                    databaseModule,
                    favoritesModule,
                    searchableModule,
                    filesModule,
                    globalActionsModule,
                    iconsModule,
                    musicModule,
                    notificationsModule,
                    permissionsModule,
                    preferencesModule,
                    searchModule,
                    searchActionsModule,
                    themesModule,
                    unitConverterModule,
                    weatherModule,
                    websitesModule,
                    widgetsModule,
                    wikipediaModule,
                    locationsModule,
                    servicesTagsModule,
                    widgetsServiceModule,
                    dataPluginsModule,
                    servicesPluginsModule,
                    backupModule,
                    devicePoseModule,
                    profilesModule,
                    i18nDataModule,
                    feedModule,
                    freezeModule,
                    wallpapersModule,
                )
            )
        }

        // Resume the floating quick launcher overlay after a process restart (e.g. reboot),
        // since it isn't a persistent system-level component and only runs while this process
        // is alive.
        launch {
            val floatingLauncherSettings = get<FloatingLauncherSettings>()
            if (floatingLauncherSettings.enabled.first() && Settings.canDrawOverlays(this@LauncherApplication)) {
                ContextCompat.startForegroundService(
                    this@LauncherApplication,
                    Intent(this@LauncherApplication, FloatingLauncherService::class.java),
                )
            }
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(applicationContext)
            .components {
                add(SvgDecoder.Factory())
            }
            .crossfade(true)
            .crossfade(200)
            .build()
    }
}