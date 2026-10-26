package de.mm20.launcher2.ui.media

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
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
import de.mm20.launcher2.ui.media.youtube.YouTubeMusicPlayerContent
import de.mm20.launcher2.ui.media.youtube.FlowMediaCard
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.android.ext.koin.androidContext
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Touch/geometry checks with actual components. Does not simulate a carrier or privileged service. */
class MediaRedesignTest {
    @get:Rule val compose = createComposeRule()

    @Test fun terminationReceivesTouchAtLargeFontInAShortWindow() {
        val state = MutableStateFlow(InCallUiState(hasCall = true, active = true, number = "123", name = "Δοκιμή κλήσης"))
        var clicks = 0
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent {
            org.koin.compose.KoinApplication(application = {
                androidContext(context)
                modules(de.mm20.launcher2.preferences.preferencesModule, org.koin.dsl.module {
                    single<de.mm20.launcher2.comms.repository.ContactDirectoryRepository> {
                        object : de.mm20.launcher2.comms.repository.ContactDirectoryRepository {
                            override fun observeContacts() = kotlinx.coroutines.flow.flowOf(emptyList<de.mm20.launcher2.comms.model.DialerContact>())
                            override suspend fun containsNumber(number: String) = false
                            override suspend fun findDuplicateGroups() = emptyList<List<de.mm20.launcher2.comms.model.DialerContact>>()
                            override suspend fun deleteContact(id: Long) = false
                            override suspend fun moveContactToAccount(contactId: Long, accountName: String?, accountType: String?) = false
                            override suspend fun mergeContactGroup(group: List<de.mm20.launcher2.comms.model.DialerContact>) = false
                            override suspend fun mergeAllDuplicateGroups() = 0
                            override suspend fun importVcf(content: String) = 0
                        }
                    }
                })
            }) {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    Box(Modifier.requiredSize(320.dp, 420.dp).testTag("media-scene")) {
                        CallScreen(onFinished = {}, stateSource = state, onHangup = { clicks++ })
                    }
                }
            }
        }
        }
        compose.onNodeWithContentDescription(context.getString(R.string.comms_hangup))
            .assertIsDisplayed().performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, clicks) }
        screenshot("media-call-termination-font2.png")
    }

    @Test fun listeningAndWatchCardsRetainPlayAndArtistRadioActions() {
        val music = mutableStateOf(true)
        val large = mutableStateOf(false)
        val light = mutableStateOf(false)
        val wide = mutableStateOf(false)
        val entry = YouTubeEntry("https://www.youtube.com/watch?v=abcdefghijk", "Μουσική και βίντεο με μεγάλο τίτλο για έλεγχο προσαρμογής", "Καλλιτέχνης", null, 125)
        var played = 0; var radio = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (large.value) 2f else 1f)) {
                MaterialTheme(colorScheme = if (light.value) lightColorScheme() else darkColorScheme()) {
                    Box(Modifier.requiredSize(if (wide.value) 720.dp else 320.dp, 850.dp).background(MaterialTheme.colorScheme.background).testTag("media-scene")) {
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
        for (listen in listOf(true, false)) for (largeFont in listOf(false, true)) for (isLight in listOf(false, true)) for (isWide in listOf(false, true)) {
            compose.runOnIdle { music.value = listen; large.value = largeFont; light.value = isLight; wide.value = isWide }
            compose.onNodeWithContentDescription(context.getString(R.string.au10_music_more)).assertIsDisplayed()
            screenshot("media-${if (listen) "listen" else "watch"}-font${if (largeFont) 2 else 1}-${if (isLight) "light" else "dark"}-${if (isWide) "wide" else "compact"}.png")
        }
    }

    @Test fun expandedMusicPlayerKeepsTransportAndQueueReachable() {
        val wide = mutableStateOf(false)
        val large = mutableStateOf(false)
        val entry = YouTubeEntry("https://www.youtube.com/watch?v=abcdefghijk", "Δοκιμή μουσικής με μεγάλο τίτλο", "Καλλιτέχνης", null, 180)
        var toggles = 0; var stopped = 0; var chosen = -1
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (large.value) 2f else 1f)) {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    Box(Modifier.requiredSize(if (wide.value) 720.dp else 320.dp, if (wide.value) 420.dp else 850.dp)
                        .background(MaterialTheme.colorScheme.background).testTag("media-scene")) {
                        YouTubeMusicPlayerContent(entry, listOf(entry, entry.copy(url = "https://www.youtube.com/watch?v=lmnopqrstuv")),
                            0, 30_000, 180_000, true, true, { toggles++ }, { stopped++ }, {}, { chosen = it })
                    }
                }
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.onNodeWithContentDescription(context.getString(R.string.hc_previous)).assertIsNotEnabled()
        compose.onNodeWithContentDescription(context.getString(R.string.hc_next)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.au_music_pause)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.hc_stop)).performClick()
        compose.runOnIdle { assertEquals(1, toggles); assertEquals(1, stopped); assertEquals(1, chosen) }
        for (landscape in listOf(false, true)) for (font2 in listOf(false, true)) {
            compose.runOnIdle { wide.value = landscape; large.value = font2 }
            compose.onNodeWithContentDescription(context.getString(R.string.hc_stop)).performScrollTo().assertIsDisplayed()
            screenshot("media-player-${if (landscape) "landscape" else "portrait"}-font${if (font2) 2 else 1}.png")
        }
    }

    private fun screenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = compose.onNodeWithTag("media-scene").captureToImage().asAndroidBitmap()
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name)
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/TelosUiValidation")
            put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = checkNotNull(context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
        checkNotNull(context.contentResolver.openOutputStream(uri)).use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        context.contentResolver.update(uri, android.content.ContentValues().apply { put(android.provider.MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        bitmap.recycle()
    }
}
