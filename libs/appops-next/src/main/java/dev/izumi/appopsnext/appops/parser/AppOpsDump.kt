package dev.izumi.appopsnext.appops.parser

/**
 * Android 15 and earlier print `Current AppOps Service state:`. ColorOS /
 * Android 16+ print `AppOps Service state:` and split uid data under
 * `AppOps Uid Op State`. Both are valid privileged dumps.
 */
object AppOpsDump {
    const val LEGACY_SERVICE_HEADER = "Current AppOps Service state:"
    const val SERVICE_HEADER = "AppOps Service state:"

    fun looksLikeServiceDump(output: String): Boolean {
        val header = output.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
            ?: return false
        return header == LEGACY_SERVICE_HEADER || header == SERVICE_HEADER
    }
}
