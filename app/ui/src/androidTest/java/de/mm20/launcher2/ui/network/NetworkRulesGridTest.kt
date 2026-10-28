package de.mm20.launcher2.ui.network

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.test.platform.app.InstrumentationRegistry
import de.mm20.launcher2.network.api.AppRule
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.network.firewall.NetworkAppRuleGrid
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class NetworkRulesGridTest {
    @get:Rule val compose = createComposeRule()
    @Test fun largeTextActionsRemainReachableAndIsolationReplacesBypass() {
        val rule = mutableStateOf(AppRule(10123, bypassFirewall = true))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    Column(Modifier.requiredSize(320.dp, 720.dp).background(MaterialTheme.colorScheme.surfaceContainer)
                        .testTag("network-rule-grid").verticalScroll(rememberScrollState())) {
                        NetworkAppRuleGrid(rule.value, true) { rule.value = it }
                    }
                }
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.onNodeWithText(context.getString(R.string.netfw_blocked)).performScrollTo().performClick()
        compose.runOnIdle { assertTrue(rule.value.blockAll); assertFalse(rule.value.bypassFirewall) }
        compose.onNodeWithText(context.getString(R.string.netfw_exclude_vpn)).performScrollTo().assertIsDisplayed()
        val bitmap = compose.onNodeWithTag("network-rule-grid").captureToImage().asAndroidBitmap()
        val directory = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES), "TelosUiValidation").apply { mkdirs() }
        java.io.File(directory, "network-app-grid-font2-dark.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        compose.onNodeWithText(context.getString(R.string.netfw_exclude_vpn)).performClick()
        compose.runOnIdle { assertTrue(rule.value.excludeFromVpn); assertFalse(rule.value.blockAll) }
    }
}
