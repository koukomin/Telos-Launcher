package de.mm20.launcher2.applock

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Secret Vault controller disguised behind Calculator input sequences ("Fake Gate")
 * and featuring Ghost Mode with Duress PIN.
 *
 * Master PIN unlocks private storage and all hidden apps.
 * Duress PIN unlocks in "Ghost Mode" (plausible deniability) where sensitive
 * Virtual Apps (KeePass, Notes, Ermis, Backups) are completely filtered out.
 */
object TelosSecretVault {

    data class VaultState(
        val isUnlocked: Boolean = false,
        val isGhostModeActive: Boolean = false,
        val pinCode: String = "",
        val duressPinCode: String = "",
        val hiddenAppPackages: Set<String> = emptySet()
    ) {
        val isPinSet: Boolean get() = pinCode.length >= 4
        val isDuressPinSet: Boolean get() = duressPinCode.length >= 4
    }

    private val GHOST_HIDDEN_DOMAINS = setOf(
        "telos_keepass_app",
        "telos_notes_app",
        "telos_ermis_app",
        "telos_backup_app",
        "telos_voice_recorder_app"
    )

    private val _state = MutableStateFlow(VaultState())
    val state: StateFlow<VaultState> = _state.asStateFlow()

    /**
     * Evaluates a calculator expression/input.
     * If it matches `[PIN]=` or `[PIN]`, unlocks the secret vault normally.
     * If it matches `[DURESS_PIN]=` or `[DURESS_PIN]`, unlocks in Ghost Mode.
     */
    fun evaluateCalculatorExpression(inputExpression: String, context: Context): Boolean {
        val current = _state.value
        val cleanInput = inputExpression.trim().removeSuffix("=")

        // Check Duress PIN first
        if (current.isDuressPinSet && cleanInput == current.duressPinCode) {
            _state.value = current.copy(
                isUnlocked = true,
                isGhostModeActive = true
            )
            return true
        }

        // Check Master PIN
        if (current.isPinSet && cleanInput == current.pinCode) {
            _state.value = current.copy(
                isUnlocked = true,
                isGhostModeActive = false
            )
            return true
        }

        return false
    }

    /**
     * Checks if a domain or package should be hidden under current Ghost Mode policy.
     */
    fun isHidden(domainOrPackage: String): Boolean {
        val current = _state.value
        if (current.isGhostModeActive) {
            if (domainOrPackage in GHOST_HIDDEN_DOMAINS || domainOrPackage in current.hiddenAppPackages) {
                return true
            }
        }
        return !current.isUnlocked && domainOrPackage in current.hiddenAppPackages
    }

    fun lockVault() {
        _state.value = _state.value.copy(
            isUnlocked = false,
            isGhostModeActive = false
        )
    }

    fun setVaultPin(newPin: String) {
        val trimmed = newPin.trim()
        if (trimmed.length >= 4) {
            _state.value = _state.value.copy(pinCode = trimmed, isUnlocked = false)
        }
    }

    fun setDuressPin(newDuressPin: String) {
        val trimmed = newDuressPin.trim()
        if (trimmed.length >= 4) {
            _state.value = _state.value.copy(duressPinCode = trimmed)
        }
    }
}
