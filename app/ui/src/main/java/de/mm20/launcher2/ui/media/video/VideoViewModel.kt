package de.mm20.launcher2.ui.media.video

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.comms.media.video.VideoItem
import de.mm20.launcher2.comms.media.video.VideoLibrary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class VideoViewModel : ViewModel() {

    private val _items = MutableStateFlow<List<VideoItem>>(emptyList())
    val items: StateFlow<List<VideoItem>> = _items

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    fun load(context: Context) {
        viewModelScope.launch {
            _loading.value = true
            _items.value = VideoLibrary.load(context)
            _loading.value = false
        }
    }
}
