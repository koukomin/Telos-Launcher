package de.mm20.launcher2.ui.comms

import android.os.Bundle
import de.mm20.launcher2.comms.telephony.TelosDialer
import de.mm20.launcher2.ui.base.BaseActivity

class DialerHandleActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val number = intent?.data?.schemeSpecificPart.orEmpty()
        // This activity is exported, so any app can send it ACTION_CALL without holding CALL_PHONE.
        // Calls are therefore never placed from here: the number is put on the keypad (ACTION_DIAL
        // and ACTION_CALL alike) and the user presses call, which also keeps the call guard,
        // the biometric check and the blocklist in the path.
        runCatching { TelosDialer.openDialpad(this, number) }
        finish()
    }
}
