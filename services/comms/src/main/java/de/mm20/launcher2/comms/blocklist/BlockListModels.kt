package de.mm20.launcher2.comms.blocklist

import kotlinx.serialization.Serializable

enum class BlockListKind {
    /** Domain lists for the web app ad and tracker blocker */
    WEB,

    /** IP range lists for the torrent peer filter */
    TORRENT_IP,
}

enum class UpdateSchedule { OFF, DAILY, WEEKLY }

/**
 * One block list. [builtinId] is set for presets (they cannot be removed, only switched off).
 * An empty [sourceUrl] means the list was imported from a file and is never updated.
 */
@Serializable
data class BlockList(
    val id: String,
    val name: String,
    val kind: BlockListKind,
    val sourceUrl: String = "",
    val builtin: Boolean = false,
    val enabled: Boolean = false,
    val lastUpdate: Long = 0L,
    val etag: String? = null,
    val lastModified: String? = null,
    val entryCount: Int = 0,
    val lastError: String? = null,
)

@Serializable
data class BlockListState(
    val lists: List<BlockList> = emptyList(),
    val schedule: UpdateSchedule = UpdateSchedule.WEEKLY,
    val wifiOnly: Boolean = true,
)

/** A list we offer ready-made. Nothing is downloaded until the user switches it on. */
data class BlockListPreset(
    val id: String,
    val name: String,
    val kind: BlockListKind,
    val url: String,
    val license: String,
    val homepage: String,
)

object BlockListPresets {
    val all: List<BlockListPreset> = listOf(
        BlockListPreset(
            "web-stevenblack", "StevenBlack unified hosts", BlockListKind.WEB,
            "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts",
            "MIT", "https://github.com/StevenBlack/hosts",
        ),
        BlockListPreset(
            "web-adguard-dns", "AdGuard DNS filter", BlockListKind.WEB,
            "https://adguardteam.github.io/AdGuardSDNSFilter/Filters/filter.txt",
            "GPL-3.0", "https://github.com/AdguardTeam/AdGuardSDNSFilter",
        ),
        BlockListPreset(
            "web-peterlowe", "Peter Lowe's ad and tracking server list", BlockListKind.WEB,
            "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext",
            "free to use and redistribute (see site)", "https://pgl.yoyo.org/adservers/",
        ),
        BlockListPreset(
            "web-oisd-small", "OISD small", BlockListKind.WEB,
            "https://small.oisd.nl/domainswild",
            "GPL-3.0", "https://oisd.nl",
        ),
        BlockListPreset(
            "web-easylist", "EasyList (domain rules only)", BlockListKind.WEB,
            "https://easylist.to/easylist/easylist.txt",
            "GPL-3.0 / CC BY-SA 3.0", "https://easylist.to",
        ),
        BlockListPreset(
            "web-urlhaus", "URLhaus malware domains", BlockListKind.WEB,
            "https://urlhaus.abuse.ch/downloads/hostfile/",
            "abuse.ch terms of use", "https://urlhaus.abuse.ch",
        ),
        BlockListPreset(
            "ip-naunter", "Naunter BT_BlockLists", BlockListKind.TORRENT_IP,
            "https://github.com/Naunter/BT_BlockLists/raw/master/bt_blocklists.gz",
            "Unlicense", "https://github.com/Naunter/BT_BlockLists",
        ),
    )

    fun byId(id: String) = all.firstOrNull { it.id == id }
}
