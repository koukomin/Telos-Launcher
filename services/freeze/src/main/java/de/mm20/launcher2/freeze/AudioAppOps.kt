package de.mm20.launcher2.freeze

internal enum class AudioOperation { TAKE_AUDIO_FOCUS, PLAY_AUDIO }
private val modePattern = Regex("(?m)^\\s*(TAKE_AUDIO_FOCUS|PLAY_AUDIO): (allow|ignore|deny|default|foreground)(?=[;\\s]|$)")
private val noAudioRecords = Regex("No operations\\.(?:\\s+Default mode: (?:allow|ignore|deny|default|foreground))?")
private val validAudioPackage = Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)*")

/** Never mix package writes with UID overrides, even if both currently have the same mode. */
internal fun parseAudioMode(output: String, operation: AudioOperation): String? {
    if (output.length > 8192 || output.contains("Uid mode:")) return null
    if (noAudioRecords.matches(output.trim())) return "default"
    val rows = modePattern.findAll(output).toList()
    return rows.singleOrNull()?.takeIf { it.groupValues[1] == operation.name }?.groupValues?.get(2)
}

internal fun audioCommand(pkg: String, user: Int, operation: AudioOperation, mode: String? = null): List<String> {
    require(validAudioPackage.matches(pkg) && user >= 0)
    require(mode == null || mode in setOf("allow", "ignore", "default", "deny", "foreground"))
    return listOf("cmd", "appops", if (mode == null) "get" else "set", "--user", user.toString(), pkg, operation.name) + listOfNotNull(mode)
}
