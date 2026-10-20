package de.mm20.launcher2.ui.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import de.mm20.launcher2.ui.component.AdaptiveNavigationBar
import de.mm20.launcher2.ui.component.AdaptiveNavigationItem
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import androidx.compose.runtime.mutableStateOf
import android.graphics.Bitmap
import java.io.File
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.component.preferences.Preference
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Instrumented geometry test, not a substitute for screenshot/TalkBack review. */
class PreferenceAdaptiveTest {
    @get:Rule val compose = createComposeRule()

    @Test fun controlsNeverOverlapLargeWrappedTitles() {
        val width = mutableFloatStateOf(320f)
        val scale = mutableFloatStateOf(1f)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale.floatValue)) {
                MaterialTheme {
                    Box(Modifier.width(width.floatValue.dp)) {
                        Preference(
                            title = { Text("A long setting title that must wrap without losing its control", Modifier.testTag("title")) },
                            controls = { Text("Control", Modifier.testTag("control")) },
                        )
                    }
                }
            }
        }
        // Width is capped by the device window; compact cases run on every phone.
        for (w in listOf(240f, 320f)) for (s in listOf(0.85f, 1f, 1.3f, 2f)) {
            compose.runOnIdle { width.floatValue = w; scale.floatValue = s }
            val title = compose.onNodeWithTag("title", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val control = compose.onNodeWithTag("control", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue("$w dp / $s", control.top >= title.bottom || control.left >= title.right)
            assertTrue(title.height > 0 && control.height > 0)
        }
    }
    @Test fun settingHasOneLabeledToggleAndDisabledState() {
        val checked = mutableStateOf(false)
        val enabled = mutableStateOf(true)
        compose.setContent {
            MaterialTheme {
                SwitchPreference(title = "Setting", value = checked.value, enabled = enabled.value,
                    onValueChanged = { checked.value = it })
            }
        }
        compose.onNodeWithText("Setting").assertIsOff().performClick().assertIsOn()
        compose.runOnIdle { enabled.value = false }
        compose.onNodeWithText("Setting").assertIsNotEnabled().assertIsOn()
    }

    @Test fun navigationLabelsGrowAndSelectedStateIsExposed() {
        val scale = mutableFloatStateOf(1f)
        val selected = mutableStateOf(0)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale.floatValue)) {
                MaterialTheme {
                    Box(Modifier.width(320.dp)) {
                        AdaptiveNavigationBar(Modifier.testTag("bar")) {
                            listOf("Recent calls", "All contacts", "Text messages").forEachIndexed { index, label ->
                                AdaptiveNavigationItem(selected.value == index, { selected.value = index },
                                    icon = { Text("•") }, label = { Text(label, Modifier.testTag("label$index")) })
                            }
                        }
                    }
                }
            }
        }
        for (s in listOf(0.85f, 1f, 1.3f, 2f)) {
            compose.runOnIdle { scale.floatValue = s }
            val bar = compose.onNodeWithTag("bar").fetchSemanticsNode().boundsInRoot
            for (index in 0..2) {
                val label = compose.onNodeWithTag("label$index", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                assertTrue("Label $index / $s", label.bottom <= bar.bottom && label.right <= bar.right)
            }
            val output = File(InstrumentationRegistry.getInstrumentation().targetContext.externalCacheDir, "adaptive-navigation-$s.png")
            output.outputStream().use { stream ->
                compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
        }
        compose.onNodeWithText("All contacts").performClick().assertIsSelected()
    }

}
