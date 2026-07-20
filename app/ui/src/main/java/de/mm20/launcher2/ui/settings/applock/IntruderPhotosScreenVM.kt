package de.mm20.launcher2.ui.settings.applock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.applock.IntruderPhotoManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

class IntruderPhotosScreenVM : ViewModel(), KoinComponent {
    private val intruderPhotoManager: IntruderPhotoManager by inject()

    private val _photos = MutableStateFlow<List<File>>(emptyList())
    val photos = _photos.asStateFlow()

    init {
        refresh()
    }

    private fun refresh() {
        viewModelScope.launch {
            _photos.value = intruderPhotoManager.listPhotos()
        }
    }

    fun delete(file: File) {
        viewModelScope.launch {
            intruderPhotoManager.delete(file)
            refresh()
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            intruderPhotoManager.deleteAll()
            refresh()
        }
    }
}
