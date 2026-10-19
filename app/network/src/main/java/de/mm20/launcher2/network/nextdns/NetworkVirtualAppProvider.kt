package de.mm20.launcher2.network.nextdns

import android.content.ComponentName
import android.content.Context
import android.os.UserHandle
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.icons.StaticLauncherIcon
import de.mm20.launcher2.icons.TintedIconLayer
import de.mm20.launcher2.icons.ColorLayer
import androidx.core.content.ContextCompat
import de.mm20.launcher2.base.R
import android.content.Intent
import de.mm20.launcher2.network.nextdns.ui.NextDnsEditorActivity

class NetworkVirtualAppProvider(private val context: Context) : de.mm20.launcher2.search.VirtualAppProvider {
    override fun getVirtualApps(): List<Application> = listOf(NextDnsVirtualApp(context))
}

class NextDnsVirtualApp(private val context: Context) : Application {
    override val componentName: ComponentName
        get() = ComponentName(context, NextDnsEditorActivity::class.java)

    override val isSuspended: Boolean
        get() = false

    override val isPrivate: Boolean
        get() = false

    override val keywords: List<String>
        get() = listOf("nextdns", "dns", "network", "editor")

    override val user: UserHandle
        get() = UserHandle.SYSTEM

    override val versionName: String?
        get() = "1.0"

    override fun uninstall(context: Context) {
        // not uninstallable
    }

    override fun openAppDetails(context: Context) {
        // launch our editor
        val i = Intent(context, NextDnsEditorActivity::class.java)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(i)
    }

    override val canUninstall: Boolean
        get() = false

    override val canShareApk: Boolean
        get() = false
}
