package de.mm20.launcher2.ui.media.video

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.comms.media.video.VideoItem
import de.mm20.launcher2.comms.media.video.VideoLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VideoViewModel : ViewModel() {

    private val _items = MutableStateFlow<List<VideoItem>>(emptyList())
    val items: StateFlow<List<VideoItem>> = _items

    // true from the start: the screen must not flash "no videos" before the first scan has run
    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading

    private var job: Job? = null

    fun load(context: Context) {
        val appContext = context.applicationContext
        // a newer scan replaces a running one, so a slow old scan cannot overwrite fresh results
        job?.cancel()
        job = viewModelScope.launch {
            _loading.value = true
            try {
                val local = VideoLibrary.load(appContext)
                // the cached list of the network videos is a file read and a JSON parse: not on the main thread
                val remote = withContext(Dispatchers.IO) { RemoteVideo.cached(appContext) }
                _items.value = local + remote
            } finally {
                _loading.value = false
            }
        }
    }
}
