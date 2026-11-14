package de.mm20.launcher2.ui.system

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import de.mm20.launcher2.i18n.R as I18nR
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FreezeDeskGridTest {
    @get:Rule val compose = createComposeRule()
    private val rows =
        (0..39).map {
            InstalledSystemRow(
                "test.app$it",
                "Application $it",
                "1 MB",
                frozen = it % 2 == 0,
                system = it % 3 == 0,
                running = it % 2 != 0,
                bloat = false,
            )
        }

    @Test
    fun selectingFrozenTileAndSwitchingToListPreservesActions() {
        var selected: String? = null
        val list = mutableStateOf(false)
        compose.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Box(Modifier.width(360.dp).height(640.dp)) {
                    FreezeDeskGrid(
                        rows,
                        list.value,
                        { selected = it.pkg },
                        header = {
                            FilterChip(
                                selected = list.value,
                                onClick = { list.value = !list.value },
                                label = { Text("List") },
                            )
                        },
                        icon = { Box(Modifier.size(72.dp)) },
                    )
                }
            }
        }
        compose
            .onNodeWithText("Application 0")
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    InstrumentationRegistry.getInstrumentation()
                        .targetContext
                        .getString(I18nR.string.hf_freeze_frozen),
                )
            )
        compose.onNodeWithText("Application 0").performClick()
        compose.runOnIdle { assertEquals("test.app0", selected) }
        compose.onNodeWithText("List").performClick()
        compose.onNodeWithText("Application 1").performClick()
        compose.runOnIdle { assertEquals("test.app1", selected) }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(40)
        compose.onNodeWithText("Application 39").assertIsDisplayed()
    }

    @Test
    fun shortWindowAtDoubleFontScaleCanReachLastAppAndHeader() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                MaterialTheme(colorScheme = lightColorScheme()) {
                    Box(Modifier.width(320.dp).height(280.dp)) {
                        FreezeDeskGrid(
                            rows,
                            false,
                            {},
                            header = { Text("Search and filters") },
                            icon = { Box(Modifier.size(72.dp)) },
                        )
                    }
                }
            }
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(40)
        compose.onNodeWithText("Application 39").assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.onNodeWithText("Search and filters").assertIsDisplayed()
    }
}
