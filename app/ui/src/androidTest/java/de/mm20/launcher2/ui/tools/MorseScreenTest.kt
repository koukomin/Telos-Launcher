package de.mm20.launcher2.ui.tools
import de.mm20.launcher2.i18n.R as I18nR

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextDirection
import org.junit.Assert.assertEquals
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.test.platform.app.InstrumentationRegistry
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.locals.LocalBackStack
import org.junit.Rule
import org.junit.Test

/** Screen-output lifecycle coverage; does not claim physical audio or flash verification. */
class MorseScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun greekTextCanDriveScreenAndTapStopsRepeatedSos() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Flashlight intentionally locks portrait. Settle that recreation before installing test content.
        compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        compose.waitUntil(5_000) {
            var portrait = false
            runCatching { compose.activityRule.scenario.onActivity {
                portrait = it.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
            } }
            portrait
        }
        compose.setContent {
            val stack = rememberNavBackStack(FlashlightRoute)
            CompositionLocalProvider(LocalBackStack provides stack, LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme(colorScheme = darkColorScheme()) { FlashlightScreen() }
            }
        }
        compose.onNodeWithTag("flashlight-controls").performScrollToNode(hasSetTextAction())
        compose.onNode(hasSetTextAction()).performTextReplacement("ΘΧΨΩ")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("-.-. ---- --.- .--").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("-.-. ---- --.- .--").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { readLayout ->
            val results = mutableListOf<TextLayoutResult>()
            readLayout(results)
            assertEquals(TextDirection.Ltr, results.single().layoutInput.style.textDirection)
        }
        compose.onNodeWithText(context.getString(I18nR.string.morse_light)).performScrollTo().performClick()
        // Emulator has no physical flash. Explicitly select screen even if a virtual camera advertises one.
        compose.onNodeWithTag("flashlight-controls").performScrollToNode(hasText(context.getString(I18nR.string.app_polish_screen_light)))
        compose.onAllNodesWithText(context.getString(I18nR.string.app_polish_screen_light)).filter(hasClickAction()).onFirst().performClick()
        compose.onNodeWithTag("flashlight-controls").performScrollToNode(hasText(context.getString(I18nR.string.app_polish_sos)))
        compose.onNodeWithText(context.getString(I18nR.string.app_polish_sos)).performClick()
        compose.onNodeWithText(context.getString(I18nR.string.app_polish_exit_light)).assertIsDisplayed().performClick()
        compose.onNodeWithTag("flashlight-controls").assertExists()
        compose.onNodeWithText(context.getString(I18nR.string.app_polish_exit_light)).assertDoesNotExist()
    }
}
