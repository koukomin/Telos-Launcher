package de.mm20.launcher2.data.comms

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Process
import androidx.core.content.ContextCompat
import de.mm20.launcher2.icons.StaticIconLayer
import de.mm20.launcher2.icons.StaticLauncherIcon
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.search.ResultScore
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.TelosAppKeywords
import de.mm20.launcher2.search.TelosAppLabels
import de.mm20.launcher2.search.VirtualAppSerializer
import de.mm20.launcher2.applock.SettingsDeepLinkContract

/** A companion screen for the official Termux runtime; never impersonates its package or UID. */
internal class VirtualTerminalApp(private val context: Context) : Application {
    override val key = "$Domain://terminal"
    override val domain = Domain
    override val label get() = TelosAppLabels.forDomain(context, Domain)
    override val labelOverride: String? = null
    override val keywords = TelosAppKeywords.forDomain(Domain)
    override val score = ResultScore.Unspecified
    override val componentName = ComponentName(context.packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
    override val isSuspended = false
    override val user = Process.myUserHandle()
    override val versionName: String? = null
    override val canUninstall = false
    override val canShareApk = false
    override fun uninstall(context: Context) = Unit
    override fun openAppDetails(context: Context) = Unit
    override fun overrideLabel(label: String): SavableSearchable = this
    override fun launch(context: Context, options: Bundle?): Boolean = runCatching {
        context.startActivity(android.content.Intent().apply {
            setClassName(context.packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
            putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_TERMINAL)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }, options)
        true
    }.getOrDefault(false)

    override suspend fun loadIcon(context: Context, size: Int, themed: Boolean): StaticLauncherIcon? {
        val foreground = ContextCompat.getDrawable(context, de.mm20.launcher2.base.R.drawable.ic_app_terminal_full)
            ?: return null
        return StaticLauncherIcon(
            foregroundLayer = StaticIconLayer(foreground, 1f),
            backgroundLayer = StaticIconLayer(ColorDrawable(0xFF183E48.toInt()), 1f),
        )
    }

    override fun getSerializer() = VirtualAppSerializer()

    companion object { const val Domain = "telos_terminal_app" }
}
