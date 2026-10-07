// === TELOS_PENDING_REVIEW_START: comms_virtual_apps ===
package de.mm20.launcher2.data.comms

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import de.mm20.launcher2.applock.SettingsDeepLinkContract
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.search.NullSerializer
import de.mm20.launcher2.search.ResultScore
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.SearchableSerializer
import de.mm20.launcher2.search.VirtualAppProvider
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.icons.StaticLauncherIcon
import de.mm20.launcher2.icons.StaticIconLayer
import de.mm20.launcher2.icons.TransparentLayer
import de.mm20.launcher2.icons.ColorLayer

internal class VirtualPhoneApp(context: Context) : Application {

    override val key: String = "$Domain://phone"
    override val label: String = "Telos Phone"
    override val labelOverride: String? = null
    override val domain: String = Domain
    override val score: ResultScore = ResultScore.Unspecified

    override val componentName: ComponentName = ComponentName(
        context.packageName,
        "de.mm20.launcher2.comms.VirtualPhoneApp",
    )
    override val isSuspended: Boolean = false
    override val user: UserHandle = Process.myUserHandle()
    override val versionName: String? = null

    override val canUninstall: Boolean = false
    override fun uninstall(context: Context) {}
    override fun openAppDetails(context: Context) {}

    override val canShareApk: Boolean = false

    override fun overrideLabel(label: String): SavableSearchable = this

