package de.mm20.launcher2.ui.settings.desktopmode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.mm20.launcher2.desktopmode.DesktopModeManager
import de.mm20.launcher2.preferences.DesktopModeOrientation
import de.mm20.launcher2.preferences.ui.DesktopModeSettings
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class DesktopModeSettingsScreenVM(
    private val settings: DesktopModeSettings,
    private val manager: DesktopModeManager,
) : ViewModel() {

    val isSupportedOnThisDevice = manager.isSupportedOnThisDevice
    val externalDisplayConnected = manager.externalDisplay

    val enabled = settings.enabled
    fun setEnabled(enabled: Boolean) = settings.setEnabled(enabled)

    val orientation = settings.orientation
    fun setOrientation(orientation: DesktopModeOrientation) = settings.setOrientation(orientation)

    companion object : KoinComponent {
        val Factory = viewModelFactory {
            initializer {
                DesktopModeSettingsScreenVM(
                    settings = get(),
                    manager = get(),
                )
            }
        }
    }
}
