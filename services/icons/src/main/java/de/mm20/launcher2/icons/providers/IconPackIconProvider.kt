package de.mm20.launcher2.icons.providers

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import androidx.core.content.getSystemService
import de.mm20.launcher2.icons.*
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.search.SavableSearchable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class IconPackIconProvider(
    private val context: Context,
    private val iconPack: IconPack,
    private val iconPackManager: IconPackManager,
    private val allowThemed: Boolean,
): IconProvider {
    override suspend fun getIcon(searchable: SavableSearchable, size: Int): LauncherIcon? {
        if (searchable !is Application || searchable.getSerializer() is de.mm20.launcher2.search.VirtualAppSerializer) return null


        return iconPackManager.getIcon(iconPack.packageName, searchable.componentName.packageName, searchable.componentName.className, allowThemed)
            ?: iconPackManager.generateIcon(
                context,
                iconPack.packageName,
                baseIcon = withContext(de.mm20.launcher2.coroutines.TelosDispatchers.Icon) {
                    val ai = context.getSystemService<LauncherApps>()?.resolveActivity(
                        Intent().setComponent(searchable.componentName),
                        searchable.user
                    )
                    ai?.getIcon(context.resources.displayMetrics.densityDpi) ?: runCatching {
                        // Disabled activities cannot be resolved by LauncherApps, but retain their
                        // original resources. Use those resources for the icon pack's mask/back.
                        if (searchable.user == android.os.Process.myUserHandle())
                            runCatching { context.packageManager.getActivityInfo(searchable.componentName,
                                android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS or
                                    android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES)
                                .loadIcon(context.packageManager) }.getOrElse {
                                context.packageManager.getApplicationInfo(searchable.componentName.packageName,
                                    android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS or
                                        android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES)
                                    .loadIcon(context.packageManager)
                            }
                        else context.getSystemService<LauncherApps>()?.getApplicationInfo(
                            searchable.componentName.packageName,
                            android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS, searchable.user)
                            ?.loadIcon(context.packageManager)
                    }.getOrNull()
                } ?: return null,
                size = size,
            )
    }
}