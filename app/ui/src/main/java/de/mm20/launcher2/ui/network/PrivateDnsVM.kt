package de.mm20.launcher2.ui.network

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.appmanagement.ShizukuManager
import de.mm20.launcher2.network.privatedns.PrivateDnsController
import de.mm20.launcher2.network.privatedns.PrivateDnsError
import de.mm20.launcher2.network.privatedns.PrivateDnsResult
import de.mm20.launcher2.network.api.PrivateDnsMode
import de.mm20.launcher2.network.api.PrivateDnsState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

data class PrivateDnsUiState(
    val current: PrivateDnsState? = null,
    val loading: Boolean = true,
    val error: PrivateDnsError? = null,
    val permissionDenied: Boolean = false,
)

class PrivateDnsVM : ViewModel(), KoinComponent {
    private val controller: PrivateDnsController by inject()
    private val shizuku: ShizukuManager by inject()
    private val mutable = MutableStateFlow(PrivateDnsUiState())
    val state = mutable.asStateFlow()
    private var running = false

    init {
        refresh()
    }

    fun refresh() = operation {
        when (val result = controller.observe()) {
            is PrivateDnsResult.Success -> mutable.update { it.copy(current = result.state) }
            is PrivateDnsResult.Failure -> mutable.update { it.copy(error = result.reason) }
        }
    }

    fun requestPermission() = operation {
        if (!shizuku.requestPermission()) {
            mutable.update { it.copy(permissionDenied = true) }
            return@operation
        }
        mutable.update { it.copy(permissionDenied = false) }
        when (val result = controller.observe()) {
            is PrivateDnsResult.Success -> mutable.update { it.copy(current = result.state) }
            is PrivateDnsResult.Failure -> mutable.update { it.copy(error = result.reason) }
        }
    }

    fun setMode(mode: PrivateDnsMode) = operation {
        when (val result = controller.setMode(mode)) {
            is PrivateDnsResult.Success -> mutable.update { it.copy(current = result.state) }
            is PrivateDnsResult.Failure -> mutable.update { it.copy(error = result.reason) }
        }
    }

    fun setProvider(hostname: String) = operation {
        when (val result = controller.setProvider(hostname)) {
            is PrivateDnsResult.Success -> mutable.update { it.copy(current = result.state) }
            is PrivateDnsResult.Failure -> mutable.update { it.copy(error = result.reason) }
        }
    }

    private fun operation(block: suspend () -> Unit) {
        if (running) return
        running = true
        mutable.update { it.copy(loading = true, error = null, permissionDenied = false) }
        viewModelScope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutable.update { it.copy(error = PrivateDnsError.COMMAND_FAILED) }
            } finally {
                running = false
                mutable.update { it.copy(loading = false) }
            }
        }
    }
}
