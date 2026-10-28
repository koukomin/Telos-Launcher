package de.mm20.launcher2.ui.camera

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.foundation.layout.requiredSize
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
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import de.mm20.launcher2.ui.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class DocumentScannerSurfaceTest {
    @get:Rule val compose = createComposeRule()
    @Test fun documentControlsRemainReachableInShortLandscapeAndBothThemes() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val store = ViewModelStore()
        lateinit var vm: DocumentScannerViewModel
        instrumentation.runOnMainSync { vm = DocumentScannerViewModel(application, SavedStateHandle()); store.put("scan", vm) }
        try {
            compose.waitUntil(10_000) { !vm.busy.value }
            val input = File.createTempFile("scan-ui-", ".png", application.cacheDir)
            try {
                val bitmap = Bitmap.createBitmap(200,400,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
                try { input.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) } } finally { bitmap.recycle() }
                instrumentation.runOnMainSync { vm.addImages(listOf(Uri.fromFile(input))) }
                compose.waitUntil(10_000) { !vm.busy.value && vm.pages.value.size == 1 }
            } finally { input.delete() }
            val wide = mutableStateOf(false); val light = mutableStateOf(false)
            var captures = 0
            compose.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density,2f)) {
                    MaterialTheme(colorScheme = if(light.value) lightColorScheme() else darkColorScheme()) {
                        DocumentScannerDialog(vm, {}, { captures++ }, Modifier.requiredSize(if(wide.value) 720.dp else 320.dp, if(wide.value) 420.dp else 720.dp).testTag("scanner-dialog"))
                    }
                }
            }
            val context = instrumentation.targetContext
            val title = context.getString(R.string.camera_scan_pdf)
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            compose.onNodeWithText(title).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(2f, layouts.single().layoutInput.density.fontScale, 0.01f)
            val capture = context.getString(R.string.camera_scan_add_page)
            val save = context.getString(R.string.pdft_save_as)
            val page = context.getString(R.string.hc_page_number,1)
            for (isLight in listOf(false,true)) for (isWide in listOf(false,true)) {
                compose.runOnIdle { light.value = isLight; wide.value = isWide }
                compose.onNode(hasContentDescription(capture) or hasText(capture)).assertIsDisplayed().performClick()
                compose.onNode(hasContentDescription(save) or hasText(save)).assertIsDisplayed()
                compose.onNodeWithContentDescription(page).performScrollTo().assertIsDisplayed()
                val screenshot = compose.onNodeWithTag("scanner-dialog").captureToImage().asAndroidBitmap()
                val dir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES), "TelosUiValidation").apply { mkdirs() }
                File(dir,"scanner-font2-${if(isLight) "light" else "dark"}-${if(isWide) "wide" else "compact"}.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG,100,it) }
            }
            compose.runOnIdle { assertEquals(4,captures) }
        } finally { instrumentation.runOnMainSync { store.clear() } }
    }
}
