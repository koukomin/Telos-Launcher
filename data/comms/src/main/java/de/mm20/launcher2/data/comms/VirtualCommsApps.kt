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
        val drawable = androidx.core.content.ContextCompat.getDrawable(
            context,
            de.mm20.launcher2.base.R.drawable.ic_telos_phone,
        ) ?: return null
        return StaticLauncherIcon(
            foregroundLayer = StaticIconLayer(drawable, 1f),
            backgroundLayer = TransparentLayer,
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
        val drawable = androidx.core.content.ContextCompat.getDrawable(context, de.mm20.launcher2.base.R.drawable.sms_24px) ?: return null
        return StaticLauncherIcon(
            foregroundLayer = StaticIconLayer(drawable, 1f),
            backgroundLayer = TransparentLayer
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
        val drawable = androidx.core.content.ContextCompat.getDrawable(context, de.mm20.launcher2.base.R.drawable.music_note_24px) ?: return null
        return StaticLauncherIcon(
            foregroundLayer = StaticIconLayer(drawable, 1f),
            backgroundLayer = TransparentLayer
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
        val drawable = androidx.core.content.ContextCompat.getDrawable(context, de.mm20.launcher2.base.R.drawable.music_note_24px) ?: return null
        return StaticLauncherIcon(
            foregroundLayer = StaticIconLayer(drawable, 1f),
            backgroundLayer = TransparentLayer
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
        val drawable = androidx.core.content.ContextCompat.getDrawable(context, de.mm20.launcher2.base.R.drawable.play_circle_24px) ?: return null
        return StaticLauncherIcon(
            foregroundLayer = StaticIconLayer(drawable, 1f),
            backgroundLayer = TransparentLayer
        )
    }

    override fun getSerializer(): SearchableSerializer = NullSerializer()

    companion object {
        const val Domain = "telos_video_app"
    }
}

internal class CommsVirtualAppProvider(private val context: Context) : VirtualAppProvider {
    override fun getVirtualApps(): List<Application> = listOf(
        VirtualPhoneApp(context),
        VirtualMessagesApp(context),
        VirtualRadioApp(context),
        VirtualMusicApp(context),
        VirtualVideoApp(context),
    )
}
// === TELOS_PENDING_REVIEW_END: comms_virtual_apps ===
