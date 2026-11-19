package de.mm20.launcher2.freeze.providers

import android.content.ComponentName
import android.content.Intent
import android.net.Uri

/** The documented Island activity protocol, shared by direct launches and notifications. */
object IslandIntentContract {
    const val PACKAGE = "com.oasisfeng.island"
    const val FREEZE = "com.oasisfeng.island.action.FREEZE"
    const val UNFREEZE = "com.oasisfeng.island.action.UNFREEZE"

    fun create(component: ComponentName, packageNames: List<String>, suspended: Boolean): Intent? {
        if (component.packageName != PACKAGE || packageNames.isEmpty()) return null
        return Intent(if (suspended) FREEZE else UNFREEZE)
            .setComponent(component)
            .setPackage(PACKAGE)
            .setData(Uri.parse("packages:" + packageNames.joinToString(",")))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
