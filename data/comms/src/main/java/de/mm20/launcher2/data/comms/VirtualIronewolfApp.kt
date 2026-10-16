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
import de.mm20.launcher2.webapp.IronewolfRuntime

/** Registered only when the actual, private Fenix host is bundled in this APK. */
internal class VirtualIronewolfApp(private val context: Context) : Application {
    override val key = IronewolfRuntime.Key
    override val domain = Domain
    override val label get() = TelosAppLabels.forDomain(context, Domain)
    override val labelOverride: String? = null
    override val keywords = TelosAppKeywords.forDomain(Domain)
    override val score = ResultScore.Unspecified
    override val componentName = ComponentName(context.packageName, IronewolfRuntime.ActivityClass)
    override val isSuspended = false
    override val user = Process.myUserHandle()
    override val versionName: String? = null
    override val canUninstall = false
    override val canShareApk = false
    override fun uninstall(context: Context) = Unit
    override fun openAppDetails(context: Context) = Unit
    override fun overrideLabel(label: String): SavableSearchable = this
    override fun launch(context: Context, options: Bundle?) =
        IronewolfRuntime.launch(context, label = label, options = options)

    override suspend fun loadIcon(context: Context, size: Int, themed: Boolean): StaticLauncherIcon? {
        val foreground = ContextCompat.getDrawable(context, de.mm20.launcher2.base.R.drawable.ic_ironewolf_fg)
            ?: return null
        return StaticLauncherIcon(
            foregroundLayer = StaticIconLayer(foreground, 1f),
            backgroundLayer = StaticIconLayer(ColorDrawable(0xFF492B72.toInt()), 1f),
        )
    }

    override fun getSerializer() = VirtualAppSerializer()

    companion object { const val Domain = IronewolfRuntime.Domain }
}
