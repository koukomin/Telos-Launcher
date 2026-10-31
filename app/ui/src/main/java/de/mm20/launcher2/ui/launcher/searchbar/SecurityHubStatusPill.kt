package de.mm20.launcher2.ui.launcher.searchbar

import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.mm20.launcher2.appmanagement.shizuku.TelosShizukuManager
import de.mm20.launcher2.ui.settings.SettingsActivity

@Composable
fun SecurityHubStatusPill() {
    val context = LocalContext.current
    val status by TelosShizukuManager.status.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { TelosShizukuManager.refreshStatus(context) }
    val active = status.binderAlive || status.internalUserService
    Box(
        modifier = Modifier
            .padding(end = 4.dp)
            .size(40.dp)
            .clip(CircleShape)
            .clickable {
                context.startActivity(
                    Intent(context, SettingsActivity::class.java).apply {
                        putExtra(SettingsActivity.EXTRA_ROUTE, SettingsActivity.ROUTE_SECURITY_HUB)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(BaseR.drawable.encrypted_24px),
            contentDescription = stringResource(I18nR.string.security_hub_title),
            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(6.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    if (active) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.error,
                ),
        )
    }
}
