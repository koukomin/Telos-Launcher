package de.mm20.launcher2.ui.component

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.ui.R

/**
 * Explains Android 13+'s "Restricted Settings" mechanism, which silently blocks the first
 * Accessibility/Notification-listener permission grant attempt for apps installed outside an app
 * store, with no error shown to the app - the only way to detect it is "the user tried, and it's
 * still not granted". Points at the exact, official steps to allow it (App info -> overflow menu
 * -> Allow restricted settings); this is guidance for the platform's own flow, not a bypass.
 */
@Composable
fun RestrictedSettingsBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Banner(
        modifier = modifier,
        text = stringResource(R.string.restricted_settings_hint),
        icon = R.drawable.info_24px,
        primaryAction = {
            OutlinedButton(
                modifier = Modifier.padding(start = 8.dp),
                onClick = {
                    context.tryStartActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            "package:${context.packageName}".toUri(),
                        )
                    )
                }
            ) {
                Text(stringResource(R.string.restricted_settings_hint_action))
            }
        }
    )
}
