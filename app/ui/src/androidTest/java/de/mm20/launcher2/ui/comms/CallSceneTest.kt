package de.mm20.launcher2.ui.comms
import de.mm20.launcher2.i18n.R as I18nR

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
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
import de.mm20.launcher2.ui.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Real call components, synthetic photo and state only: no carrier call or phone number is sent. */
class CallSceneTest {
    @get:Rule val compose = createComposeRule()

    @Test fun ringingToActivePhotoAndControlsSurviveFontThemeAndWindowChanges() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val photo = File(context.cacheDir, "call-scene-test.png")
        val bitmap = Bitmap.createBitmap(600, 800, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(android.graphics.Color.rgb(40, 110, 140))
            val paint = Paint().apply { color = android.graphics.Color.rgb(235, 190, 125) }
            drawCircle(300f, 280f, 125f, paint)
            drawOval(100f, 410f, 500f, 820f, paint)
        }
        photo.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val incoming = mutableStateOf(true)
        val outgoing = mutableStateOf(false)
        val hasPhoto = mutableStateOf(true)
        val light = mutableStateOf(false)
        val large = mutableStateOf(false)
        val wide = mutableStateOf(false)
        var answers = 0; var rejects = 0; var hangups = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (large.value) 2f else 1f)) {
                MaterialTheme(colorScheme = if (light.value) lightColorScheme() else darkColorScheme()) {
                    Box(Modifier.requiredSize(if (wide.value) 720.dp else 320.dp, if (wide.value) 420.dp else 850.dp)) {
                        val state = InCallUiState(hasCall = true, incoming = incoming.value, connecting = outgoing.value, active = !incoming.value && !outgoing.value,
                            name = "Δοκιμή επαφής", photoUri = if (hasPhoto.value) photo.absolutePath else null)
                        CallScreenContent(state, state.name!!, if (incoming.value) "Εισερχόμενη κλήση" else if (outgoing.value) "Κλήση…" else "00:01",
                            onHangup = { hangups++ },
                            incomingActions = { IncomingCallActions("", "buttons", onAnswer = { answers++; incoming.value = false }, onReject = { rejects++ }) },
                            activeControls = { Text("Χειριστήρια κλήσης") },
                        )
                    }
                }
            }
        }
        compose.onNodeWithContentDescription(context.getString(I18nR.string.comms_answer)).assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription(context.getString(I18nR.string.comms_hangup)).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, answers); assertEquals(1, hangups); assertEquals(0, rejects) }
        for (mode in listOf("incoming", "outgoing", "active")) for (isLight in listOf(true, false)) for (font2 in listOf(false, true)) for (landscape in listOf(false, true)) {
            compose.runOnIdle {
                incoming.value = mode == "incoming"; outgoing.value = mode == "outgoing"; light.value = isLight; large.value = font2; wide.value = landscape
                hasPhoto.value = !font2 // Also exercise the initials path under larger text.
            }
            if (!font2) compose.waitUntil(5_000) { compose.onAllNodesWithTag("caller-photo-loaded").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription(context.getString(if (mode == "incoming") I18nR.string.comms_answer else I18nR.string.comms_hangup)).assertIsDisplayed()
            screenshot("call-$mode-${if (isLight) "light" else "dark"}-font${if (font2) 2 else 1}-${if (landscape) "wide" else "portrait"}.png")
        }
        photo.delete()
    }

    private fun screenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = compose.onNodeWithTag("call-scene").captureToImage().asAndroidBitmap()
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
