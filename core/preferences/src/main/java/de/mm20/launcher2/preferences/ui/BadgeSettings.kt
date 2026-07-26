package de.mm20.launcher2.preferences.ui

import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.NotificationBadgeStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

data class BadgeSettingsData(
    val notifications: Boolean = true,
    val suspendedApps: Boolean = true,
    val cloudFiles: Boolean = true,
    val shortcuts: Boolean = true,
    val plugins: Boolean = true,
)

class BadgeSettings internal constructor(
    private val launcherDataStore: LauncherDataStore,
) : Flow<BadgeSettingsData> by (launcherDataStore.data.map {
    BadgeSettingsData(
        notifications = it.badges.badgesNotifications,
        suspendedApps = it.badges.badgesSuspendedApps,
        cloudFiles = it.badges.badgesCloudFiles,
        shortcuts = it.badges.badgesShortcuts,
        plugins = it.badges.badgesPlugins,
    )
}) {
    val notificationStyle
        get() = launcherDataStore.data.map { it.badges.badgesNotificationStyle }.distinctUntilChanged()

    fun setNotificationStyle(style: NotificationBadgeStyle) {
        launcherDataStore.update { it.copy(badges = it.badges.copy(badgesNotificationStyle = style)) }
    }

    /** Null = follow the theme's tertiary color. */
    val notificationColor
        get() = launcherDataStore.data.map { it.badges.badgesNotificationColor }.distinctUntilChanged()

    fun setNotificationColor(color: Int?) {
        launcherDataStore.update { it.copy(badges = it.badges.copy(badgesNotificationColor = color)) }
    }

    val notifications
        get() = launcherDataStore.data.map { it.badges.badgesNotifications }

    fun setNotifications(notifications: Boolean) {
        launcherDataStore.update {
            it.copy(badges = it.badges.copy(badgesNotifications = notifications))
        }
    }

    val suspendedApps
        get() = launcherDataStore.data.map { it.badges.badgesSuspendedApps }

    fun setSuspendedApps(suspendedApps: Boolean) {
        launcherDataStore.update {
            it.copy(badges = it.badges.copy(badgesSuspendedApps = suspendedApps))
        }
    }

    val cloudFiles
        get() = launcherDataStore.data.map { it.badges.badgesCloudFiles }

    fun setCloudFiles(cloudFiles: Boolean) {
        launcherDataStore.update {
            it.copy(badges = it.badges.copy(badgesCloudFiles = cloudFiles))
        }
    }

    val shortcuts
        get() = launcherDataStore.data.map { it.badges.badgesShortcuts }

    fun setShortcuts(shortcuts: Boolean) {
        launcherDataStore.update {
            it.copy(badges = it.badges.copy(badgesShortcuts = shortcuts))
        }
    }

    val plugins
        get() = launcherDataStore.data.map { it.badges.badgesPlugins }

    fun setPlugins(plugins: Boolean) {
        launcherDataStore.update {
            it.copy(badges = it.badges.copy(badgesPlugins = plugins))
        }
    }
}