// === TELOS_PENDING_REVIEW_START: ui_frozen_apps_style ===
package de.mm20.launcher2.badges.providers

import android.content.Context
import androidx.core.content.ContextCompat
import de.mm20.launcher2.badges.Badge
import de.mm20.launcher2.badges.BadgeIcon
import de.mm20.launcher2.badges.BadgeProvider
import de.mm20.launcher2.badges.MutableBadge
import de.mm20.launcher2.badges.R
import de.mm20.launcher2.preferences.FreezeMethod
import de.mm20.launcher2.preferences.FrozenAppStyle
import de.mm20.launcher2.preferences.freeze.FreezeSettings
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.search.Searchable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.combine
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SuspendedAppsBadgeProvider(private val context: Context) : BadgeProvider, KoinComponent {
    private val freezeSettings: FreezeSettings by inject()

    override fun getBadge(searchable: Searchable): Flow<Badge?> {
        if (searchable !is Application) return flowOf(null)

        val packageName = searchable.componentName.packageName

        return combine(
            freezeSettings.frozenAppStyle,
            freezeSettings.candidates
        ) { style, candidates ->
            if (style == FrozenAppStyle.Grayscale) {
                // Handled natively by ShapedLauncherIcon color matrix
                return@combine null
            }

            if (searchable.isSuspended) {
                // Solid Snowflake
                val drawable = ContextCompat.getDrawable(context, R.drawable.ac_unit_24px)
                if (drawable != null) {
                    MutableBadge(icon = BadgeIcon(drawable))
                } else {
                    MutableBadge(icon = BadgeIcon(R.drawable.ac_unit_24px))
                }
            } else if (candidates.contains(packageName)) {
                // Faint Snowflake
                val drawable = ContextCompat.getDrawable(context, R.drawable.ac_unit_24px)?.apply {
                    alpha = (0.4f * 255).toInt()
                }
                if (drawable != null) {
                    MutableBadge(icon = BadgeIcon(drawable))
                } else {
                    null
                }
            } else {
                null
            }
        }
    }
}
// === TELOS_PENDING_REVIEW_END: ui_frozen_apps_style ===
