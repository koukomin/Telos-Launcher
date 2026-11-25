package de.mm20.launcher2.ui.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics

/**
 * Accessibility Helper Modifier for TalkBack Screen Reader tree merging and compliance.
 */
fun Modifier.telosSemantics(
    label: String,
    actionDescription: String? = null,
    onClickAction: (() -> Boolean)? = null,
): Modifier = this.semantics(mergeDescendants = true) {
    contentDescription = label
    if (onClickAction != null) {
        onClick(label = actionDescription, action = onClickAction)
    }
}
