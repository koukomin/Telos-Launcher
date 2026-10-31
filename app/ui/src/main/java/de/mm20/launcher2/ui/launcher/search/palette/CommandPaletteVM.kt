package de.mm20.launcher2.ui.launcher.search.palette

import android.app.Application
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.comms.search.TelosSearch
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.base.R
import de.mm20.launcher2.ui.voice.VoiceRecorderService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class CommandAction(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val icon: Int,
    val onExecute: (Context) -> Unit
)

class CommandPaletteVM(application: Application) : AndroidViewModel(application) {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    // Static system actions (Quick settings, tools)
    private val systemActions = listOf(
        CommandAction(
            id = "sys_flashlight",
            title = "Toggle Flashlight",
            subtitle = "Turn camera LED on/off",
            icon = R.drawable.light_mode_24px
        ) { ctx ->
            // Simple toggle logic (in a real app, you'd check current state)
            val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            try {
                val cameraId = cm.cameraIdList.firstOrNull()
                if (cameraId != null) {
                    // Assuming off for now, a proper implementation would track state
                    cm.setTorchMode(cameraId, true)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        },
        CommandAction(
            id = "sys_voice_recorder",
            title = "Start Voice Recording",
            subtitle = "Record audio instantly",
            icon = R.drawable.mic_24px
        ) { ctx ->
            VoiceRecorderService.startRecording(ctx)
        },
        CommandAction(
            id = "sys_magnifier",
            title = "Open Magnifier",
            subtitle = "Zoom in with camera",
            icon = de.mm20.launcher2.base.R.drawable.pinch_zoom_in_24px
        ) { _ ->
            // Note: In Compose Navigation you'd route this.
            // For now, it's a placeholder intent action.
        },
        CommandAction(
            id = "sys_settings",
            title = "Launcher Settings",
            subtitle = "Customize Telos",
            icon = R.drawable.settings_24px
        ) { ctx ->
             ctx.tryStartActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
        }
    )

    val results: StateFlow<List<CommandAction>> = _query.combine(MutableStateFlow(systemActions)) { q, actions ->
        if (q.isBlank()) {
            actions
        } else {
            TelosSearch.filter(actions, q) { listOf(it.title, it.subtitle ?: "") }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), systemActions)

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
    }
}
