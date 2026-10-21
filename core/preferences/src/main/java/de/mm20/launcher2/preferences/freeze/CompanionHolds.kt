package de.mm20.launcher2.preferences.freeze

/** Legacy single-owner values and new shared-owner values use the same portable representation. */
internal fun addCompanionOwners(holds: Map<String, String>, master: String, companions: Collection<String>): Map<String, String> =
    holds.toMutableMap().apply {
        companions.forEach { pkg ->
            this[pkg] = (this[pkg].orEmpty().split(',').filter { it.isNotBlank() }.toSet() + master)
                .sorted().joinToString(",")
        }
    }

/** Released contains only companions whose final owner has gone. */
internal fun removeCompanionOwner(holds: Map<String, String>, master: String): Pair<Map<String, String>, Set<String>> {
    val remaining = holds.mapValues { (_, owners) ->
        owners.split(',').filter { it.isNotBlank() && it != master }.joinToString(",")
    }
    val released = remaining.filterValues { it.isEmpty() }.keys
    return (remaining - released) to released
}
