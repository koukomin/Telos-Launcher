package de.mm20.launcher2.ui.media.akou

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.locals.LocalBackStack
import kotlinx.serialization.Serializable

@Serializable
data object AkouRoute : NavKey

/** Recognition remains unavailable until a real recorder and fingerprint provider are connected. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AkouScreen() {
    val backStack = LocalBackStack.current
    Scaffold(containerColor = MaterialTheme.colorScheme.surface, topBar = {
        TopAppBar(title = { Text("Άκου") }, navigationIcon = {
            IconButton(onClick = { backStack.removeLastOrNull() }) {
                Icon(painterResource(R.drawable.arrow_back_24px), stringResource(R.string.hc_back))
            }
        })
    }) { padding ->
        Text(stringResource(R.string.akou_not_implemented), Modifier.fillMaxWidth().padding(padding).padding(24.dp))
    }
}
