package de.mm20.launcher2.applock

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Secret Vault controller disguised behind Calculator input sequences ("Fake Gate").
 * Evaluates calculator inputs (e.g. `1234=`) to securely unlock hidden apps, notes, and private files.
 */
object TelosSecretVault {

    private const val TAG = "TelosSecretVault"
    private const val DEFAULT_SECRET_PIN = "1234"

    data class VaultState(
        val isUnlocked: Boolean = false,
        val pinCode: String = DEFAULT_SECRET_PIN,
        val hiddenAppPackages: Set<String> = emptySet()
    )

    private val _state = MutableStateFlow(VaultState())
    val state: StateFlow<VaultState> = _state.asStateFlow()

    /**
     * Evaluates a calculator expression/input. If it matches `[PIN]=` or `[PIN]`, unlocks the secret vault.
     */
    fun evaluateCalculatorExpression(inputExpression: String, context: Context): Boolean {
        val cleanInput = inputExpression.trim().removeSuffix("=")
        val currentPin = _state.value.pinCode

        return if (cleanInput == currentPin) {
            _state.value = _state.value.copy(isUnlocked = true)
            Log.i(TAG, "Secret Vault successfully unlocked via Calculator gate!")
            true
        } else {
            false
        }
    }

    fun lockVault() {
        _state.value = _state.value.copy(isUnlocked = false)
        Log.i(TAG, "Secret Vault locked")
    }

    fun setVaultPin(newPin: String) {
        if (newPin.length >= 4) {
            _state.value = _state.value.copy(pinCode = newPin)
            Log.i(TAG, "Vault PIN updated")
        }
    }
}
