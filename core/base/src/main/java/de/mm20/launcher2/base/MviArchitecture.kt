package de.mm20.launcher2.base

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.StateFlow

/**
 * Marker interface for MVI Screen UI State.
 * Annotate implementations with @Immutable for Compose recomposition optimization.
 */
@Immutable
interface UiState

/**
 * Marker interface for MVI User Actions / Events.
 */
interface UiEvent

/**
 * Unidirectional Data Flow Contract for MVI ViewModels in Telos Launcher.
 */
interface MviViewModel<S : UiState, E : UiEvent> {
    val uiState: StateFlow<S>
    fun onEvent(event: E)
}
