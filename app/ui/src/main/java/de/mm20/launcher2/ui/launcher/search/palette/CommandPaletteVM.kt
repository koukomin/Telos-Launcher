package de.mm20.launcher2.ui.launcher.search.palette

import android.app.Application
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.comms.search.TelosSearch
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.base.R
import de.mm20.launcher2.ui.settings.SettingsActivity
import de.mm20.launcher2.ui.voice.VoiceRecorderService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.concurrent.atomic.AtomicBoolean

data class CommandAction(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val icon: Int,
    val onExecute: (Context) -> Unit,
)

class CommandPaletteVM(application: Application) : AndroidViewModel(application) {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val torchOn = AtomicBoolean(false)

    private val systemActions: List<CommandAction> = run {
        val app = getApplication<Application>()
        listOf(
            CommandAction(
                id = "sys_flashlight",
                title = app.getString(I18nR.string.command_palette_flashlight),
                subtitle = app.getString(I18nR.string.command_palette_flashlight_sub),
                icon = R.drawable.light_mode_24px,
            ) { ctx ->
                val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                runCatching {
                    val cameraId = cm.cameraIdList.firstOrNull() ?: return@runCatching
                    val next = !torchOn.get()
                    cm.setTorchMode(cameraId, next)
                    torchOn.set(next)
                }
            },
            CommandAction(
                id = "sys_voice_recorder",
                title = app.getString(I18nR.string.command_palette_voice),
                subtitle = app.getString(I18nR.string.command_palette_voice_sub),
                icon = R.drawable.mic_24px,
            ) { ctx ->
                VoiceRecorderService.startRecording(ctx)
            },
            CommandAction(
                id = "sys_magnifier",
                title = app.getString(I18nR.string.command_palette_magnifier),
                subtitle = app.getString(I18nR.string.command_palette_magnifier_sub),
                icon = R.drawable.pinch_zoom_in_24px,
            ) { ctx ->
                ctx.tryStartActivity(
                    Intent(ctx, SettingsActivity::class.java)
                        .putExtra(SettingsActivity.EXTRA_ROUTE, "settings/magnifier")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
            CommandAction(
                id = "sys_settings",
                title = app.getString(I18nR.string.command_palette_settings),
                subtitle = app.getString(I18nR.string.command_palette_settings_sub),
                icon = R.drawable.settings_24px,
            ) { ctx ->
                ctx.tryStartActivity(
                    Intent(ctx, SettingsActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
        )
    }

    val results: StateFlow<List<CommandAction>> = _query.combine(MutableStateFlow(systemActions)) { q, actions ->
        if (q.isBlank()) actions
        else TelosSearch.filter(actions, q) { listOf(it.title, it.subtitle ?: "") }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), systemActions)

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
    }
}
