package de.mm20.launcher2.ui.system

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test

/** Actual embedded entry point, with no Shizuku grant or device AppOps mutations. */
class AppOpsSurfaceTest {
    @get:Rule val compose = createComposeRule()
    @Test fun embeddedAppOpsRemainsReadableInBothThemesAtLargeText() {
        val light = mutableStateOf(false)
        val wide = mutableStateOf(false)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                MaterialTheme(colorScheme = if (light.value) lightColorScheme() else darkColorScheme()) {
                    Box(Modifier.requiredSize(if(wide.value) 720.dp else 320.dp, if(wide.value) 420.dp else 820.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainer).testTag("embedded-appops")) {
                        dev.izumi.appopsnext.EmbeddedAppOpsScreen()
                    }
                }
            }
        }
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val browse = context.getString(dev.izumi.appopsnext.R.string.appopsnext_browse_by_app)
        compose.waitUntil(30_000) { compose.onAllNodesWithText(browse).fetchSemanticsNodes().isNotEmpty() }
        val search = context.getString(dev.izumi.appopsnext.R.string.appopsnext_app_list_search_label)
        compose.waitUntil(30_000) { compose.onAllNodesWithText(search).fetchSemanticsNodes().isNotEmpty() }
        for (isLight in listOf(false,true)) for (isWide in listOf(false,true)) {
            compose.runOnIdle { light.value = isLight; wide.value = isWide }
            compose.waitForIdle()
            val bitmap = compose.onNodeWithTag("embedded-appops").captureToImage().asAndroidBitmap()
            val dir = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES), "TelosUiValidation").apply { mkdirs() }
            java.io.File(dir,"appops-font2-${if(isLight) "light" else "dark"}-${if(isWide) "wide" else "compact"}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        }
        val settings = context.getString(dev.izumi.appopsnext.R.string.appopsnext_navigation_settings)
        compose.onNodeWithText(settings).performClick()
        compose.waitForIdle()
        val bitmap = compose.onNodeWithTag("embedded-appops").captureToImage().asAndroidBitmap()
        val dir = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES), "TelosUiValidation").apply { mkdirs() }
        java.io.File(dir,"appops-settings-font2-light-wide.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
}
