package de.mm20.launcher2.ui.component.preferences

import androidx.compose.runtime.Composable

/**
 * Compact DSL extension helpers for Preference items to minimize UI token bloat.
 */

@Composable
fun SwitchPref(
    title: String,
    summary: String? = null,
    value: Boolean,
    onValueChanged: (Boolean) -> Unit,
    icon: Int? = null,
    enabled: Boolean = true,
) {
    SwitchPreference(
        title = title,
        summary = summary,
        value = value,
        onValueChanged = onValueChanged,
        icon = icon,
        enabled = enabled,
    )
}

@Composable
fun <T> ListPref(
    title: String,
    summary: String? = null,
    value: T,
    items: List<ListPreferenceItem<T>>,
    onValueChanged: (T) -> Unit,
    enabled: Boolean = true,
) {
    ListPreference(
        title = title,
        summary = summary,
        value = value,
        items = items,
        onValueChanged = onValueChanged,
        enabled = enabled,
    )
}
