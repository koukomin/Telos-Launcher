package de.mm20.launcher2.badges.providers

import de.mm20.launcher2.badges.Badge
import de.mm20.launcher2.badges.BadgeIcon
import de.mm20.launcher2.badges.BadgeProvider
import de.mm20.launcher2.badges.MutableBadge
import de.mm20.launcher2.badges.R
import de.mm20.launcher2.preferences.FreezeMethod
import de.mm20.launcher2.preferences.freeze.FreezeSettings
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.search.Searchable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Badges a frozen app with an hourglass (suspended, reversible) or a power-off icon (disabled) -
 * this reflects the *configured* freeze method for the package (FreezeSettings.freezeMethods),
 * not a live re-read of the OS suspended/enabled flags. The two only disagree if the user changes
 * the method setting for an already-frozen app without re-freezing it, which self-corrects the
 * next time it's frozen/unfrozen - not worth the much larger plumbing change (splitting
 * Application.isSuspended into a live suspended/disabled state across every implementer) that a
 * fully live-accurate badge would need.
 */
class SuspendedAppsBadgeProvider : BadgeProvider, KoinComponent {
    private val freezeSettings: FreezeSettings by inject()

    override fun getBadge(searchable: Searchable): Flow<Badge?> {
        if (searchable !is Application || !searchable.isSuspended) {
            return flowOf(null)
        }
        val packageName = searchable.componentName.packageName
        return freezeSettings.freezeMethods.map { methods ->
            val icon = when (methods[packageName] ?: FreezeMethod.Suspend) {
                FreezeMethod.Suspend -> R.drawable.hourglass_bottom_20px
                FreezeMethod.Disable -> R.drawable.power_settings_new_24px
            }
            MutableBadge(icon = BadgeIcon(icon))
        }
    }
}