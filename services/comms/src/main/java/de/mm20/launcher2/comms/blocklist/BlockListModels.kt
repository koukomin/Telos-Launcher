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
    /** The date the list itself says it was generated on (header or Last-Modified), 0 when unknown */
    val dataDate: Long = 0L,
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
    /** What the list is and is not, shown with the list. English, like the name. */
    val description: String = "",
    /** Cut private and local ranges out of the list (lists that contain bogons) */
    val skipReserved: Boolean = false,
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
            "ip-naunter", "Naunter BT_BlockLists (combined)", BlockListKind.TORRENT_IP,
            "https://raw.githubusercontent.com/Naunter/BT_BlockLists/master/bt_blocklists.gz",
            "Unlicense for the project; the entries come from other lists whose terms were not checked",
            "https://github.com/Naunter/BT_BlockLists",
            "A large p2p list merged from many public lists (named organisations that monitor torrent swarms, plus attackers). Not actively maintained according to its author (notice of 2024-10-22), but an automatic workflow still regenerates the file; check its date below.",
            skipReserved = true,
        ),
        BlockListPreset(
            "ip-spamhaus-drop", "Spamhaus DROP", BlockListKind.TORRENT_IP,
            "https://www.spamhaus.org/drop/drop.txt",
            "Free of charge, credit to The Spamhaus Project, keep the date and copyright text with the data",
            "https://www.spamhaus.org/drop/",
            "Networks leased or stolen by cybercrime operations. Security list, not a copyright list. Small and very precise.",
            skipReserved = true,
        ),
        BlockListPreset(
            "ip-firehol-level1", "FireHOL level 1", BlockListKind.TORRENT_IP,
            "https://raw.githubusercontent.com/firehol/blocklist-ipsets/master/firehol_level1.netset",
            "Combination of DShield (CC BY-NC-SA 2.5), Feodo Tracker (CC0), Spamhaus DROP and bogons; FireHOL says each source keeps its own terms",
            "https://iplists.firehol.org/?ipset=firehol_level1",
            "Attack and malware networks with very few false positives. Security list, not a copyright list. Private ranges are cut out.",
            skipReserved = true,
        ),
    )

    fun byId(id: String) = all.firstOrNull { it.id == id }
}
