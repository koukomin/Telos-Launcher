package de.mm20.launcher2.ui.keyboard

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.base.R as BaseR
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.ui.component.TelosCyber
import de.mm20.launcher2.ui.component.TelosCyberAppBarTitle
import de.mm20.launcher2.ui.component.TelosCyberSectionLabel
import de.mm20.launcher2.ui.component.VirtualAppWindow
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.component.telosTechSurface
import de.mm20.launcher2.ui.locals.LocalBackStack
import kotlinx.serialization.Serializable

@Serializable data object TelosKeyboardRoute : NavKey

@Composable
fun TelosKeyboardSettingsScreen() {
    val context = LocalContext.current
    val backStack = LocalBackStack.current

    VirtualAppWindow {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (backStack.size > 1) {
                    IconButton(onClick = { backStack.removeLastOrNull() }) {
                        Icon(
                            painterResource(BaseR.drawable.arrow_back_24px),
                            contentDescription = stringResource(I18nR.string.hc_back),
                        )
                    }
                }
                TelosCyberAppBarTitle(
                    title = stringResource(I18nR.string.settings_keyboard),
                    status = stringResource(I18nR.string.keyboard_settings_category),
                    statusColor = TelosCyber.NeonCyan,
                    modifier = Modifier.weight(1f),
                )
            }

            TelosCyberSectionLabel(
                stringResource(I18nR.string.keyboard_enable_title),
                accent = TelosCyber.NeonGreen,
            )

            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .telosTechSurface(
                            borderColor = TelosCyber.NeonCyan,
                            shape = RoundedCornerShape(18.dp),
                        )
                        .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(I18nR.string.keyboard_enable_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(I18nR.string.keyboard_enable_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                ) {
                    Text(stringResource(I18nR.string.keyboard_enable_action))
                }
                androidx.compose.material3.OutlinedButton(
                    onClick = {
                        context
                            .getSystemService(
                                android.view.inputmethod.InputMethodManager::class.java
                            )
                            ?.showInputMethodPicker()
                    }
                ) {
                    Text(stringResource(I18nR.string.keyboard_choose_action))
                }
            }

            PreferenceCategory(title = stringResource(I18nR.string.keyboard_settings_category)) {
                SwitchPreference(
                    title = stringResource(I18nR.string.keyboard_incognito_title),
                    summary = stringResource(I18nR.string.preference_private_keyboard_summary),
                    value = TelosKeyboardState.isIncognitoMode,
                    onValueChanged = { TelosKeyboardState.isIncognitoMode = it },
                )
            }

            TelosCyberSectionLabel(
                stringResource(I18nR.string.keyboard_look_category),
                accent = TelosCyber.NeonMagenta,
            )
            androidx.compose.runtime.LaunchedEffect(Unit) { TelosKeyboardAppearance.load(context) }
            PreferenceCategory(title = stringResource(I18nR.string.keyboard_look_category)) {
                Text(
                    stringResource(I18nR.string.keyboard_accent_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TelosKeyboardAppearance.accentSwatches.forEach { argb ->
                        val selected = TelosKeyboardAppearance.accentArgb == argb
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(androidx.compose.ui.graphics.Color(argb.toULong()))
                                .clickable {
                                    TelosKeyboardAppearance.accentArgb = argb
                                    TelosKeyboardAppearance.save(context)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) {
                                Text("✓", color = androidx.compose.ui.graphics.Color.Black)
                            }
                        }
                    }
                }
                de.mm20.launcher2.ui.component.preferences.SliderPreference(
                    title = stringResource(I18nR.string.keyboard_scale_title),
                    value = TelosKeyboardAppearance.keyboardScale,
                    min = 0.8f,
                    max = 1.35f,
                    step = 0.05f,
                    onValueChanged = {
                        TelosKeyboardAppearance.keyboardScale = it
                        TelosKeyboardAppearance.save(context)
                    },
                    label = { v -> Text("%.0f%%".format(v * 100f)) },
                )
                de.mm20.launcher2.ui.component.preferences.SliderPreference(
                    title = stringResource(I18nR.string.keyboard_key_height_title),
                    value = TelosKeyboardAppearance.keyHeightDp,
                    min = 44f,
                    max = 72f,
                    step = 2f,
                    onValueChanged = {
                        TelosKeyboardAppearance.keyHeightDp = it
                        TelosKeyboardAppearance.save(context)
                    },
                    label = { v -> Text("%.0f dp".format(v)) },
                )
                de.mm20.launcher2.ui.component.preferences.SliderPreference(
                    title = stringResource(I18nR.string.keyboard_key_font_title),
                    value = TelosKeyboardAppearance.keyFontSp,
                    min = 14f,
                    max = 26f,
                    step = 1f,
                    onValueChanged = {
                        TelosKeyboardAppearance.keyFontSp = it
                        TelosKeyboardAppearance.save(context)
                    },
                    label = { v -> Text("%.0f sp".format(v)) },
                )
                SwitchPreference(
                    title = "Show Key Borders",
                    summary = "Draw outlines around individual keys like classic keyboards",
                    value = TelosKeyboardAppearance.showKeyBorders,
                    onValueChanged = {
                        TelosKeyboardAppearance.showKeyBorders = it
                        TelosKeyboardAppearance.save(context)
                    }
                )
                SwitchPreference(
                    title = "Show Number Row",
                    summary = "Display a dedicated 1-0 number row above the keyboard",
                    value = TelosKeyboardAppearance.showNumberRow,
                    onValueChanged = {
                        TelosKeyboardAppearance.showNumberRow = it
                        TelosKeyboardAppearance.save(context)
                    }
                )
                SwitchPreference(
                    title = "Show Symbol Hints",
                    summary = "Show secondary number and symbol labels on key corners",
                    value = TelosKeyboardAppearance.showSymbolHints,
                    onValueChanged = {
                        TelosKeyboardAppearance.showSymbolHints = it
                        TelosKeyboardAppearance.save(context)
                    }
                )
            }
        }
    }
}
