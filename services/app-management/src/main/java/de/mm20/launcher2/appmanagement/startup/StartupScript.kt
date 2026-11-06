/* SPDX-License-Identifier: GPL-3.0-or-later */
package de.mm20.launcher2.appmanagement.startup

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
enum class ScriptIdentity { App, Shizuku, Root }

@Serializable
data class StartupScript(
    val id: String,
    val name: String,
    val body: String,
    val identity: ScriptIdentity = ScriptIdentity.App,
    val enabled: Boolean = false,
) {
    fun mayStartAutomatically(expected: StartupScript): Boolean = enabled && this == expected

    fun validated(): StartupScript {
        require(id.matches(ID_PATTERN))
        require(name.isNotBlank() && name.length <= 80)
        require(body.isNotBlank() && body.length <= 16_384 && '\u0000' !in body)
        return this
    }
}

data class ScriptRun(val id: String, val exitCode: Int, val output: String, val timedOut: Boolean = false)

private val ID_PATTERN = Regex("[a-zA-Z0-9-]{1,64}")

internal fun encodeStartupScripts(scripts: List<StartupScript>): ByteArray {
    require(scripts.size <= 10 && scripts.map { it.id }.distinct().size == scripts.size)
    scripts.forEach { it.validated() }
    return Json.encodeToString(scripts).toByteArray(Charsets.UTF_8).also {
        require(it.size <= 1024 * 1024) { "Oversized script storage" }
    }
}
