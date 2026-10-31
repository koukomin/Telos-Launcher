package de.mm20.launcher2.ui.layout
import de.mm20.launcher2.i18n.R as I18nR

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
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
import androidx.compose.ui.unit.sp
import de.mm20.launcher2.ui.component.preferences.Preference
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Instrumented geometry test, not a substitute for screenshot/TalkBack review. */
class PreferenceAdaptiveTest {
    @get:Rule val compose = createComposeRule()

    @Test fun settingsOpensWithLauncherRelativeAndUnspecifiedLineHeights() {
        val scale = mutableFloatStateOf(1f)
        val heightMode = androidx.compose.runtime.mutableIntStateOf(0)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent {
            val typography = de.mm20.launcher2.ui.theme.typography.typographyOf(
                de.mm20.launcher2.themes.typography.Typography(name = "Settings regression")
            )
            check(typography.titleLarge.lineHeight.isEm)
            val lineHeight = when (heightMode.intValue) {
                1 -> androidx.compose.ui.unit.TextUnit.Unspecified
                2 -> 28.sp
                else -> typography.titleLarge.lineHeight
            }
            val density = LocalDensity.current
            val stack = androidx.navigation3.runtime.rememberNavBackStack(
                de.mm20.launcher2.ui.settings.main.MainRoute
            )
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, scale.floatValue),
                de.mm20.launcher2.ui.locals.LocalBackStack provides stack,
            ) {
                MaterialTheme(colorScheme = darkColorScheme(), typography = typography.copy(
                    titleLarge = typography.titleLarge.copy(lineHeight = lineHeight)
                )) {
                    de.mm20.launcher2.ui.settings.main.MainSettingsScreen()
                }
            }
        }
        for (mode in 0..2) for (fontScale in listOf(0.85f, 1f, 1.3f, 2f)) {
            compose.runOnIdle { heightMode.intValue = mode; scale.floatValue = fontScale }
            compose.onNodeWithText(context.getString(I18nR.string.settings)).assertIsDisplayed()
            compose.onNodeWithText(context.getString(I18nR.string.preference_screen_appearance)).assertIsDisplayed()
            saveScreenshot("settings-line-height-$mode-$fontScale.png")
        }
    }

    @Test fun virtualNotificationsCarryTheirOwnIdentity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val domains = listOf("phone", "messages", "ermis", "clock", "network", "store", "radio", "music", "video", "youtube")
        for (app in domains) {
            val domain = "telos_${app}_app"
            val notification = de.mm20.launcher2.search.VirtualAppAppearance.decorate(
                androidx.core.app.NotificationCompat.Builder(context, "identity-test"), context, domain
            ).build()
            val glyph = de.mm20.launcher2.search.VirtualAppAppearance.glyph(domain)
            org.junit.Assert.assertNotNull(domain, glyph)
            org.junit.Assert.assertEquals(domain, glyph, notification.smallIcon.resId)
            org.junit.Assert.assertNotNull(domain, notification.getLargeIcon())
        }
        org.junit.Assert.assertNotEquals(
            de.mm20.launcher2.search.TelosAppIcons.forDomain("telos_store_app"),
            de.mm20.launcher2.search.VirtualAppAppearance.glyph("telos_store_app")
        )
    }

    @Test fun radioDefaultFollowsTheLauncherPalette() {
        var actual = androidx.compose.ui.graphics.Color.Unspecified
        val launcher = darkColorScheme()
        compose.setContent {
            MaterialTheme(colorScheme = launcher) {
                de.mm20.launcher2.ui.comms.radio.RadioAppearance(
                    de.mm20.launcher2.comms.radio.RadioSettings.Values(theme = "system", dynamicColors = true)
                ) {
                    val foreground = MaterialTheme.colorScheme.onSurface
                    androidx.compose.runtime.SideEffect { actual = foreground }
                    Text("Radio follows launcher")
                }
            }
        }
        compose.runOnIdle { org.junit.Assert.assertEquals(launcher.onSurface, actual) }
    }

    @Test fun radioPaletteDoesNotInheritTheParentTextColor() {
        val dark = mutableStateOf(false)
        var actual = androidx.compose.ui.graphics.Color.Unspecified
        var expected = androidx.compose.ui.graphics.Color.Unspecified
        compose.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides androidx.compose.ui.graphics.Color.Magenta) {
                    de.mm20.launcher2.ui.comms.radio.RadioAppearance(
                        de.mm20.launcher2.comms.radio.RadioSettings.Values(theme = if (dark.value) "dark" else "light", dynamicColors = false, translucent = true)
                    ) {
                        val foreground = androidx.compose.material3.LocalContentColor.current
                        val paired = MaterialTheme.colorScheme.onSurface
                        androidx.compose.runtime.SideEffect { actual = foreground; expected = paired }
                        Box(Modifier.fillMaxSize().then(Modifier.background(MaterialTheme.colorScheme.surfaceContainer))) {
                            Text("Ραδιόφωνο · Αθήνα", Modifier.testTag("radio-label"))
                        }
                    }
                }
            }
        }
        for (mode in listOf(false, true)) {
            compose.runOnIdle { dark.value = mode }
            compose.onNodeWithTag("radio-label").assertIsDisplayed()
            compose.runOnIdle { org.junit.Assert.assertEquals(expected, actual) }
            saveScreenshot("radio-palette-$mode.png")
        }
    }

    @Test fun virtualAppKeepsRootGlassAndOpaqueInnerContainers() {
        var alphas = emptyList<Float>()
        var rootAlpha = 1f
        var restoredSurface = androidx.compose.ui.graphics.Color.Unspecified
        compose.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                de.mm20.launcher2.ui.component.VirtualAppTheme {
                    val colors = MaterialTheme.colorScheme
                    androidx.compose.runtime.SideEffect {
                        rootAlpha = colors.surface.alpha
                        restoredSurface = colors.surface.copy(alpha = 1f)
                        alphas = listOf(colors.surfaceContainerLowest.alpha, colors.surfaceContainerLow.alpha,
                            colors.surfaceContainer.alpha, colors.surfaceContainerHigh.alpha, colors.surfaceContainerHighest.alpha)
                    }
                    Text("Readable controls")
                }
            }
        }
        compose.runOnIdle {
            org.junit.Assert.assertEquals(0f, rootAlpha, 0f)
            org.junit.Assert.assertEquals(darkColorScheme().surface, restoredSurface)
            assertTrue(alphas.all { it == 1f })
        }
    }

    @Test fun controlsNeverOverlapLargeWrappedTitles() {
        val width = mutableFloatStateOf(320f)
        val dark = mutableStateOf(false)
        val scale = mutableFloatStateOf(1f)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale.floatValue)) {
                MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                    Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize()) {
                    Box(Modifier.width(width.floatValue.dp).testTag("viewport")) {
                        Preference(
                            title = { Text("A long setting title that must wrap without losing its control", Modifier.testTag("title")) },
                            controls = { Text("Control", Modifier.testTag("control")) },
                        )
                    }
                    }
                    }
                }
            }
        }
        // CI provisions a >=840dp window; assert that each requested constraint was actually applied.
        for (isDark in listOf(false, true)) for (w in listOf(240f, 320f, 600f, 840f)) for (s in listOf(0.85f, 1f, 1.3f, 2f)) {
            compose.runOnIdle { dark.value = isDark; width.floatValue = w; scale.floatValue = s }
            compose.onNodeWithTag("viewport").assertWidthIsEqualTo(w.dp)
            val title = compose.onNodeWithTag("title", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val control = compose.onNodeWithTag("control", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue("$w dp / $s", control.top >= title.bottom || control.left >= title.right)
            assertTrue(title.height > 0 && control.height > 0)
            saveScreenshot("preference-$isDark-$w-$s.png")
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
        val dark = mutableStateOf(false)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale.floatValue)) {
                MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                    Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize()) {
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
            }
        }
        for (isDark in listOf(false, true)) for (s in listOf(0.85f, 1f, 1.3f, 2f)) {
            compose.runOnIdle { dark.value = isDark; scale.floatValue = s }
            val bar = compose.onNodeWithTag("bar").fetchSemanticsNode().boundsInRoot
            for (index in 0..2) {
                val label = compose.onNodeWithTag("label$index", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                assertTrue("Label $index / $s", label.bottom <= bar.bottom && label.right <= bar.right)
            }
            saveScreenshot("adaptive-navigation-$isDark-$s.png")
        }
        compose.onNodeWithText("All contacts").performClick().assertIsSelected()
    }

    private fun saveScreenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            // Shared test evidence survives AGP's test-APK uninstall; app cache does not.
            // Creating our own images through MediaStore needs no storage-read permission.
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name)
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,
                    android.os.Environment.DIRECTORY_PICTURES + "/TelosUiValidation")
                put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = checkNotNull(context.contentResolver.insert(
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values,
            ))
            checkNotNull(context.contentResolver.openOutputStream(uri)).use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
            }
            context.contentResolver.update(uri, android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
            }, null, null)
        } else {
            val output = File(context.externalCacheDir, name)
            output.outputStream().use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
            }
        }
    }
}
