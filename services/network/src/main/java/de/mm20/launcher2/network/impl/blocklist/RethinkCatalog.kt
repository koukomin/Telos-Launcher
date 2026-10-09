/*
 * Parsing of the Rethink DNS blocklist catalog (`filetag.json`).
 *
 * Adapted from RethinkDNS (https://github.com/celzero/rethink-app), RethinkBlocklistManager.kt and
 * FileTag.kt. Copyright 2022 RethinkDNS and its authors, licensed under the Apache License,
 * Version 2.0 (https://www.apache.org/licenses/LICENSE-2.0).
 */
package de.mm20.launcher2.network.impl.blocklist

import de.mm20.launcher2.network.api.Blocklist
import de.mm20.launcher2.network.api.BlocklistCategory
import de.mm20.launcher2.network.api.BlocklistGroup
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

internal object RethinkCatalog {
    const val OTHERS = "others"

    /**
     * Reads `filetag.json`: an object whose values describe one list each (`value` = the number used
     * in stamps, `vname` = display name, `group` = privacy | security | parentalcontrol, `subg` =
     * subgroup, `url` = string or array, `entries` = number of domains).
     */
    fun parse(text: String): List<BlocklistGroup> {
        val root = Json.parseToJsonElement(text).jsonObject
        val byGroup = LinkedHashMap<String, MutableList<Blocklist>>()
        val sections = HashMap<String, String>()
        for ((_, element) in root) {
            val o = element as? JsonObject ?: continue
            val value = o["value"]?.jsonPrimitive?.longOrNull ?: continue
            val name = o["vname"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: value.toString()
            val section = (o["group"]?.jsonPrimitive?.contentOrNull ?: "").lowercase().ifEmpty { "privacy" }
            val sub = (o["subg"]?.jsonPrimitive?.contentOrNull ?: "").lowercase().ifEmpty { OTHERS }
            val url = when (val u = o["url"]) {
                is JsonArray -> (u.firstOrNull() as? JsonPrimitive)?.contentOrNull
                is JsonPrimitive -> u.contentOrNull
                else -> null
            }.orEmpty()
            val entries = o["entries"]?.jsonPrimitive?.longOrNull ?: 0L
            val id = "$section/$sub"
            sections[id] = section
            byGroup.getOrPut(id) { mutableListOf() }.add(Blocklist(value.toString(), name, "", entries, url))
        }
        return byGroup.map { (id, lists) ->
            val section = sections.getValue(id)
            val sub = id.substringAfter('/')
            BlocklistGroup(
                id = id,
                name = pretty(sub),
                category = categoryOf(section, sub),
                lists = lists.sortedBy { it.name.lowercase() },
                section = section,
            )
        }.sortedWith(compareBy({ sectionOrder(it.section) }, { it.id.endsWith("/$OTHERS") }, { it.name }))
    }

    private fun sectionOrder(section: String) = when (section) {
        "parentalcontrol" -> 0
        "security" -> 1
        "privacy" -> 2
        else -> 3
    }

    fun pretty(sub: String): String =
        sub.replace('-', ' ').replace('_', ' ').trim().replaceFirstChar { it.titlecase() }

    fun categoryOf(section: String, sub: String): BlocklistCategory = when {
        sub == "porn" || sub == "dating" -> BlocklistCategory.Adult
        sub == "gambling" -> BlocklistCategory.Gambling
        sub == "social-networks" -> BlocklistCategory.Social
        section == "security" -> BlocklistCategory.Malware
        section == "privacy" -> BlocklistCategory.Trackers
        else -> BlocklistCategory.Other
    }
}
