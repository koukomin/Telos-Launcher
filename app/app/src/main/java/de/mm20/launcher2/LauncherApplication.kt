package de.mm20.launcher2

import android.app.Application
import android.app.ActivityOptions
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
import de.mm20.launcher2.contextprofiles.contextProfilesModule
import de.mm20.launcher2.desktopmode.DesktopModeManager
import de.mm20.launcher2.desktopmode.desktopModeModule
import de.mm20.launcher2.data.customattrs.customAttrsModule
import de.mm20.launcher2.data.i18nDataModule
import de.mm20.launcher2.searchable.searchableModule
import de.mm20.launcher2.files.filesModule
import de.mm20.launcher2.icons.iconsModule
import de.mm20.launcher2.music.musicModule
import de.mm20.launcher2.search.searchModule
import de.mm20.launcher2.unitconverter.unitConverterModule
import de.mm20.launcher2.websites.websitesModule
import de.mm20.launcher2.webappshortcuts.webAppShortcutsModule
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
import de.mm20.launcher2.appmanagement.appManagementModule
import de.mm20.launcher2.data.store.dataStoreModule
import de.mm20.launcher2.data.store.worker.StoreUpdateScheduler
import de.mm20.launcher2.store.storeModule
import de.mm20.launcher2.applock.appLockModule
import de.mm20.launcher2.preferences.applock.AppLockSettings
import de.mm20.launcher2.ui.applock.AppLockOverlayService
import de.mm20.launcher2.plugins.servicesPluginsModule
import de.mm20.launcher2.preferences.preferencesModule
import de.mm20.launcher2.preferences.ui.DynamicIslandSettings
import de.mm20.launcher2.preferences.ui.FloatingLauncherSettings
import de.mm20.launcher2.profiles.profilesModule
import de.mm20.launcher2.ui.desktopmode.DesktopModeActivity
import de.mm20.launcher2.ui.floating.FloatingLauncherService
import de.mm20.launcher2.ui.islandoverlay.DynamicIslandService
import de.mm20.launcher2.ui.islandoverlay.islandOverlayModule
import de.mm20.launcher2.ui.webappspanel.webAppsPanelModule
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
                    webAppShortcutsModule,
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
                    contextProfilesModule,
                    desktopModeModule,
                    islandOverlayModule,
                    webAppsPanelModule,
                    appLockModule,
                    appManagementModule,
                    storeModule,
                    dataStoreModule,
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

        // Same reasoning as the Floating Launcher above - resume the Dynamic Island overlay
        // after a process restart.
        launch {
            val dynamicIslandSettings = get<DynamicIslandSettings>()
            if (dynamicIslandSettings.enabled.first() && Settings.canDrawOverlays(this@LauncherApplication)) {
                ContextCompat.startForegroundService(
                    this@LauncherApplication,
                    Intent(this@LauncherApplication, DynamicIslandService::class.java),
                )
            }
        }

        // Same reasoning as the Floating Launcher above - resume App Lock's overlay watcher
        // after a process restart.
        launch {
            val appLockSettings = get<AppLockSettings>()
            if (appLockSettings.enabled.first() && Settings.canDrawOverlays(this@LauncherApplication)) {
                ContextCompat.startForegroundService(
                    this@LauncherApplication,
                    Intent(this@LauncherApplication, AppLockOverlayService::class.java),
                )
            }
        }

        // Show/hide the desktop shell on an external display as it connects/disconnects, or as
        // the setting is toggled. DesktopModeManager only tracks state - this is the one place
        // that actually knows about DesktopModeActivity (services:desktop-mode sits below app:ui
        // in the dependency graph and can't reference it directly).
        launch {
            val desktopModeManager = get<DesktopModeManager>()
            var shellStarted = false
            desktopModeManager.shouldShowDesktopShell.collect { shouldShow ->
                if (shouldShow && !shellStarted) {
                    val displayId = desktopModeManager.currentExternalDisplayId() ?: return@collect
                    val options = ActivityOptions.makeBasic().apply {
                        launchDisplayId = displayId
                    }
                    try {
                        startActivity(
                            Intent(this@LauncherApplication, DesktopModeActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            },
                            options.toBundle(),
                        )
                        shellStarted = true
                    } catch (e: Exception) {
                        // Device doesn't actually support launching on this display despite
                        // advertising the feature, or the display disappeared mid-launch.
                    }
                } else if (!shouldShow) {
                    // DesktopModeActivity is destroyed by the system when its display goes away;
                    // nothing to do here beyond letting it be relaunched next time.
                    shellStarted = false
                }
            }
        }

        // enqueueUniquePeriodicWork + KEEP is idempotent, so it's safe to call this on every
        // process start rather than gating it behind a one-time setup step.
        get<StoreUpdateScheduler>().enable()
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