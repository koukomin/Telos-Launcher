package de.mm20.launcher2.ui.media

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import de.mm20.launcher2.comms.telephony.InCallUiState
import de.mm20.launcher2.comms.youtube.YouTubeEntry
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.comms.CallScreen
import de.mm20.launcher2.ui.media.youtube.FlowMediaCard
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Touch/geometry checks with actual components. Does not simulate a carrier or privileged service. */
class MediaRedesignTest {
    @get:Rule val compose = createComposeRule()

    @Test fun terminationReceivesTouchAtLargeFontInAShortWindow() {
        val state = MutableStateFlow(InCallUiState(hasCall = true, active = true, number = "123", name = "Δοκιμή κλήσης"))
        var clicks = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    Box(Modifier.requiredSize(320.dp, 420.dp)) {
                        CallScreen(onFinished = {}, stateSource = state, onHangup = { clicks++ })
                    }
                }
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.onNodeWithContentDescription(context.getString(R.string.comms_hangup))
            .assertIsDisplayed().performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, clicks) }
        screenshot("media-call-termination-font2.png")
    }

    @Test fun listeningAndWatchCardsRetainPlayAndArtistRadioActions() {
        val music = mutableStateOf(true)
        val large = mutableStateOf(false)
        val light = mutableStateOf(false)
        val entry = YouTubeEntry("https://www.youtube.com/watch?v=abcdefghijk", "Μουσική και βίντεο με μεγάλο τίτλο για έλεγχο προσαρμογής", "Καλλιτέχνης", null, 125)
        var played = 0; var radio = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (large.value) 2f else 1f)) {
                MaterialTheme(colorScheme = if (light.value) lightColorScheme() else darkColorScheme()) {
                    Box(Modifier.requiredSize(320.dp, 650.dp)) {
                        FlowMediaCard(entry, music.value, false, { played++ }, {}, {}, { radio++ }, {}, {})
                    }
                }
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.onNodeWithText(entry.title).performTouchInput { click() }
        compose.onNodeWithContentDescription(context.getString(R.string.au10_music_more)).performClick()
        compose.onNodeWithText(context.getString(R.string.telos_youtube_artist_radio)).performClick()
        compose.runOnIdle { assertEquals(1, played); assertEquals(1, radio) }
        for (listen in listOf(true, false)) for (largeFont in listOf(false, true)) for (isLight in listOf(false, true)) {
            compose.runOnIdle { music.value = listen; large.value = largeFont; light.value = isLight }
            compose.onNodeWithContentDescription(context.getString(R.string.au10_music_more)).assertIsDisplayed()
            screenshot("media-${if (listen) "listen" else "watch"}-font${if (largeFont) 2 else 1}-${if (isLight) "light" else "dark"}.png")
        }
    }

    private fun screenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
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
