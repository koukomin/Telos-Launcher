package de.mm20.launcher2.ui.network.firewall

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** Compact, labeled and selectable network control with a full 48dp hit target. */
@Composable
internal fun NetworkRuleToggle(label: Int, allowed: Boolean, enabled: Boolean, icon: Int, blockedIcon: Int, onAllowed: (Boolean) -> Unit) {
    IconToggleButton(checked = allowed, enabled = enabled, onCheckedChange = onAllowed,
        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .background(if (allowed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.medium)) {
        Icon(painterResource(if (allowed) icon else blockedIcon), stringResource(label),
            tint = if (allowed) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer)
    }
}
