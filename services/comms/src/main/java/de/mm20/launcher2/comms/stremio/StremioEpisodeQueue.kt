package de.mm20.launcher2.comms.stremio

object StremioEpisodeQueue {
    fun after(type: String, videos: List<Video>, currentId: String): List<Video> {
        if (type != "series") return emptyList()
        val ordered = videos.filter { it.season != null && it.episode != null && it.id.length <= 512 }.distinctBy { it.id }
            .sortedWith(compareBy({ it.season }, { it.episode }))
        val index = ordered.indexOfFirst { it.id == currentId }
        return if (index < 0) emptyList() else ordered.drop(index + 1).take(100).map { it.copy(title = it.title.take(512)) }
    }
}
