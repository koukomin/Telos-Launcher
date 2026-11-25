package de.mm20.launcher2.data.store

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import de.mm20.launcher2.applock.SettingsDeepLinkContract
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.search.VirtualApplication
import de.mm20.launcher2.search.ResultScore
import de.mm20.launcher2.search.TelosAppKeywords
import de.mm20.launcher2.search.VirtualAppProvider
import de.mm20.launcher2.search.VirtualAppIcon
import de.mm20.launcher2.icons.LauncherIcon

/**
 * A synthetic "app" entry for the Store, so it shows up in the app drawer/app list like any
 * installed app instead of being buried in Settings. [launch] deep-links into
 * [de.mm20.launcher2.ui.settings.SettingsActivity] (referenced only by class name via
 * [SettingsDeepLinkContract], since `:data:store` can't depend on `:app:ui`) pointed straight at
 * the Store dashboard, rather than resolving a real [componentName] through the package manager.
 */
internal class VirtualStoreApp(private val context: Context) : VirtualApplication {

    override val key: String = "$Domain://store"
    override val label: String get() = de.mm20.launcher2.search.TelosAppLabels.forDomain(context, Domain)
    override val keywords: List<String> = TelosAppKeywords.forDomain(Domain)
    override val domain: String = Domain
    override val score: ResultScore = ResultScore.Unspecified

    override val componentName: ComponentName = ComponentName(
        context.packageName,
        "de.mm20.launcher2.store.VirtualStoreApp",
    )

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

    override suspend fun loadIcon(context: Context, size: Int, themed: Boolean): LauncherIcon? {
        return VirtualAppIcon.tileOnPlate(
            context,
            Domain,
            de.mm20.launcher2.base.R.drawable.ic_app_store_full,
            0xFF12131C.toInt(),
        )
    }

    companion object {
        const val Domain = "telos_store_app"
    }
}

internal class StoreVirtualAppProvider(private val context: Context) : VirtualAppProvider {
    override fun getVirtualApps(): List<Application> = listOf(VirtualStoreApp(context))
}
