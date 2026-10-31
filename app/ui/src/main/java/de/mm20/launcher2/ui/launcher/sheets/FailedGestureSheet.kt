package de.mm20.launcher2.ui.launcher.sheets
import de.mm20.launcher2.i18n.R as I18nR

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.preferences.GestureAction
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.DismissableBottomSheet
import de.mm20.launcher2.ui.component.MissingPermissionBanner
import de.mm20.launcher2.ui.component.RestrictedSettingsBanner
import de.mm20.launcher2.ui.launcher.scaffold.Gesture

data class FailedGesture(val gesture: Gesture, val action: GestureAction)

@Composable
fun FailedGestureSheet(
    failedGesture: FailedGesture?,
    onDismiss: () -> Unit,
) {

    DismissableBottomSheet(
        state = failedGesture,
        expanded = { it != null },
        onDismissRequest = onDismiss,
    ) {
        it ?: return@DismissableBottomSheet
        val viewModel: FailedGestureSheetVM = viewModel()
        val hasAttemptedPermissionRequest by viewModel.hasAttemptedPermissionRequest.collectAsState(false)

        val actionName = stringResource(when(it.action) {
            is GestureAction.Search -> I18nR.string.gesture_action_open_search
            is GestureAction.Notifications -> I18nR.string.gesture_action_notifications
            is GestureAction.ScreenLock -> I18nR.string.gesture_action_lock_screen
            is GestureAction.QuickSettings -> I18nR.string.gesture_action_quick_settings
            is GestureAction.Recents -> I18nR.string.gesture_action_recents
            is GestureAction.PowerMenu -> I18nR.string.gesture_action_power_menu
            is GestureAction.WebAppsPanel -> I18nR.string.gesture_action_web_apps_panel
            is GestureAction.HomeScreenMenu -> I18nR.string.gesture_action_home_screen_menu
            else -> I18nR.string.gesture_action_none
        })
        val gestureName = stringResource(when(it.gesture) {
            Gesture.DoubleTap -> I18nR.string.preference_gesture_double_tap
            Gesture.LongPress -> I18nR.string.preference_gesture_long_press
            Gesture.SwipeDown -> I18nR.string.preference_gesture_swipe_down
            Gesture.SwipeLeft -> I18nR.string.preference_gesture_swipe_left
            Gesture.SwipeRight -> I18nR.string.preference_gesture_swipe_right
            Gesture.SwipeUp -> I18nR.string.preference_gesture_swipe_up
            Gesture.HomeButton -> I18nR.string.preference_gesture_home_button
            Gesture.PinchIn -> I18nR.string.preference_gesture_pinch_in
            Gesture.PinchOut -> I18nR.string.preference_gesture_pinch_out
            Gesture.TwoFingerSwipeUp -> I18nR.string.preference_gesture_two_finger_swipe_up
            Gesture.TwoFingerSwipeDown -> I18nR.string.preference_gesture_two_finger_swipe_down
            else -> throw IllegalArgumentException("Unknown gesture: ${it.gesture}")
        })

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .navigationBarsPadding()
        ) {
            Text(
                stringResource(I18nR.string.gesture_failed_message, gestureName, actionName),
                style = MaterialTheme.typography.bodySmall
            )
            val context = LocalLifecycleOwner.current
            MissingPermissionBanner(
                modifier = Modifier.padding(vertical = 16.dp),
                text = stringResource(id = I18nR.string.missing_permission_accessibility_gesture_failed),
                secondaryAction = {
                    OutlinedButton(onClick = {
                        viewModel.disableGesture(it.gesture)
                        onDismiss()
                    }) {
                        Text(stringResource(I18nR.string.turn_off))
                    }
                },
                onClick = {
                    viewModel.requestPermission(context as AppCompatActivity)
                    onDismiss()
                })
            if (hasAttemptedPermissionRequest) {
                RestrictedSettingsBanner()
            }
        }
    }
}