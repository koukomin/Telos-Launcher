package com.vitorpamplona.amethyst.ui.tor

/**
 * Global bridge allowing host apps (like Telos Launcher) to provide their unified [TorBackend]
 * implementation, avoiding duplicate native Tor daemons across modules.
 */
object TorBackendBridge {
    @Volatile
    var provider: (() -> TorBackend)? = null
}
