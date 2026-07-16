package de.mm20.launcher2.ui.launcher.widgets.freeze

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.freeze.AutoFreezeController
import de.mm20.launcher2.freeze.FreezeManager
import de.mm20.launcher2.preferences.freeze.FreezeSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class FreezeWidgetVM : ViewModel(), KoinComponent {
    private val freezeManager: FreezeManager by inject()
    private val autoFreezeController: AutoFreezeController by inject()
    private val freezeSettings: FreezeSettings by inject()

    val candidates = freezeSettings.candidates
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptySet())

    private val _frozenCount = MutableStateFlow(0)
    val frozenCount = _frozenCount.asStateFlow()

    fun refresh() {
        _frozenCount.value = candidates.value.count { freezeManager.isFrozen(it) }
    }

    /** Same exclusion checks as the automatic triggers - this does not bypass them. */
    fun freezeAllNow() {
        autoFreezeController.freezeAllCandidatesNow()
        viewModelScope.launch {
            delay(1000)
            refresh()
        }
    }
}
