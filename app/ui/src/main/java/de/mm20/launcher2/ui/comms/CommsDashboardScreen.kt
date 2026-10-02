package de.mm20.launcher2.ui.comms

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ui.R
import kotlinx.serialization.Serializable

@Serializable
data object CommsDashboardRoute : NavKey

private enum class CommsTab(val label: String) {
    Recents("Recents"),
    Dialpad("Dialpad"),
    Messages("Messages"),
    Contacts("Contacts"),
}

@Composable
fun CommsDashboardScreen() {
    var selectedTab by remember { mutableStateOf(CommsTab.Dialpad) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(selectedTab.label) })
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == CommsTab.Recents,
                    onClick = { selectedTab = CommsTab.Recents },
                    icon = { Icon(painterResource(R.drawable.call_24px), contentDescription = null) },
                    label = { Text(CommsTab.Recents.label) },
                )
                NavigationBarItem(
                    selected = selectedTab == CommsTab.Dialpad,
                    onClick = { selectedTab = CommsTab.Dialpad },
                    icon = { Icon(painterResource(R.drawable.dialpad_24px), contentDescription = null) },
                    label = { Text(CommsTab.Dialpad.label) },
                )
                NavigationBarItem(
                    selected = selectedTab == CommsTab.Messages,
                    onClick = { selectedTab = CommsTab.Messages },
                    icon = { Icon(painterResource(R.drawable.sms_24px), contentDescription = null) },
                    label = { Text(CommsTab.Messages.label) },
                )
                NavigationBarItem(
                    selected = selectedTab == CommsTab.Contacts,
                    onClick = { selectedTab = CommsTab.Contacts },
                    icon = { Icon(painterResource(R.drawable.person_24px), contentDescription = null) },
                    label = { Text(CommsTab.Contacts.label) },
                )
            }
        },
    ) { contentPadding ->
        Box(modifier = Modifier.padding(contentPadding)) {
            when (selectedTab) {
                CommsTab.Recents -> RecentsScreen()
                CommsTab.Dialpad -> DialpadScreen()
                CommsTab.Messages -> MessagesScreen()
                CommsTab.Contacts -> ContactsScreen()
            }
        }
    }
}
