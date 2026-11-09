package de.mm20.launcher2.search

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.content.ContextCompat
import de.mm20.launcher2.applock.SettingsDeepLinkContract
import de.mm20.launcher2.base.R
import de.mm20.launcher2.icons.ColorLayer
import de.mm20.launcher2.icons.StaticLauncherIcon
import de.mm20.launcher2.icons.TintedIconLayer
import de.mm20.launcher2.ktx.tryStartActivity

enum class SettingsSearchSource {
    Android,
    Telos,
    TelosApp,
}

data class SettingsSearchItem(
    override val label: String,
    val subtitle: String,
    val source: SettingsSearchSource,
    val androidAction: String? = null,
    val telosRoute: String? = null,
    val iconRes: Int,
    val keywords: List<String> = emptyList(),
    override val score: ResultScore = ResultScore.Unspecified,
    override val labelOverride: String? = null,
) : SavableSearchable {

    override val domain: String = Domain
    override val key: String = "$domain://${source.name}/${androidAction ?: telosRoute ?: label}"
    override val preferDetailsOverLaunch: Boolean = false

    override fun overrideLabel(label: String): SavableSearchable = copy(labelOverride = label)

    override fun launch(context: Context, options: Bundle?): Boolean {
        val intent = when (source) {
            SettingsSearchSource.Android -> Intent(androidAction).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            SettingsSearchSource.Telos, SettingsSearchSource.TelosApp -> Intent()
                .setClassName(context.packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
                .putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, telosRoute)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return context.tryStartActivity(intent, options)
    }

    override fun getPlaceholderIcon(context: Context): StaticLauncherIcon {
        val drawable = ContextCompat.getDrawable(context, iconRes)
            ?: ContextCompat.getDrawable(context, R.drawable.settings_24px)!!
        val color = when (source) {
            SettingsSearchSource.Android -> 0xFF607D8B.toInt()
            SettingsSearchSource.Telos -> 0xFF5C6BC0.toInt()
            SettingsSearchSource.TelosApp -> 0xFF26A69A.toInt()
        }
        return StaticLauncherIcon(
            foregroundLayer = TintedIconLayer(icon = drawable, scale = 0.62f, color = color),
            backgroundLayer = ColorLayer(color),
        )
    }

    override fun getSerializer(): SearchableSerializer = NullSerializer()

    companion object {
        const val Domain = "settings"
    }
}
