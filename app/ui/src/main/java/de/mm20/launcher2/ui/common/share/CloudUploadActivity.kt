package de.mm20.launcher2.ui.common.share

import android.os.Bundle
import androidx.activity.compose.setContent
import de.mm20.launcher2.ui.base.BaseActivity
import de.mm20.launcher2.ui.base.ProvideCompositionLocals
import de.mm20.launcher2.ui.files.CloudUploadUriDialog
import de.mm20.launcher2.ui.theme.LauncherTheme

/** Entered only via the Telos share sheet's immutable action. */
class CloudUploadActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent.data?.takeIf { it.scheme == "content" && it.authority == "$packageName.fileprovider" }
        if (uri == null) {
            finish()
            return
        }
        setContent {
            ProvideCompositionLocals {
                LauncherTheme {
                    CloudUploadUriDialog(uri, ::finish)
                }
            }
        }
    }
}
