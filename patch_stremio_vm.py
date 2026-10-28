import re

with open('app/ui/src/main/java/de/mm20/launcher2/ui/media/stremio/StremioViewModel.kt', 'r') as f:
    code = f.read()

funcs = """    fun addAddon(url: String, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            val error = withContext(Dispatchers.IO) { Stremio.addAddon(context, url) }
            onDone(error)
            reload()
        }
    }

    fun markWatched(metaId: String, videoId: String, season: Int?, episode: Int?) {
        viewModelScope.launch(Dispatchers.IO) {
            val session = de.mm20.launcher2.comms.media.video.trakt.Trakt.session(context)
            if (session.isNotBlank()) {
                val at = System.currentTimeMillis()
                val idStr = "$metaId:$season:$episode"
                de.mm20.launcher2.comms.media.video.trakt.Trakt.scrobbleId(context, "stop", metaId, season, episode, 100.0, session, at)
                Stremio.savePlaybackState(context, metaId, videoId, season, episode, 0, 100.0)
            }
        }
    }

    fun markSeasonWatched(metaId: String, season: Int?, episodes: List<Pair<String, Int?>>) {
        viewModelScope.launch(Dispatchers.IO) {
            val session = de.mm20.launcher2.comms.media.video.trakt.Trakt.session(context)
            if (session.isNotBlank()) {
                val at = System.currentTimeMillis()
                episodes.forEach { (vId, ep) ->
                    de.mm20.launcher2.comms.media.video.trakt.Trakt.scrobbleId(context, "stop", metaId, season, ep, 100.0, session, at)
                    Stremio.savePlaybackState(context, metaId, vId, season, ep, 0, 100.0)
                }
            }
        }
    }"""

code = re.sub(r'fun addAddon\(url: String, onDone: \(String\?\) -> Unit\) \{[\s\S]*?\}', funcs, code)

with open('app/ui/src/main/java/de/mm20/launcher2/ui/media/stremio/StremioViewModel.kt', 'w') as f:
    f.write(code)

