package de.mm20.launcher2.ui.tools

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.test.platform.app.InstrumentationRegistry
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.locals.LocalBackStack
import org.junit.Rule
import org.junit.Test

/** Screen-output lifecycle coverage; does not claim physical audio or flash verification. */
class MorseScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun greekTextCanDriveScreenAndTapStopsRepeatedSos() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent {
            val stack = rememberNavBackStack(FlashlightRoute)
            CompositionLocalProvider(LocalBackStack provides stack) {
                MaterialTheme(colorScheme = darkColorScheme()) { FlashlightScreen() }
            }
        }
        compose.onNodeWithTag("flashlight-controls").performScrollToNode(hasSetTextAction())
        compose.onNode(hasSetTextAction()).performTextReplacement("ΘΧΨΩ")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("-.-. ---- --.- .--").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(context.getString(R.string.morse_light)).performClick()
        // Emulator has no physical flash. Explicitly select screen even if a virtual camera advertises one.
        compose.onNodeWithTag("flashlight-controls").performScrollToNode(hasText(context.getString(R.string.app_polish_screen_light)))
        compose.onAllNodesWithText(context.getString(R.string.app_polish_screen_light)).filter(hasClickAction()).onFirst().performClick()
        compose.onNodeWithTag("flashlight-controls").performScrollToNode(hasText(context.getString(R.string.app_polish_sos)))
        compose.onNodeWithText(context.getString(R.string.app_polish_sos)).performClick()
        compose.onNodeWithText(context.getString(R.string.app_polish_exit_light)).assertIsDisplayed().performClick()
        compose.onNodeWithTag("flashlight-controls").assertExists()
        compose.onNodeWithText(context.getString(R.string.app_polish_exit_light)).assertDoesNotExist()
    }
}
