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
import androidx.core.content.ContextCompat

internal class VirtualTrashApp(context: Context) : Application {
    override val key: String = "$Domain://trash"
    override val label: String = "Κάδος"
    override val labelOverride: String? = null
    override val domain: String = Domain
    override val score: ResultScore = ResultScore.Unspecified

    override val componentName: ComponentName = ComponentName(
        context.packageName,
        "de.mm20.launcher2.comms.VirtualTrashApp",
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
                putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_TRASH)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent, options)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun loadIcon(context: Context, size: Int, themed: Boolean): LauncherIcon? {
        val res = de.mm20.launcher2.search.TelosAppIcons.forDomain(Domain) ?: de.mm20.launcher2.base.R.drawable.delete_24px
        val drawable = ContextCompat.getDrawable(context, res) ?: return null
        return StaticLauncherIcon(
            foregroundLayer = StaticIconLayer(drawable, 1f),
            backgroundLayer = TransparentLayer
        )
    }

    override fun getSerializer(): SearchableSerializer = NullSerializer()

    companion object {
        const val Domain = "telos_trash_app"
    }
}

internal class TrashVirtualAppProvider(private val context: Context) : VirtualAppProvider {
    override fun getVirtualApps(): List<Application> = listOf(VirtualTrashApp(context))
}
