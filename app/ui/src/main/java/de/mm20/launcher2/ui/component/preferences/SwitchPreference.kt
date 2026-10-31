package de.mm20.launcher2.ui.component.preferences
import de.mm20.launcher2.base.R as BaseR

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import de.mm20.launcher2.ui.R

@Composable
fun SwitchPreference(
    title: String,
    @DrawableRes icon: Int? = null,
    iconPadding: Boolean = icon != null,
    summary: String? = null,
    value: Boolean,
    onValueChanged: (Boolean) -> Unit,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surfaceBright,
) {
    Preference(
        title = title,
        icon = icon,
        iconPadding = iconPadding,
        summary = summary,
        enabled = enabled,
        toggleValue = value,
        onClick = {
            onValueChanged(!value)
        },
        controls = {
            Switch(
                enabled = enabled, checked = value, onCheckedChange = null,
                thumbContent = {
                    Icon(
                        painterResource(if (value) BaseR.drawable.check_20px else BaseR.drawable.close_20px),
                        modifier = Modifier.size(SwitchDefaults.IconSize),
                        contentDescription = null,
                    )
                }
            )
        },
        containerColor = containerColor,
    )
}
