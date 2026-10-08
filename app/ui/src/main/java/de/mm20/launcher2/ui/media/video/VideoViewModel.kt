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
            // the cached list of the network videos is a file read and a JSON parse: not on the main thread
            val local = VideoLibrary.load(context)
            val remote = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { RemoteVideo.cached(context) }
            _items.value = local + remote
            _loading.value = false
        }
    }
}
