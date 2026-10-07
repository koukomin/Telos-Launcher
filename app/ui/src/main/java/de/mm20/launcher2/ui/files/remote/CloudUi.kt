package de.mm20.launcher2.ui.files.remote

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/** The sign-in fields of the cloud storages. */
@Composable
internal fun CloudFields(c: RemoteConnection, onChange: (RemoteConnection) -> Unit, onStatus: (String) -> Unit) {
    Text("Cloud sign-in is not available yet.")
}
