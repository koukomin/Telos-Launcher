package de.mm20.launcher2.ktx

import kotlinx.serialization.json.Json

/**
 * Singleton [Json] configuration for Telos Launcher.
 * Used universally across the project to avoid duplicate parser instantiations.
 */
object TelosJsonParser {

    val instance: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    val Lenient: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
        coerceInputValues = true
    }

}
