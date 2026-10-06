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
)

data class RadioHistoryEntry(
    val id: Long,
    val stationId: String,
    val stationName: String,
    val title: String,
    val playedAt: Long,
)