    override fun launch(context: Context, options: Bundle?): Boolean {
        return try {
            val intent = Intent().apply {
                setClassName(context.packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
                putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_COMMS)
                putExtra(SettingsDeepLinkContract.EXTRA_COMMS_TAB, "recents")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent, options)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun loadIcon(context: Context, size: Int, themed: Boolean): LauncherIcon? {
        val drawable = androidx.core.content.ContextCompat.getDrawable(context, de.mm20.launcher2.base.R.drawable.ic_app_phone_fg) ?: return null
        return StaticLauncherIcon(
            foregroundLayer = StaticIconLayer(drawable, 1f),
            backgroundLayer = ColorLayer(0xFF0B1F4B.toInt()),
        )
    }

    override fun getSerializer(): SearchableSerializer = NullSerializer()

    companion object {
        const val Domain = "telos_phone_app"
    }
}

internal class VirtualMessagesApp(context: Context) : Application {

    override val key: String = "$Domain://messages"
    override val label: String = "Telos Messages"
    override val labelOverride: String? = null
    override val domain: String = Domain
    override val score: ResultScore = ResultScore.Unspecified

    override val componentName: ComponentName = ComponentName(
        context.packageName,
        "de.mm20.launcher2.comms.VirtualMessagesApp",
    )
    override val isSuspended: Boolean = false
    override val user: UserHandle = Process.myUserHandle()
    override val versionName: String? = null

    override val canUninstall: Boolean = false
    override fun uninstall(context: Context) {}
    override fun openAppDetails(context: Context) {}

    override val canShareApk: Boolean = false

    override fun overrideLabel(label: String): SavableSearchable = this

    override fun launch(context: Context, options: Bundle?): Boolean {
        return try {
            val intent = Intent().apply {
                setClassName(context.packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
                putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_COMMS)
                putExtra(SettingsDeepLinkContract.EXTRA_COMMS_TAB, "messages")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent, options)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun loadIcon(context: Context, size: Int, themed: Boolean): LauncherIcon? {
        val drawable = androidx.core.content.ContextCompat.getDrawable(context, de.mm20.launcher2.base.R.drawable.ic_app_messages_fg) ?: return null
        return StaticLauncherIcon(
            foregroundLayer = StaticIconLayer(drawable, 1f),
            backgroundLayer = ColorLayer(0xFF0F6B63.toInt()),
        )
    }

    override fun getSerializer(): SearchableSerializer = NullSerializer()

    companion object {
        const val Domain = "telos_messages_app"
    }
}

internal class VirtualRadioApp(context: Context) : Application {

    override val key: String = "$Domain://radio"
    override val label: String = "Telos Radio"
    override val labelOverride: String? = null
    override val domain: String = Domain
    override val score: ResultScore = ResultScore.Unspecified

    override val componentName: ComponentName = ComponentName(
        context.packageName,
        "de.mm20.launcher2.comms.VirtualRadioApp",
    )
    override val isSuspended: Boolean = false
    override val user: UserHandle = Process.myUserHandle()
    override val versionName: String? = null

    override val canUninstall: Boolean = false
    override fun uninstall(context: Context) {}
    override fun openAppDetails(context: Context) {}

    override val canShareApk: Boolean = false

    override fun overrideLabel(label: String): SavableSearchable = this

    override fun launch(context: Context, options: Bundle?): Boolean {
        return try {
            val intent = Intent().apply {
                setClassName(context.packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
                putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_RADIO)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent, options)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun loadIcon(context: Context, size: Int, themed: Boolean): LauncherIcon? {
        val drawable = androidx.core.content.ContextCompat.getDrawable(context, de.mm20.launcher2.base.R.drawable.ic_app_radio_fg) ?: return null
        return StaticLauncherIcon(
            foregroundLayer = StaticIconLayer(drawable, 1f),
            backgroundLayer = ColorLayer(0xFF1B7F3B.toInt()),
        )
    }

    override fun getSerializer(): SearchableSerializer = NullSerializer()

    companion object {
        const val Domain = "telos_radio_app"
    }
}

internal class VirtualMusicApp(context: Context) : Application {

    override val key: String = "$Domain://music"
    override val label: String = "Telos Music"
    override val labelOverride: String? = null
    override val domain: String = Domain
    override val score: ResultScore = ResultScore.Unspecified

    override val componentName: ComponentName = ComponentName(
        context.packageName,
        "de.mm20.launcher2.comms.VirtualMusicApp",
    )
    override val isSuspended: Boolean = false
    override val user: UserHandle = Process.myUserHandle()
    override val versionName: String? = null

    override val canUninstall: Boolean = false
    override fun uninstall(context: Context) {}
    override fun openAppDetails(context: Context) {}

    override val canShareApk: Boolean = false

    override fun overrideLabel(label: String): SavableSearchable = this

    override fun launch(context: Context, options: Bundle?): Boolean {
        return try {
            val intent = Intent().apply {
                setClassName(context.packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
                putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_MUSIC)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent, options)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun loadIcon(context: Context, size: Int, themed: Boolean): LauncherIcon? {
        val drawable = androidx.core.content.ContextCompat.getDrawable(context, de.mm20.launcher2.base.R.drawable.ic_app_music_fg) ?: return null
        return StaticLauncherIcon(
            foregroundLayer = StaticIconLayer(drawable, 1f),
            backgroundLayer = ColorLayer(0xFF5B2DB5.toInt()),
        )
    }

    override fun getSerializer(): SearchableSerializer = NullSerializer()

    companion object {
        const val Domain = "telos_music_app"
    }
}

internal class VirtualVideoApp(context: Context) : Application {

    override val key: String = "$Domain://video"
    override val label: String = "Telos Video"
    override val labelOverride: String? = null
    override val domain: String = Domain
    override val score: ResultScore = ResultScore.Unspecified

    override val componentName: ComponentName = ComponentName(
        context.packageName,
        "de.mm20.launcher2.comms.VirtualVideoApp",
    )
    override val isSuspended: Boolean = false
    override val user: UserHandle = Process.myUserHandle()
    override val versionName: String? = null

    override val canUninstall: Boolean = false
    override fun uninstall(context: Context) {}
    override fun openAppDetails(context: Context) {}

    override val canShareApk: Boolean = false

    override fun overrideLabel(label: String): SavableSearchable = this

    override fun launch(context: Context, options: Bundle?): Boolean {
        return try {
            val intent = Intent().apply {
                setClassName(context.packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
                putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_VIDEO)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent, options)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun loadIcon(context: Context, size: Int, themed: Boolean): LauncherIcon? {
        val drawable = androidx.core.content.ContextCompat.getDrawable(context, de.mm20.launcher2.base.R.drawable.ic_app_video_fg) ?: return null
        return StaticLauncherIcon(
            foregroundLayer = StaticIconLayer(drawable, 1f),
            backgroundLayer = ColorLayer(0xFFB3261E.toInt()),
        )
    }

    override fun getSerializer(): SearchableSerializer = NullSerializer()

    companion object {
        const val Domain = "telos_video_app"
    }
}

internal class VirtualPhotosApp(context: Context) : Application {

    override val key: String = "$Domain://photos"
    override val label: String = "Telos Photos"
    override val labelOverride: String? = null
    override val domain: String = Domain
    override val score: ResultScore = ResultScore.Unspecified

    override val componentName: ComponentName = ComponentName(
        context.packageName,
        "de.mm20.launcher2.comms.VirtualPhotosApp",
    )
    override val isSuspended: Boolean = false
    override val user: UserHandle = Process.myUserHandle()
    override val versionName: String? = null

    override val canUninstall: Boolean = false
    override fun uninstall(context: Context) {}
    override fun openAppDetails(context: Context) {}

    override val canShareApk: Boolean = false

    override fun overrideLabel(label: String): SavableSearchable = this

    override fun launch(context: Context, options: Bundle?): Boolean {
        return try {
            val intent = Intent().apply {
                setClassName(context.packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
                putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_PHOTOS)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent, options)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun loadIcon(context: Context, size: Int, themed: Boolean): LauncherIcon? {
        val drawable = androidx.core.content.ContextCompat.getDrawable(context, de.mm20.launcher2.base.R.drawable.ic_app_photos_fg) ?: return null
        return StaticLauncherIcon(
            foregroundLayer = StaticIconLayer(drawable, 1f),
            backgroundLayer = ColorLayer(0xFFB4236A.toInt()),
        )
    }

    override fun getSerializer(): SearchableSerializer = NullSerializer()

    companion object {
        const val Domain = "telos_photos_app"
    }
}

internal class VirtualFilesApp(context: Context) : Application {

    override val key: String = "$Domain://files"
    override val label: String = "Telos Files"
    override val labelOverride: String? = null
    override val domain: String = Domain
    override val score: ResultScore = ResultScore.Unspecified

    override val componentName: ComponentName = ComponentName(
        context.packageName,
        "de.mm20.launcher2.comms.VirtualFilesApp",
    )
    override val isSuspended: Boolean = false
    override val user: UserHandle = Process.myUserHandle()
    override val versionName: String? = null

    override val canUninstall: Boolean = false
    override fun uninstall(context: Context) {}
    override fun openAppDetails(context: Context) {}

    override val canShareApk: Boolean = false

    override fun overrideLabel(label: String): SavableSearchable = this

    override fun launch(context: Context, options: Bundle?): Boolean {
        return try {
            val intent = Intent().apply {
                setClassName(context.packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
                putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_FILES)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent, options)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun loadIcon(context: Context, size: Int, themed: Boolean): LauncherIcon? {
        val drawable = androidx.core.content.ContextCompat.getDrawable(context, de.mm20.launcher2.base.R.drawable.ic_app_files_fg) ?: return null
        return StaticLauncherIcon(
            foregroundLayer = StaticIconLayer(drawable, 1f),
            backgroundLayer = ColorLayer(0xFFB45309.toInt()),
        )
    }

    override fun getSerializer(): SearchableSerializer = NullSerializer()

    companion object {
        const val Domain = "telos_files_app"
    }
}

internal class VirtualCalculatorApp(context: Context) : Application {

    override val key: String = "$Domain://calculator"
    override val label: String = "Telos Calculator"
    override val labelOverride: String? = null
    override val domain: String = Domain
    override val score: ResultScore = ResultScore.Unspecified

    override val componentName: ComponentName = ComponentName(
        context.packageName,
        "de.mm20.launcher2.comms.VirtualCalculatorApp",
    )
    override val isSuspended: Boolean = false
    override val user: UserHandle = Process.myUserHandle()
    override val versionName: String? = null

    override val canUninstall: Boolean = false
    override fun uninstall(context: Context) {}
    override fun openAppDetails(context: Context) {}

    override val canShareApk: Boolean = false

    override fun overrideLabel(label: String): SavableSearchable = this

    override fun launch(context: Context, options: Bundle?): Boolean {
        return try {
            val intent = Intent().apply {
                setClassName(context.packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
                putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_CALCULATOR)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent, options)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun loadIcon(context: Context, size: Int, themed: Boolean): LauncherIcon? {
        val drawable = androidx.core.content.ContextCompat.getDrawable(context, de.mm20.launcher2.base.R.drawable.ic_app_calculator_fg) ?: return null
        return StaticLauncherIcon(
            foregroundLayer = StaticIconLayer(drawable, 1f),
            backgroundLayer = ColorLayer(0xFF37474F.toInt()),
        )
    }

    override fun getSerializer(): SearchableSerializer = NullSerializer()

    companion object {
        const val Domain = "telos_calculator_app"
    }
}

internal class VirtualVoiceRecorderApp(context: Context) : Application {

    override val key: String = "$Domain://voice_recorder"
    override val label: String = "Telos Voice Recorder"
    override val labelOverride: String? = null
    override val domain: String = Domain
    override val score: ResultScore = ResultScore.Unspecified

    override val componentName: ComponentName = ComponentName(
        context.packageName,
        "de.mm20.launcher2.comms.VirtualVoiceRecorderApp",
    )
    override val isSuspended: Boolean = false
    override val user: UserHandle = Process.myUserHandle()
    override val versionName: String? = null

    override val canUninstall: Boolean = false
    override fun uninstall(context: Context) {}
    override fun openAppDetails(context: Context) {}

    override val canShareApk: Boolean = false

    override fun overrideLabel(label: String): SavableSearchable = this

    override fun launch(context: Context, options: Bundle?): Boolean {
        return try {
            val intent = Intent().apply {
                setClassName(context.packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
                putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_VOICE_RECORDER)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent, options)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun loadIcon(context: Context, size: Int, themed: Boolean): LauncherIcon? {
        val drawable = androidx.core.content.ContextCompat.getDrawable(context, de.mm20.launcher2.base.R.drawable.ic_app_voice_recorder_fg) ?: return null
        return StaticLauncherIcon(
            foregroundLayer = StaticIconLayer(drawable, 1f),
            backgroundLayer = ColorLayer(0xFF00838F.toInt()),
        )
    }

    override fun getSerializer(): SearchableSerializer = NullSerializer()

    companion object {
        const val Domain = "telos_voice_recorder_app"
    }
}

internal class VirtualScreenRecorderApp(context: Context) : Application {

    override val key: String = "$Domain://screen_recorder"
    override val label: String = "Telos Screen Recorder"
    override val labelOverride: String? = null
    override val domain: String = Domain
    override val score: ResultScore = ResultScore.Unspecified

    override val componentName: ComponentName = ComponentName(
        context.packageName,
        "de.mm20.launcher2.comms.VirtualScreenRecorderApp",
    )
    override val isSuspended: Boolean = false
    override val user: UserHandle = Process.myUserHandle()
    override val versionName: String? = null

    override val canUninstall: Boolean = false
    override fun uninstall(context: Context) {}
    override fun openAppDetails(context: Context) {}

    override val canShareApk: Boolean = false

    override fun overrideLabel(label: String): SavableSearchable = this

    override fun launch(context: Context, options: Bundle?): Boolean {
        return try {
            val intent = Intent().apply {
                setClassName(context.packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
                putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_SCREEN_RECORDER)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent, options)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun loadIcon(context: Context, size: Int, themed: Boolean): LauncherIcon? {
        val drawable = androidx.core.content.ContextCompat.getDrawable(context, de.mm20.launcher2.base.R.drawable.ic_app_screen_recorder_fg) ?: return null
        return StaticLauncherIcon(
            foregroundLayer = StaticIconLayer(drawable, 1f),
            backgroundLayer = ColorLayer(0xFFE64A19.toInt()),
        )
    }

    override fun getSerializer(): SearchableSerializer = NullSerializer()

    companion object {
        const val Domain = "telos_screen_recorder_app"
    }
}

internal class VirtualScreenshotApp(context: Context) : Application {

    override val key: String = "$Domain://screenshot"
    override val label: String = "Telos Screenshot"
    override val labelOverride: String? = null
    override val domain: String = Domain
    override val score: ResultScore = ResultScore.Unspecified

    override val componentName: ComponentName = ComponentName(
        context.packageName,
        "de.mm20.launcher2.comms.VirtualScreenshotApp",
    )
    override val isSuspended: Boolean = false
    override val user: UserHandle = Process.myUserHandle()
    override val versionName: String? = null

    override val canUninstall: Boolean = false
    override fun uninstall(context: Context) {}
    override fun openAppDetails(context: Context) {}

    override val canShareApk: Boolean = false

    override fun overrideLabel(label: String): SavableSearchable = this

    override fun launch(context: Context, options: Bundle?): Boolean {
        return try {
            val intent = Intent().apply {
                setClassName(context.packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
                putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_SCREENSHOT)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent, options)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun loadIcon(context: Context, size: Int, themed: Boolean): LauncherIcon? {
        val drawable = androidx.core.content.ContextCompat.getDrawable(context, de.mm20.launcher2.base.R.drawable.ic_app_screenshot_fg) ?: return null
        return StaticLauncherIcon(
            foregroundLayer = StaticIconLayer(drawable, 1f),
            backgroundLayer = ColorLayer(0xFF1A6DFF.toInt()),
        )
    }

    override fun getSerializer(): SearchableSerializer = NullSerializer()

    companion object {
        const val Domain = "telos_screenshot_app"
    }
}

internal class VirtualNotesApp(context: Context) : Application {

    override val key: String = "$Domain://notes"
    override val label: String = "Telos Notes"
    override val labelOverride: String? = null
    override val domain: String = Domain
    override val score: ResultScore = ResultScore.Unspecified

    override val componentName: ComponentName = ComponentName(
        context.packageName,
        "de.mm20.launcher2.comms.VirtualNotesApp",
    )
    override val isSuspended: Boolean = false
    override val user: UserHandle = Process.myUserHandle()
    override val versionName: String? = null

    override val canUninstall: Boolean = false
    override fun uninstall(context: Context) {}
    override fun openAppDetails(context: Context) {}

    override val canShareApk: Boolean = false

    override fun overrideLabel(label: String): SavableSearchable = this

    override fun launch(context: Context, options: Bundle?): Boolean {
        return try {
            val intent = Intent().apply {
                setClassName(context.packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
                putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_NOTES)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent, options)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun loadIcon(context: Context, size: Int, themed: Boolean): LauncherIcon? {
        val drawable = androidx.core.content.ContextCompat.getDrawable(context, de.mm20.launcher2.base.R.drawable.ic_app_notes_fg) ?: return null
        return StaticLauncherIcon(
            foregroundLayer = StaticIconLayer(drawable, 1f),
            backgroundLayer = ColorLayer(0xFFF9A825.toInt()),
        )
    }

    override fun getSerializer(): SearchableSerializer = NullSerializer()

    companion object {
        const val Domain = "telos_notes_app"
    }
}

internal class VirtualCalendarApp(context: Context) : Application {

    override val key: String = "$Domain://calendar"
    override val label: String = "Telos Calendar"
    override val labelOverride: String? = null
    override val domain: String = Domain
    override val score: ResultScore = ResultScore.Unspecified

    override val componentName: ComponentName = ComponentName(
        context.packageName,
        "de.mm20.launcher2.comms.VirtualCalendarApp",
    )
    override val isSuspended: Boolean = false
    override val user: UserHandle = Process.myUserHandle()
    override val versionName: String? = null

    override val canUninstall: Boolean = false
    override fun uninstall(context: Context) {}
    override fun openAppDetails(context: Context) {}

    override val canShareApk: Boolean = false

    override fun overrideLabel(label: String): SavableSearchable = this

    override fun launch(context: Context, options: Bundle?): Boolean {
        return try {
            val intent = Intent().apply {
                setClassName(context.packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
                putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_CALENDAR)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent, options)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun loadIcon(context: Context, size: Int, themed: Boolean): LauncherIcon? {
        val drawable = androidx.core.content.ContextCompat.getDrawable(context, de.mm20.launcher2.base.R.drawable.ic_app_calendar_fg) ?: return null
        return StaticLauncherIcon(
            foregroundLayer = StaticIconLayer(drawable, 1f),
            backgroundLayer = ColorLayer(0xFF1E88E5.toInt()),
        )
    }

    override fun getSerializer(): SearchableSerializer = NullSerializer()

    companion object {
        const val Domain = "telos_calendar_app"
    }
}

internal class CommsVirtualAppProvider(private val context: Context) : VirtualAppProvider {
    override fun getVirtualApps(): List<Application> = listOf(
        VirtualPhoneApp(context),
        VirtualMessagesApp(context),
        VirtualRadioApp(context),
        VirtualMusicApp(context),
        VirtualVideoApp(context),
        VirtualPhotosApp(context),
        VirtualFilesApp(context),
        VirtualCalculatorApp(context),
        VirtualVoiceRecorderApp(context),
        VirtualScreenRecorderApp(context),
        VirtualScreenshotApp(context),
        VirtualNotesApp(context),
        VirtualCalendarApp(context),
    )
}
// === TELOS_PENDING_REVIEW_END: comms_virtual_apps ===
