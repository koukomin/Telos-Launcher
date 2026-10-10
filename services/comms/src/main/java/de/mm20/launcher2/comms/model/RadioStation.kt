package de.mm20.launcher2.comms.model

data class RadioStation(
    val id: String,
    val name: String,
    val streamUrl: String,
    val faviconUrl: String,
    val homepage: String = "",
    val streamContent: String = "",
    val nameManuallySet: Boolean = false,
    /** Fallback streams that are tried when the main stream fails */
    val alternateStreams: List<String> = emptyList(),
    /** Epoch millis of the last time the user played this saved station, 0 when never */
    val lastPlayedAt: Long = 0L,
)

data class RadioHistoryEntry(
    val id: Long,
    val stationId: String,
    val stationName: String,
    val title: String,
    val playedAt: Long,
)
