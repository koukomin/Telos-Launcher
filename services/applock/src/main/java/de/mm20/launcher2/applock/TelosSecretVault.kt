package de.mm20.launcher2.applock

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Secret Vault controller disguised behind Calculator input sequences ("Fake Gate").
 * Evaluates calculator inputs (e.g. `1234=`) to securely unlock hidden apps, notes, and private files.
 */
object TelosSecretVault {

    data class VaultState(
        val isUnlocked: Boolean = false,
        val pinCode: String = "",
        val hiddenAppPackages: Set<String> = emptySet()
    ) {
        val isPinSet: Boolean get() = pinCode.length >= 4
    }

    private val _state = MutableStateFlow(VaultState())
    val state: StateFlow<VaultState> = _state.asStateFlow()

    /**
     * Evaluates a calculator expression/input. If it matches `[PIN]=` or `[PIN]`, unlocks the secret vault.
     */
    fun evaluateCalculatorExpression(inputExpression: String, context: Context): Boolean {
        val current = _state.value
        if (!current.isPinSet) return false
        val cleanInput = inputExpression.trim().removeSuffix("=")
        return if (cleanInput == current.pinCode) {
            _state.value = current.copy(isUnlocked = true)
            true
        } else {
            false
        }
    }

    fun lockVault() {
        _state.value = _state.value.copy(isUnlocked = false)
    }

    fun setVaultPin(newPin: String) {
        val trimmed = newPin.trim()
        if (trimmed.length >= 4) {
            _state.value = _state.value.copy(pinCode = trimmed, isUnlocked = false)
        }
    }
}
