package com.telos.desktop.cursor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/** Compose-visible revision bumped whenever [CursorThemeApplier.apply] finishes. */
object CursorUiState {
    var revision by mutableIntStateOf(0)
        private set

    fun bump() {
        revision++
    }
}
