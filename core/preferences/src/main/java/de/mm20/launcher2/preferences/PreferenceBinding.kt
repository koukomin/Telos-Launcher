package de.mm20.launcher2.preferences

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Creates a [StateFlow] bound to a preference Datastore [Flow] with standard
 * WhileSubscribed(5000) retention policy.
 */
fun <T> Flow<T>.asPreferenceState(
    scope: CoroutineScope,
    initialValue: T
): StateFlow<T> = this.stateIn(
    scope = scope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = initialValue
)
