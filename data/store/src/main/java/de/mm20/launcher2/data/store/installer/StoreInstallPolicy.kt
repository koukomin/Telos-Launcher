package de.mm20.launcher2.data.store.installer

/** Which privileged backends to try before the unprivileged session installer. */
object StoreInstallPolicy {
    fun backends(selected: String, shizukuReady: Boolean, rootReady: Boolean): List<String> {
        return when (selected) {
            "shizuku" -> listOfNotNull("shizuku".takeIf { shizukuReady })
            "root" -> listOfNotNull("root".takeIf { rootReady })
            else -> buildList {
                if (shizukuReady) add("shizuku")
                if (rootReady) add("root")
            }
        }
    }

    fun canInstallSilently(
        selected: String,
        shizukuReady: Boolean,
        rootReady: Boolean,
        deviceOwner: Boolean,
    ): Boolean = shizukuReady || rootReady || (selected == "session" && deviceOwner)
}
