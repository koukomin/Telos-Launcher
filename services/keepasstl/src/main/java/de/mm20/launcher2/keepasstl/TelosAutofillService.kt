package de.mm20.launcher2.keepasstl

import android.app.assist.AssistStructure
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest

class TelosAutofillService : AutofillService() {
    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        // Find fields like username, email, password from assist structure
        // Present datasets containing credentials if unlocked
        // Will be fleshed out with UI
        callback.onSuccess(FillResponse.Builder().build())
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        // Collect credentials from AssistStructure and save them to KeePassTL Database
        callback.onSuccess()
    }
}
