package de.mm20.launcher2.ui.settings.applock

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.ListPreferenceItem

/** Retention options offered for intruder photo auto-delete, in days. The longest option (2
 * years) matches [de.mm20.launcher2.preferences.applock.INTRUDER_PHOTO_MAX_RETENTION_DAYS], the
 * hard cap enforced in [de.mm20.launcher2.preferences.applock.AppLockSettings.setIntruderPhotoRetentionDays]
 * itself - this list can't offer anything past it. */
@Composable
fun intruderPhotoRetentionOptions(): List<ListPreferenceItem<Int>> = listOf(
    stringResource(R.string.intruder_photo_retention_7d) to 7,
    stringResource(R.string.intruder_photo_retention_30d) to 30,
    stringResource(R.string.intruder_photo_retention_90d) to 90,
    stringResource(R.string.intruder_photo_retention_1y) to 365,
    stringResource(R.string.intruder_photo_retention_2y) to 730,
)
