package de.mm20.launcher2.search

/**
 * Universal Omnibar Quick Action Parser for Telos Search.
 * Instantly evaluates terminal-like directives, calculations, and system toggles
 * directly inside the unified launcher search bar.
 */
object OmnibarQuickActions {

    sealed class QuickAction {
        data class SystemToggle(val target: String, val enable: Boolean) : QuickAction()
        data class FreezeApp(val packageName: String) : QuickAction()
        data object LockVault : QuickAction()
        data object TriggerGhostMode : QuickAction()
        data class QuickMath(val expression: String) : QuickAction()
    }

    /**
     * Parses search input string and detects if it maps to an instant omnibar action.
     */
    fun parse(query: String): QuickAction? {
        val q = query.trim().lowercase()

        // 1. Vault commands
        if (q == "lock" || q == ":lock") return QuickAction.LockVault
        if (q == "ghost" || q == ":ghost") return QuickAction.TriggerGhostMode

        // 2. System toggles
        if (q in setOf("wifi on", "wifi:on")) return QuickAction.SystemToggle("wifi", true)
        if (q in setOf("wifi off", "wifi:off")) return QuickAction.SystemToggle("wifi", false)
        if (q in setOf("bt on", "bluetooth on")) return QuickAction.SystemToggle("bluetooth", true)
        if (q in setOf("bt off", "bluetooth off")) return QuickAction.SystemToggle("bluetooth", false)
        if (q in setOf("torch on", "flashlight on")) return QuickAction.SystemToggle("flashlight", true)
        if (q in setOf("torch off", "flashlight off")) return QuickAction.SystemToggle("flashlight", false)

        // 3. App freeze directive: e.g. "freeze com.facebook.katana"
        if (q.startsWith("freeze ") || q.startsWith(":freeze ")) {
            val pkg = q.substringAfter("freeze ").trim()
            if (pkg.isNotEmpty()) return QuickAction.FreezeApp(pkg)
        }

        // 4. Quick Math detection
        if (q.matches(Regex("^[0-9+\\-*/^.()\\s]+$")) && q.any { it in "+-*/^" }) {
            return QuickAction.QuickMath(q)
        }

        return null
    }
}
