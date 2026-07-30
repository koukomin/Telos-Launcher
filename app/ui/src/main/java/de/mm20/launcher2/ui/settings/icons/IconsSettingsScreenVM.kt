package de.mm20.launcher2.ui.settings.icons

import android.content.ComponentName
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.mm20.launcher2.icons.IconPack
import de.mm20.launcher2.icons.IconPackManager
import de.mm20.launcher2.icons.IconService
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.IconShape
import de.mm20.launcher2.preferences.ui.BadgeSettings
import de.mm20.launcher2.preferences.ui.IconSettings
import de.mm20.launcher2.preferences.ui.UiSettings
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.services.favorites.FavoritesService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class IconsSettingsScreenVM(
    private val uiSettings: UiSettings,
    private val iconSettings: IconSettings,
    private val badgeSettings: BadgeSettings,
    private val iconService: IconService,
    private val favoritesService: FavoritesService,
    private val permissionsManager: PermissionsManager,
    private val iconPackManager: IconPackManager,
) : ViewModel() {

    val grid = uiSettings.gridSettings
    val homeGrid = uiSettings.homeGridSettings
    val drawerGrid = uiSettings.drawerGridSettings

    fun setColumnCount(columnCount: Int) {
        uiSettings.setGridColumnCount(columnCount)
    }

    fun setIconSize(iconSize: Int) {
        uiSettings.setGridIconSize(iconSize)
    }

    fun setHomeGridColumnCount(columnCount: Int?) = uiSettings.setHomeGridColumnCount(columnCount)
    fun setHomeGridIconSize(iconSize: Int?) = uiSettings.setHomeGridIconSize(iconSize)

    fun setDrawerGridColumnCount(columnCount: Int?) = uiSettings.setDrawerGridColumnCount(columnCount)
    fun setDrawerGridIconSize(iconSize: Int?) = uiSettings.setDrawerGridIconSize(iconSize)

    fun setGridLabelSize(size: Float) = uiSettings.setGridLabelSize(size)
    fun setGridLabelMaxLines(lines: Int) = uiSettings.setGridLabelMaxLines(lines)
    fun setGridLabelShadow(enabled: Boolean) = uiSettings.setGridLabelShadow(enabled)

    fun setGridLabelColor(color: Int?) = uiSettings.setGridLabelColor(color)

    val drawerBackgroundEnabled = uiSettings.drawerBackgroundEnabled
    val drawerBackgroundColor = uiSettings.drawerBackgroundColor
    val drawerBackgroundOpacity = uiSettings.drawerBackgroundOpacity
    fun setDrawerBackgroundEnabled(enabled: Boolean) = uiSettings.setDrawerBackgroundEnabled(enabled)
    fun setDrawerBackgroundColor(color: Int?) = uiSettings.setDrawerBackgroundColor(color)
    fun setDrawerBackgroundOpacity(opacity: Float) = uiSettings.setDrawerBackgroundOpacity(opacity)

    val folderBackgroundColor = uiSettings.folderBackgroundColor
    fun setFolderBackgroundColor(color: Int?) = uiSettings.setFolderBackgroundColor(color)

    val folderCoverEnabled = uiSettings.folderCoverEnabled
    fun setFolderCoverEnabled(enabled: Boolean) = uiSettings.setFolderCoverEnabled(enabled)

    fun setShowLabels(showLabels: Boolean) {
        uiSettings.setGridShowLabels(showLabels)
    }

    fun setShowList(showList: Boolean) {
        uiSettings.setGridShowList(showList)
    }

    fun setShowListIcons(showIcons: Boolean) {
        uiSettings.setGridShowListIcons(showIcons)
    }

    val iconShape = uiSettings.iconShape
    fun setIconShape(iconShape: IconShape) {
        uiSettings.setIconShape(iconShape)
    }

    val icons = iconSettings
    fun setAdaptifyLegacyIcons(adaptify: Boolean) {
        iconSettings.setAdaptifyLegacyIcons(adaptify)
    }

    fun setThemedIcons(themedIcons: Boolean) {
        iconSettings.setThemedIcons(themedIcons)
    }

    fun setForceThemedIcons(forceThemedIcons: Boolean) {
        iconSettings.setForceThemedIcons(forceThemedIcons)
    }

    val installedIconPacks: Flow<List<IconPack>> = iconService.getInstalledIconPacks().map {
        listOf(
            IconPack(
                name = "System",
                packageName = "",
                version = "",
                themed = true,
            )
        ) + it
    }

    fun setIconPack(iconPack: String?) {
        iconSettings.setIconPack(iconPack?.takeIf { it.isNotBlank() })
    }

    val hasNotificationsPermission = permissionsManager.hasPermission(PermissionGroup.Notifications)
    val hasAttemptedNotificationsPermissionRequest =
        permissionsManager.hasAttemptedRequest(PermissionGroup.Notifications)

    val notificationBadges = badgeSettings.notifications
    fun setNotifications(notifications: Boolean) {
        badgeSettings.setNotifications(notifications)
    }

    val notificationBadgeStyle = badgeSettings.notificationStyle
    fun setNotificationBadgeStyle(style: de.mm20.launcher2.preferences.NotificationBadgeStyle) {
        badgeSettings.setNotificationStyle(style)
    }

    val notificationBadgeColor = badgeSettings.notificationColor
    fun setNotificationBadgeColor(color: Int?) {
        badgeSettings.setNotificationColor(color)
    }

    fun requestNotificationsPermission(context: AppCompatActivity) {
        permissionsManager.requestPermission(context, PermissionGroup.Notifications)
    }

    val cloudFileBadges = badgeSettings.cloudFiles
    fun setCloudFiles(cloudFiles: Boolean) {
        badgeSettings.setCloudFiles(cloudFiles)
    }

    val shortcutBadges = badgeSettings.shortcuts
    fun setShortcuts(shortcuts: Boolean) {
        badgeSettings.setShortcuts(shortcuts)
    }

    val suspendedAppBadges = badgeSettings.suspendedApps
    fun setSuspendedApps(suspendedApps: Boolean) {
        badgeSettings.setSuspendedApps(suspendedApps)
    }

    val pluginBadges = badgeSettings.plugins
    fun setPluginBadges(plugins: Boolean) {
        badgeSettings.setPlugins(plugins)
    }

    private val previewItems = grid.flatMapLatest { grid ->
        favoritesService.getFavorites(
            includeTypes = listOf("app"),
            limit = grid.columnCount,
        )
    }.shareIn(viewModelScope, started = SharingStarted.WhileSubscribed(), 1)

    fun getPreviewIcons(size: Int): Flow<List<LauncherIcon>> {
        return previewItems.flatMapLatest { apps ->
            combine(apps.map {
                iconService.getIcon(it, size).filterNotNull()
            }) {
                it.toList()
            }
        }
    }

    fun getIconPackPreviewIcons(
        context: Context,
        iconPack: IconPack,
        count: Int,
        size: Int,
        themed: Boolean
    ): Flow<List<LauncherIcon>> {
        return previewItems.map { items ->
            val apps = items.filterIsInstance<Application>()
            val icons = mutableListOf<LauncherIcon>()

            val usedApps = mutableSetOf<ComponentName>()

            for (app in apps) {
                val icon = if (iconPack.packageName == "") {
                    app.loadIcon(context, size, themed)
                } else {
                    iconPackManager.getIcon(
                        packageName = app.componentName.packageName,
                        activityName = app.componentName.className,
                        iconPack = iconPack.packageName,
                        allowThemed = themed,
                    )
                }
                if (icon != null) {
                    icons += icon
                    usedApps += app.componentName
                }
            }

            for (fallback in fallbackIconPackIcons) {
                if (icons.size >= count) break

                if (fallback in usedApps) continue

                val icon = iconPackManager.getIcon(
                    packageName = fallback.packageName,
                    activityName = fallback.className,
                    iconPack = iconPack.packageName,
                    allowThemed = themed,
                )
                if (icon != null) {
                    icons += icon
                    usedApps += fallback
                }
            }

            return@map icons
        }
    }

    companion object : KoinComponent {
        val Factory = viewModelFactory {
            initializer {
                IconsSettingsScreenVM(
                    uiSettings = get(),
                    iconService = get(),
                    permissionsManager = get(),
                    favoritesService = get(),
                    badgeSettings = get(),
                    iconSettings = get(),
                    iconPackManager = get(),
                )
            }
        }

        // Some very common activities that are likely included in most icon packs
        private val fallbackIconPackIcons = mutableListOf(
            ComponentName("com.android.vending", "com.android.vending.AssetBrowserActivity"),
            ComponentName("com.google.android.camera", "com.android.camera.Camera"),
            ComponentName("com.android.settings", "com.android.settings.Settings"),
            ComponentName("com.google.android.deskclock", "com.android.deskclock.DeskClock"),
            ComponentName("com.android.chrome", "com.android.chrome.Main"),
            ComponentName("com.google.android.calendar", "com.android.calendar.AllInOneActivity"),
            ComponentName("com.android.dialer", "com.android.dialer.main.impl.MainActivity"),
            ComponentName(
                "com.google.android.googlequicksearchbox",
                "com.google.android.googlequicksearchbox.SearchActivity"
            ),
        )
    }
}