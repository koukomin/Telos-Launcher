package de.mm20.launcher2.webapp

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle

/**
 * Internal, same-APK contract for the Fenix host. An installed external browser does not qualify.
 * Version 1 requires the host to implement the URL, CSS and isolated web-app profile contract.
 * Until that runtime is packaged, neither an Ironewolf app entry nor an engine choice is shown.
 */
object IronewolfRuntime {
    const val Domain = "telos_ironewolf_app"
    const val Key = "$Domain://browser"
    const val ActivityClass = "de.mm20.launcher2.ironewolf.BrowserActivity"
    const val ContractMetadata = "de.mm20.launcher2.ironewolf.EMBEDDED_CONTRACT_VERSION"
    const val ContractVersion = 1

    fun isBundled(context: Context): Boolean = runCatching {
        val component = ComponentName(context.packageName, ActivityClass)
        val info = context.packageManager.getActivityInfo(component, PackageManager.GET_META_DATA)
        info.enabled && info.applicationInfo?.enabled == true && !info.exported &&
            info.packageName == context.packageName &&
            info.metaData?.getInt(ContractMetadata, 0) == ContractVersion
    }.getOrDefault(false)

    fun launch(
        context: Context,
        url: String? = null,
        label: String? = null,
        customCss: String? = null,
        shortcutKey: String? = null,
        options: Bundle? = null,
    ): Boolean {
        if (!isBundled(context)) return false
        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                component = ComponentName(context.packageName, ActivityClass)
                putExtra(WebAppLaunchContract.EXTRA_URL, url)
                putExtra(WebAppLaunchContract.EXTRA_LABEL, label)
                putExtra(WebAppLaunchContract.EXTRA_CUSTOM_CSS, customCss)
                putExtra(WebAppLaunchContract.EXTRA_KEY, shortcutKey)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent, options)
            true
        }.getOrDefault(false)
    }
}
