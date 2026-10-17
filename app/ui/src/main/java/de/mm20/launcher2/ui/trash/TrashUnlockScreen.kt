package de.mm20.launcher2.ui.trash

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.comms.AuthManager
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.locals.LocalBackStack
import kotlinx.serialization.Serializable

@Serializable
data object TrashUnlockRoute : NavKey

@Composable
fun TrashUnlockScreen() {
    val context = LocalContext.current
    val activity = LocalActivity.current as? FragmentActivity
    val backStack = LocalBackStack.current
    val title = stringResource(R.string.telos_trash_unlock)
    LaunchedEffect(Unit) {
        val ok = if (activity != null) AuthManager().authenticateNative(activity, title) else false
        if (!ok) {
            backStack.removeLastOrNull()
            return@LaunchedEffect
        }
        val launched = openOsTrash(context)
        if (!launched) {
            Toast.makeText(context, context.getString(R.string.telos_os_trash_unavailable), Toast.LENGTH_LONG).show()
        }
        backStack.removeLastOrNull()
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

private fun openOsTrash(context: android.content.Context): Boolean {
    val intents = buildList {
        add(Intent("com.google.android.apps.nbu.files.action.VIEW_TRASH").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        add(Intent(Intent.ACTION_VIEW).setClassName("com.google.android.apps.nbu.files", "com.google.android.apps.nbu.files.home.HomeActivity"))
        if (Build.VERSION.SDK_INT >= 26) {
            val trash = DocumentsContract.buildDocumentUri(
                "com.android.externalstorage.documents",
                "primary:.trashed",
            )
            add(Intent(Intent.ACTION_VIEW).setDataAndType(trash, DocumentsContract.Document.MIME_TYPE_DIR).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            add(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).putExtra(DocumentsContract.EXTRA_INITIAL_URI, trash))
        }
        add(Intent(Intent.ACTION_VIEW).setData(Uri.parse("content://com.android.externalstorage.documents/root/primary")))
    }
    for (intent in intents) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (context.tryStartActivity(intent)) return true
    }
    return false
}
