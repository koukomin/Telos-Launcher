package de.mm20.launcher2.ui.keepass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ui.component.VirtualAppWindow
import kotlinx.serialization.Serializable

@Serializable
data object KeePassDashboardRoute : NavKey

@Composable
fun KeePassDashboardScreen() {
    VirtualAppWindow {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("KeePassTL")
        }
    }
}
