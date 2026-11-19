package de.mm20.launcher2.ui.aether

import com.vitorpamplona.amethyst.ui.tor.TorBackend
import com.vitorpamplona.amethyst.ui.tor.TorBackendBridge
import com.vitorpamplona.amethyst.ui.tor.TorServiceStatus
import de.mm20.launcher2.network.tor.TorController
import de.mm20.launcher2.network.tor.TorStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * [TorBackend] bridge linking Telos Launcher's global [TorController] in `:services:network`
 * to Amethyst's Tor manager, preventing duplicate native Tor instances.
 */
class TelosTorBackend(
    private val torController: TorController,
    scope: CoroutineScope,
) : TorBackend {
    private val _status = MutableStateFlow<TorServiceStatus>(TorServiceStatus.Off)
    override val status: StateFlow<TorServiceStatus> = _status.asStateFlow()

    private val _bootstrapInFlight = MutableStateFlow(false)
    override val bootstrapInFlight: StateFlow<Boolean> = _bootstrapInFlight.asStateFlow()

    private val _bootstrapProgress = MutableStateFlow(-1)
    override val bootstrapProgress: StateFlow<Int> = _bootstrapProgress.asStateFlow()

    private val _guardsDownSignal = MutableSharedFlow<Unit>()
    override val guardsDownSignal: Flow<Unit> = _guardsDownSignal.asSharedFlow()

    init {
        torController.status.onEach { state ->
            when (state) {
                TorStatus.Ready -> {
                    _bootstrapInFlight.value = false
                    _bootstrapProgress.value = 100
                    val port = torController.progress.value
                    _status.value = TorServiceStatus.Active(if (port > 0) port else 9050)
                }
                TorStatus.Starting, TorStatus.Waiting -> {
                    _bootstrapInFlight.value = true
                    _status.value = TorServiceStatus.Connecting
                }
                TorStatus.Off, TorStatus.Unavailable -> {
                    _bootstrapInFlight.value = false
                    _bootstrapProgress.value = -1
                    _status.value = TorServiceStatus.Off
                }
            }
        }.launchIn(scope)

        torController.progress.onEach { prog ->
            _bootstrapProgress.value = prog
        }.launchIn(scope)
    }

    override suspend fun start() {
        torController.requestForOnion()
    }

    override suspend fun stop() {
        _status.value = TorServiceStatus.Off
    }

    override suspend fun reset() {
        torController.requestForOnion()
    }

    override suspend fun resetWithCleanState() {
        torController.requestForOnion()
    }

    override suspend fun hasBootstrappedBefore(): Boolean {
        return torController.ready
    }

    companion object {
        fun registerBridge(torController: TorController, scope: CoroutineScope) {
            TorBackendBridge.provider = { TelosTorBackend(torController, scope) }
        }
    }
}
