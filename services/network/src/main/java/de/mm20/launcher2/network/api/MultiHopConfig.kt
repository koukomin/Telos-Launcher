package de.mm20.launcher2.network.api

import kotlinx.serialization.Serializable

@Serializable
data class MultiHopConfig(
    val entryNodeId: Int,
    val exitNodeId: Int,
    val enabled: Boolean = true
)
