// === TELOS_PENDING_REVIEW_START: desktop_wm_klassy_sweet_theming ===
package de.mm20.launcher2.ui.desktopmode.theme

import android.content.Context
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.desktopmode.cursor.CursorConfig
import de.mm20.launcher2.ui.desktopmode.cursor.CursorThemePreset
import de.mm20.launcher2.ui.desktopmode.wm.ButtonPosition
import de.mm20.launcher2.ui.desktopmode.wm.WindowButtonStyle
import de.mm20.launcher2.ui.desktopmode.wm.WindowDecorationPreset
import de.mm20.launcher2.ui.desktopmode.wm.WindowThemeConfig
import de.mm20.launcher2.ui.desktopmode.wm.WindowThemePresets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists and exposes live Desktop Mode Window Decoration & Cursor Theme configurations.
 */
class DesktopThemeRepository(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("telos_desktop_theme", Context.MODE_PRIVATE)

    private val _windowTheme = MutableStateFlow(loadWindowTheme())
    val windowTheme: StateFlow<WindowThemeConfig> = _windowTheme.asStateFlow()

    private val _cursorConfig = MutableStateFlow(loadCursorConfig())
    val cursorConfig: StateFlow<CursorConfig> = _cursorConfig.asStateFlow()

    private fun loadWindowTheme(): WindowThemeConfig {
        val presetName = prefs.getString("wm_preset", WindowDecorationPreset.SIERRA_MACOS_DARK.name) ?: WindowDecorationPreset.SIERRA_MACOS_DARK.name
        val preset = runCatching { WindowDecorationPreset.valueOf(presetName) }.getOrDefault(WindowDecorationPreset.SIERRA_MACOS_DARK)
        val base = WindowThemePresets.forPreset(preset)

        val buttonStyleName = prefs.getString("wm_button_style", base.buttonStyle.name) ?: base.buttonStyle.name
        val buttonStyle = runCatching { WindowButtonStyle.valueOf(buttonStyleName) }.getOrDefault(base.buttonStyle)

        val buttonPosName = prefs.getString("wm_button_pos", base.buttonPosition.name) ?: base.buttonPosition.name
        val buttonPos = runCatching { ButtonPosition.valueOf(buttonPosName) }.getOrDefault(base.buttonPosition)

        val cornerTop = prefs.getFloat("wm_corner_top", base.cornerRadiusTop.value)
        val cornerBottom = prefs.getFloat("wm_corner_bottom", base.cornerRadiusBottom.value)
        val glow = prefs.getFloat("wm_glow", base.borderGlowIntensity)
        val opacity = prefs.getFloat("wm_opacity", base.titlebarOpacity)
        val unison = prefs.getBoolean("wm_unison_hover", base.unisonHoverEffect)

        return base.copy(
            buttonStyle = buttonStyle,
            buttonPosition = buttonPos,
            cornerRadiusTop = cornerTop.dp,
            cornerRadiusBottom = cornerBottom.dp,
            borderGlowIntensity = glow,
            titlebarOpacity = opacity,
            unisonHoverEffect = unison
        )
    }

    private fun loadCursorConfig(): CursorConfig {
        val presetName = prefs.getString("cursor_preset", CursorThemePreset.SWEET_DARK_NEON.name) ?: CursorThemePreset.SWEET_DARK_NEON.name
        val preset = runCatching { CursorThemePreset.valueOf(presetName) }.getOrDefault(CursorThemePreset.SWEET_DARK_NEON)
        val size = prefs.getFloat("cursor_size", 32f)
        val glow = prefs.getBoolean("cursor_glow", true)
        val pulse = prefs.getBoolean("cursor_pulse", true)

        return CursorConfig(
            preset = preset,
            size = size.dp,
            showNeonGlow = glow,
            showClickPulse = pulse
        )
    }

    fun updateWindowPreset(preset: WindowDecorationPreset) {
        val newConfig = WindowThemePresets.forPreset(preset)
        _windowTheme.value = newConfig
        prefs.edit()
            .putString("wm_preset", preset.name)
            .putString("wm_button_style", newConfig.buttonStyle.name)
            .putString("wm_button_pos", newConfig.buttonPosition.name)
            .putFloat("wm_corner_top", newConfig.cornerRadiusTop.value)
            .putFloat("wm_corner_bottom", newConfig.cornerRadiusBottom.value)
            .putFloat("wm_glow", newConfig.borderGlowIntensity)
            .putFloat("wm_opacity", newConfig.titlebarOpacity)
            .putBoolean("wm_unison_hover", newConfig.unisonHoverEffect)
            .apply()
    }

    fun updateWindowTheme(config: WindowThemeConfig) {
        _windowTheme.value = config
        prefs.edit()
            .putString("wm_preset", config.preset.name)
            .putString("wm_button_style", config.buttonStyle.name)
            .putString("wm_button_pos", config.buttonPosition.name)
            .putFloat("wm_corner_top", config.cornerRadiusTop.value)
            .putFloat("wm_corner_bottom", config.cornerRadiusBottom.value)
            .putFloat("wm_glow", config.borderGlowIntensity)
            .putFloat("wm_opacity", config.titlebarOpacity)
            .putBoolean("wm_unison_hover", config.unisonHoverEffect)
            .apply()
    }

    fun updateCursorConfig(config: CursorConfig) {
        _cursorConfig.value = config
        prefs.edit()
            .putString("cursor_preset", config.preset.name)
            .putFloat("cursor_size", config.size.value)
            .putBoolean("cursor_glow", config.showNeonGlow)
            .putBoolean("cursor_pulse", config.showClickPulse)
            .apply()
    }

    companion object {
        @Volatile
        private var instance: DesktopThemeRepository? = null

        fun getInstance(context: Context): DesktopThemeRepository {
            return instance ?: synchronized(this) {
                instance ?: DesktopThemeRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
// === TELOS_PENDING_REVIEW_END: desktop_wm_klassy_sweet_theming ===
