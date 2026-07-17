package de.mm20.launcher2.ui.component.preferences

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.Banner
import de.mm20.launcher2.ui.component.RestrictedSettingsBanner

@Composable
fun GuardedPreference(
    locked: Boolean,
    onUnlock: (() -> Unit)? = null,
    description: String,
    @DrawableRes icon: Int = R.drawable.lock_24px,
    unlockLabel: String = stringResource(R.string.grant_permission),
    /** Shows [RestrictedSettingsBanner] below the lock banner - pass true once the user has
     * already tried to grant this permission at least once and it's still not showing granted. */
    showRestrictedSettingsHint: Boolean = false,
    preference: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MaterialTheme.colorScheme.surfaceBright)
    ) {
        if (locked) {
            Banner(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 8.dp),
                icon = icon,
                text = description,
                primaryAction = if (onUnlock != null) {
                    {
                        Button(onClick = onUnlock) {
                            Text(text = unlockLabel)
                        }
                    }
                } else null
            )
            if (showRestrictedSettingsHint) {
                RestrictedSettingsBanner(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                )
            }
        }
        preference()
    }
}