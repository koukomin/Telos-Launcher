package de.mm20.launcher2.contextprofiles

import android.app.NotificationManager
import android.content.Context
import android.provider.Settings
import androidx.core.content.getSystemService
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.ContextProfile
import de.mm20.launcher2.searchable.SavableSearchableRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Applies the "action" side of an active [ContextProfile]'s overrides that need to reach out to
 * system state - Do Not Disturb and screen brightness, which are forced while the profile is
 * active and released again once it isn't - plus one-shot app launching on activation.
 *
 * Started exactly once (guarded by [started]) from the composable that hosts the home screen,
 * matching [ContextProfileManager]'s own foreground-only evaluation scope: like every other
 * context profile effect, this only runs while the launcher process is alive and observing.
 */
class ContextProfileEffectsApplier internal constructor(
    private val context: Context,
    private val contextProfileManager: ContextProfileManager,
    private val permissionsManager: PermissionsManager,
    private val savableSearchableRepository: SavableSearchableRepository,
) {
    private val started = AtomicBoolean(false)

    private var dndForcedByUs = false
    private var savedBrightness: Int? = null
    private var previousActiveProfileId: String? = null

    suspend fun start() {
        if (!started.compareAndSet(false, true)) return
        contextProfileManager.activeProfile.collectLatest { profile ->
            applyDoNotDisturb(profile?.doNotDisturbOverride)
            applyBrightness(profile?.brightnessOverride)
            maybeLaunchApp(profile)
        }
    }

    private fun applyDoNotDisturb(override: Boolean?) {
        if (!permissionsManager.checkPermissionOnce(PermissionGroup.NotificationPolicy)) return
        val notificationManager = context.getSystemService<NotificationManager>() ?: return
        when (override) {
            true -> {
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                dndForcedByUs = true
            }

            false -> {
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                dndForcedByUs = true
            }

            null -> {
                if (dndForcedByUs) {
                    notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                    dndForcedByUs = false
                }
            }
        }
    }

    private fun applyBrightness(overridePercent: Int?) {
        if (!permissionsManager.checkPermissionOnce(PermissionGroup.WriteSettings)) return
        val resolver = context.contentResolver
        if (overridePercent != null) {
            if (savedBrightness == null) {
                savedBrightness = try {
                    Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS)
                } catch (e: Settings.SettingNotFoundException) {
                    null
                }
            }
            val value = (overridePercent.coerceIn(0, 100) * 255 / 100).coerceIn(1, 255)
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, value)
        } else {
            val original = savedBrightness ?: return
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, original)
            savedBrightness = null
        }
    }

    private suspend fun maybeLaunchApp(profile: ContextProfile?) {
        val justActivated = profile?.id != previousActiveProfileId
        previousActiveProfileId = profile?.id
        val key = profile?.launchAppOverride ?: return
        if (!justActivated) return
        val searchable = savableSearchableRepository.getByKeys(listOf(key)).first().firstOrNull()
        searchable?.launch(context, null)
    }
}
