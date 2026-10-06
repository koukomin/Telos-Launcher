package de.mm20.launcher2.ui.comms

import android.content.Intent
import android.os.Bundle
import de.mm20.launcher2.comms.telephony.TelosDialer
import de.mm20.launcher2.ui.base.BaseActivity

class DialerHandleActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val number = intent.data?.schemeSpecificPart.orEmpty()
        when (intent.action) {
            Intent.ACTION_CALL -> TelosDialer.placeCall(this, number)
            else -> TelosDialer.openDialpad(this, number)
        }
        finish()
    }
}
