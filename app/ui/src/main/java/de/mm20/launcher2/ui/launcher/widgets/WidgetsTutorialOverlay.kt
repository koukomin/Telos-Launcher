package de.mm20.launcher2.ui.launcher.widgets

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import de.mm20.launcher2.ui.R

private val steps = listOf(
    R.string.widgets_tutorial_add_title to R.string.widgets_tutorial_add_text,
    R.string.widgets_tutorial_move_title to R.string.widgets_tutorial_move_text,
    R.string.widgets_tutorial_resize_title to R.string.widgets_tutorial_resize_text,
    R.string.widgets_tutorial_stack_title to R.string.widgets_tutorial_stack_text,
)

/**
 * One-time, sequential coach-mark shown the first time the widgets screen appears, explaining
 * add/move/resize/stack. Finishing the last step or tapping Skip both count as "seen" - there's
 * no separate "don't show again" checkbox since there'd be nothing else to do with this dialog
 * once dismissed anyway. Can be re-triggered from Settings > Homescreen > Widgets ("Reset
 * tutorial").
 */
@Composable
fun WidgetsTutorialOverlay(onFinished: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val (titleRes, textRes) = steps[step]

    AlertDialog(
        onDismissRequest = onFinished,
        title = { Text(stringResource(titleRes)) },
        text = { Text(stringResource(textRes)) },
        confirmButton = {
            TextButton(onClick = {
                if (step < steps.lastIndex) step++ else onFinished()
            }) {
                Text(
                    stringResource(
                        if (step < steps.lastIndex) R.string.widgets_tutorial_next
                        else R.string.widgets_tutorial_done
                    )
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onFinished) {
                Text(stringResource(R.string.widgets_tutorial_skip))
            }
        }
    )
}
