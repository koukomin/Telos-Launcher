package de.mm20.launcher2.data.store

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

/**
 * A synthetic "app" entry for the Store, so it shows up in the app drawer/app list like any
 * installed app instead of being buried in Settings. [launch] deep-links into
 * [de.mm20.launcher2.ui.settings.SettingsActivity] (referenced only by class name via
 * [SettingsDeepLinkContract], since `:data:store` can't depend on `:app:ui`) pointed straight at
 * the Store dashboard, rather than resolving a real [componentName] through the package manager.
 */
internal class VirtualStoreApp(context: Context) : Application {

    override val key: String = "$Domain://store"
    override val label: String = "Telos Store"
    override val labelOverride: String? = null
    override val domain: String = Domain
    override val score: ResultScore = ResultScore.Unspecified

    override val componentName: ComponentName = ComponentName(
        context.packageName,
        "de.mm20.launcher2.store.VirtualStoreApp",
    )
    override val isSuspended: Boolean = false
    override val user: UserHandle = Process.myUserHandle()
    override val versionName: String? = null

    override val canUninstall: Boolean = false
    override fun uninstall(context: Context) { /* Not a real package - nothing to uninstall. */ }
    override fun openAppDetails(context: Context) { /* No system app-info page for this entry. */ }

    override val canShareApk: Boolean = false

    override fun overrideLabel(label: String): SavableSearchable = this

    override fun launch(context: Context, options: Bundle?): Boolean {
        return try {
            val intent = Intent().apply {
                setClassName(context.packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
                putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_STORE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent, options)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun loadIcon(context: Context, size: Int, themed: Boolean): de.mm20.launcher2.icons.LauncherIcon? {
        val drawable = androidx.core.content.ContextCompat.getDrawable(
            context,
            de.mm20.launcher2.base.R.drawable.ic_app_launcher_fg,
        ) ?: return null
        return StaticLauncherIcon(
            foregroundLayer = StaticIconLayer(drawable, 1f),
            backgroundLayer = de.mm20.launcher2.icons.ColorLayer(0xFF2B2F8F.toInt()),
        )
    }

    override fun getSerializer(): SearchableSerializer = NullSerializer()

    companion object {
        const val Domain = "telos_store_app"
    }
}

internal class StoreVirtualAppProvider(private val context: Context) : VirtualAppProvider {
    override fun getVirtualApps(): List<Application> = listOf(VirtualStoreApp(context))
}
