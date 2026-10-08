package de.mm20.launcher2.comms.media.video.torrent

/** How peer connections are encrypted (BitTorrent protocol encryption) */
enum class TorrentEncryption {
    /** Encrypted when the peer can, plain otherwise */
    Prefer,

    /** Only encrypted connections, peers without encryption are refused */
    Require,

    /** Never encrypted */
    Disable,
}

/**
 * Settings of the one torrent session of Telos. They are shared by Telos Downloads and the Telos Video
 * streamer because both use the same session. Pure data, so the rules around it can be tested.
 */
data class TorrentConfig(
    val dht: Boolean = true,
    val pex: Boolean = true,
    val lsd: Boolean = true,
    val utp: Boolean = true,
    val encryption: TorrentEncryption = TorrentEncryption.Prefer,
    /** 0: a random port every time the session starts */
    val listenPort: Int = 0,
    val upnp: Boolean = true,
    val natPmp: Boolean = true,
    /** KB/s, 0: no limit */
    val downloadLimitKBps: Int = 0,
    val uploadLimitKBps: Int = 0,
    /** Torrents that download at the same time; the others wait in the queue of the session */
    val maxActiveDownloads: Int = 3,
    val maxActiveSeeds: Int = 3,
    val maxConnections: Int = 200,
    /** Default for new torrents: stop seeding when this ratio is reached, in hundredths (100 = 1.0), 0: no ratio limit */
    val seedRatioX100: Int = 100,
    /** Default for new torrents: stop seeding after this many minutes, 0: no time limit */
    val seedMinutes: Int = 0,
    /** Default for new torrents: do not seed at all */
    val stopAtDone: Boolean = false,
    /** Default for new torrents: download in order */
    val sequentialByDefault: Boolean = false,
) {
    fun sanitized() = copy(
        listenPort = if (listenPort == 0) 0 else listenPort.coerceIn(1024, 65535),
        downloadLimitKBps = downloadLimitKBps.coerceAtLeast(0),
        uploadLimitKBps = uploadLimitKBps.coerceAtLeast(0),
        maxActiveDownloads = maxActiveDownloads.coerceIn(1, 20),
        maxActiveSeeds = maxActiveSeeds.coerceIn(0, 20),
        maxConnections = maxConnections.coerceIn(20, 1000),
        seedRatioX100 = seedRatioX100.coerceIn(0, 10_000),
        seedMinutes = seedMinutes.coerceIn(0, 60 * 24 * 365),
    )

    /** "0.0.0.0:6881,[::]:6881" style value of libtorrent's listen_interfaces */
    fun listenInterfaces(): String = "0.0.0.0:$listenPort,[::]:$listenPort"

    /** Hard limit of active torrents (downloading plus seeding plus checking) */
    fun activeLimit(): Int = maxActiveDownloads + maxActiveSeeds + 2
}
