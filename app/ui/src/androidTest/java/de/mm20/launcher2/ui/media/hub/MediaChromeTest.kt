package de.mm20.launcher2.ui.media.hub

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.media.stremio.StremioBrowseControls
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MediaChromeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun oneOverflowSourceSelectorAndCompactSearchWorkAtLargeFonts() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var source by mutableStateOf("Stremio Fork")
        var sourceMenu by mutableStateOf(false)
        var more by mutableStateOf(false)
        var query by mutableStateOf("")
        var expanded by mutableStateOf(false)
        var tab by mutableStateOf(0)
        var large by mutableStateOf(false)
        var light by mutableStateOf(false)
        var wide by mutableStateOf(false)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (large) 2f else 1f)) {
                MaterialTheme(colorScheme = if (light) lightColorScheme() else darkColorScheme()) {
                    Surface(Modifier.requiredSize(if (wide) 720.dp else 320.dp, if (wide) 420.dp else 800.dp).testTag("media-chrome")) {
                        Column {
                            MediaToolbarHeader(source, {}, { sourceMenu = true }, actions = {
                                IconButton(onClick = {}) { Icon(painterResource(R.drawable.autorenew_24px), context.getString(R.string.hc_refresh)) }
                                IconButton(onClick = {}) { Icon(painterResource(R.drawable.settings_24px), context.getString(R.string.settings)) }
                                IconButton(onClick = { more = true }) {
                                    Icon(painterResource(R.drawable.more_vert_24px), context.getString(R.string.hc_more))
                                }
                                DropdownMenu(more, { more = false }) {
                                    DropdownMenuItem(text = { Text(context.getString(R.string.settings)) }, onClick = { more = false })
                                }
                            }, sourceMenu = {
                                DropdownMenu(sourceMenu, { sourceMenu = false }) {
                                    DropdownMenuItem(text = { Text("TV") }, onClick = { source = "TV"; sourceMenu = false })
                                }
                            })
                            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                item { de.mm20.launcher2.ui.media.MediaFilterButton(true, {}, context.getString(R.string.au9_mediahub_space_music), R.drawable.music_note_24px) }
                                item { de.mm20.launcher2.ui.media.MediaFilterButton(false, {}, context.getString(R.string.hc_radio), R.drawable.ic_glyph_radio) }
                                item { de.mm20.launcher2.ui.media.MediaFilterButton(false, {}, context.getString(R.string.telos_youtube_music_label), R.drawable.play_arrow_24px) }
                            }
                            StremioBrowseControls(tab, { tab = it }, query, { query = it }, expanded, { expanded = it })
                            Text("Συνέχεια παρακολούθησης", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
                        }
                    }
                }
            }
        }
        val hint = context.getString(R.string.au22_stremio_search_hint)
        compose.onAllNodesWithContentDescription(context.getString(R.string.hc_more)).assertCountEquals(1)
        compose.onNodeWithContentDescription(hint).assertDoesNotExist()
        compose.onNodeWithContentDescription(context.getString(R.string.hc_search)).performClick()
        compose.onNodeWithContentDescription(hint).assertIsDisplayed().performTextInput("Δοκιμή")
        compose.runOnIdle { assertEquals("Δοκιμή", query) }
        compose.onAllNodesWithContentDescription(context.getString(R.string.hc_close)).assertCountEquals(1)
        val closeBounds = compose.onNodeWithContentDescription(context.getString(R.string.hc_close)).fetchSemanticsNode().boundsInRoot
        val inputBounds = compose.onNodeWithContentDescription(hint).fetchSemanticsNode().boundsInRoot
        assertTrue("Close must lead the input, not duplicate its clear action", closeBounds.right <= inputBounds.left + 1f)
        compose.onNodeWithContentDescription(context.getString(R.string.ts_clear)).assertIsDisplayed()
        compose.onAllNodesWithContentDescription(context.getString(R.string.hc_close)).onFirst().performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription(hint).assertDoesNotExist()
        compose.runOnIdle { assertEquals("", query) }
        compose.onNodeWithTag("media-source-selector").performClick()
        compose.onNodeWithText("TV").performClick()
        compose.runOnIdle { assertEquals("TV", source); source = "Stremio Fork" }
        compose.onNodeWithContentDescription(context.getString(R.string.hc_more)).performClick()
        compose.onNodeWithText(context.getString(R.string.settings)).assertIsDisplayed().performClick()
        for (font2 in listOf(false, true)) for (isLight in listOf(false, true)) for (landscape in listOf(false, true)) {
            compose.runOnIdle { large = font2; light = isLight; wide = landscape }
            compose.onAllNodesWithContentDescription(context.getString(R.string.hc_more)).assertCountEquals(1)
            val selector = compose.onNodeWithTag("media-source-selector").fetchSemanticsNode().boundsInRoot
            val action = compose.onNodeWithContentDescription(context.getString(R.string.hc_more)).fetchSemanticsNode().boundsInRoot
            assertTrue("Source selector overlaps overflow", selector.right <= action.left + 1f)
            compose.onNodeWithContentDescription(context.getString(R.string.hc_search)).assertIsDisplayed()
            compose.onNodeWithContentDescription(context.getString(R.string.hc_refresh)).assertIsDisplayed()
            compose.onNodeWithContentDescription(context.getString(R.string.settings)).assertIsDisplayed()
            screenshot("media-chrome-font${if (font2) 2 else 1}-${if (isLight) "light" else "dark"}-${if (landscape) "wide" else "portrait"}.png")
            compose.onNodeWithContentDescription(context.getString(R.string.hc_search)).performClick()
            compose.onNodeWithContentDescription(hint).assertIsDisplayed().performTextInput("Σύντομη αναζήτηση")
            screenshot("media-chrome-search-font${if (font2) 2 else 1}-${if (isLight) "light" else "dark"}-${if (landscape) "wide" else "portrait"}.png")
            compose.onAllNodesWithContentDescription(context.getString(R.string.hc_close)).onFirst().performClick()
            compose.waitForIdle()
        }
    }

    @Test fun translucentRadioRetainsTheParentToneAndContentColor() {
        val parent = androidx.compose.ui.graphics.Color(0.85f, 0.75f, 0.65f)
        var actual = androidx.compose.ui.graphics.Color.Unspecified
        var content = androidx.compose.ui.graphics.Color.Unspecified
        compose.setContent {
            MaterialTheme(colorScheme = lightColorScheme(surface = parent)) {
                de.mm20.launcher2.ui.comms.radio.RadioAppearance(
                    de.mm20.launcher2.comms.radio.RadioSettings.Values(theme = "system", translucent = true),
                ) {
                    actual = MaterialTheme.colorScheme.surface
                    content = LocalContentColor.current
                    Text("Radio")
                }
            }
        }
        compose.runOnIdle {
            assertEquals(parent.red, actual.red, 0.001f)
            assertEquals(parent.green, actual.green, 0.001f)
            assertEquals(parent.blue, actual.blue, 0.001f)
            assertEquals(0f, actual.alpha, 0.001f)
            assertEquals(lightColorScheme().onSurface, content)
        }
    }

    private fun screenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = compose.onNodeWithTag("media-chrome").captureToImage().asAndroidBitmap()
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name)
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/TelosUiValidation")
            put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = checkNotNull(context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
        checkNotNull(context.contentResolver.openOutputStream(uri)).use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        context.contentResolver.update(uri, android.content.ContentValues().apply { put(android.provider.MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
    }
}
