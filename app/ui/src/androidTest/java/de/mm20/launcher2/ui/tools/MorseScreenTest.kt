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
import de.mm20.launcher2.ui.locals.LocalBackStack
import org.junit.Rule
import org.junit.Test

/** Screen-output lifecycle coverage; does not claim physical audio or flash verification. */
class MorseScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun waitUntilText(text: String, timeoutMs: Long = 5_000) {
        compose.waitUntil(timeoutMs) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
    }

    private fun waitUntilTag(tag: String, timeoutMs: Long = 5_000) {
        compose.waitUntil(timeoutMs) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
    }

    /** Wait until the node is displayed, then click after idle — avoids flaky touch injection. */
    private fun clickWhenReady(matcher: SemanticsMatcher, scrollInTag: String? = null) {
        compose.waitUntil(5_000) {
            compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
        if (scrollInTag != null) {
            compose.onNodeWithTag(scrollInTag).performScrollToNode(matcher)
            compose.waitForIdle()
        }
        val node = compose.onNode(matcher)
        node.assertIsDisplayed()
        // Retry once if the first inject fails (animation / sheet settle on emulator).
        try {
            node.performClick()
        } catch (_: AssertionError) {
            compose.waitForIdle()
            compose.waitUntil(5_000) {
                compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNode(matcher).assertIsDisplayed().performClick()
        }
        compose.waitForIdle()
    }

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
        compose.waitForIdle()

        // Emulator has no physical flash. Pick screen light on the main surface first.
        val screenLight = context.getString(I18nR.string.app_polish_screen_light)
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText(screenLight).filter(hasClickAction()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
        compose.onAllNodesWithText(screenLight)
            .filter(hasClickAction())
            .onFirst()
            .assertIsDisplayed()
            .performClick()
        compose.waitForIdle()

        // Morse / SOS live in the Advanced ("More") sheet after Cyber-Tech chrome.
        clickWhenReady(hasText(context.getString(I18nR.string.hc_more)))
        waitUntilTag("morse-input")
        compose.onNodeWithTag("morse-input").assertIsDisplayed().performTextReplacement("ΘΧΨΩ")
        waitUntilText("-.-. ---- --.- .--")
        compose.onNodeWithText("-.-. ---- --.- .--").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { readLayout ->
            val results = mutableListOf<TextLayoutResult>()
            readLayout(results)
            assertEquals(TextDirection.Ltr, results.single().layoutInput.style.textDirection)
        }
        clickWhenReady(hasText(context.getString(I18nR.string.morse_light)))
        clickWhenReady(
            matcher = hasText(context.getString(I18nR.string.app_polish_sos)),
            scrollInTag = "flashlight-advanced",
        )
        clickWhenReady(hasText(context.getString(I18nR.string.app_polish_exit_light)))
        compose.onNodeWithTag("flashlight-controls").assertExists()
        compose.onNodeWithText(context.getString(I18nR.string.app_polish_exit_light)).assertDoesNotExist()
    }
}
