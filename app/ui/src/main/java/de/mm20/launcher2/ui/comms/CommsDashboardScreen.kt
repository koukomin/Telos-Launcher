package de.mm20.launcher2.ui.comms

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.preferences.comms.CommsSettings
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.locals.LocalBackStack
import de.mm20.launcher2.ui.settings.comms.CommsSettingsRoute
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

@Serializable
data class CommsDashboardRoute(
    val initialTab: String = "recents",
    val initialNumber: String = "",
) : NavKey

private enum class CommsTab(val label: String) {
    Recents("Recents"),
    Contacts("Contacts"),
    Keypad("Keypad"),
    Messages("Messages"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommsDashboardScreen(initialTab: String = "recents", initialNumber: String = "") {
    val defaultTab = when (initialTab.lowercase()) {
        "messages" -> CommsTab.Messages
        "favorites", "contacts" -> CommsTab.Contacts
        "keypad", "dialpad", "dial" -> CommsTab.Keypad
        else -> CommsTab.Recents
    }
    var selectedTab by remember(initialTab) { mutableStateOf(defaultTab) }
    var searchQuery by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    val commsSettings: CommsSettings = koinInject()
    val autoOpenDialpad by commsSettings.autoOpenDialpad.collectAsStateWithLifecycle(false)
    val phoneAppLock by commsSettings.phoneAppLock.collectAsStateWithLifecycle(false)
    val phoneUnlocked by de.mm20.launcher2.comms.privacy.PrivacySession.phoneUnlocked.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val backStack = LocalBackStack.current
    val scope = rememberCoroutineScope()
    if (phoneAppLock && !phoneUnlocked) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Phone is locked", style = MaterialTheme.typography.titleLarge)
                Button(
                    onClick = {
                        val activity = context as? androidx.fragment.app.FragmentActivity ?: return@Button
                        scope.launch {
                            val ok = de.mm20.launcher2.comms.AuthManager()
                                .authenticateNative(activity, "Unlock Phone")
                            if (ok) de.mm20.launcher2.comms.privacy.PrivacySession.unlockPhone()
                        }
                    },
                    modifier = Modifier.padding(top = 16.dp),
                ) { Text("Unlock") }
            }
        }
        return
    }
    LaunchedEffect(autoOpenDialpad, initialNumber) {
        if (autoOpenDialpad || initialNumber.isNotEmpty()) selectedTab = CommsTab.Keypad
    }

    if (searchOpen) {
        BackHandler {
            searchQuery = ""
            searchOpen = false
        }
    }

    val searching = searchOpen && searchQuery.isNotBlank()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = {
                    if (searchOpen && selectedTab != CommsTab.Messages && selectedTab != CommsTab.Keypad) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(28.dp)),
                            placeholder = { Text(stringResource(R.string.comms_search_contacts)) },
                            singleLine = true,
                            shape = RoundedCornerShape(28.dp),
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ),
                        )
                    } else {
                        Text(
                            text = selectedTab.label,
                            style = MaterialTheme.typography.headlineSmall,
                        )
                    }
                },
                navigationIcon = {
                    if (searchOpen && selectedTab != CommsTab.Keypad) {
                        IconButton(onClick = {
                            searchQuery = ""
                            searchOpen = false
                        }) {
                            Icon(
                                painterResource(R.drawable.arrow_back_24px),
                                contentDescription = null,
                            )
                        }
                    }
                },
                actions = {
                    if (selectedTab != CommsTab.Messages && selectedTab != CommsTab.Keypad && !searchOpen) {
                        IconButton(onClick = { searchOpen = true }) {
                            Icon(
                                painterResource(R.drawable.search_24px),
                                contentDescription = stringResource(R.string.comms_search_contacts),
                            )
                        }
                    }
                    IconButton(onClick = { backStack.add(CommsSettingsRoute) }) {
                        Icon(
                            painterResource(R.drawable.settings_24px),
                            contentDescription = "Settings",
                        )
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 0.dp,
            ) {
                NavigationBarItem(
                    selected = selectedTab == CommsTab.Recents && !searching,
                    onClick = {
                        selectedTab = CommsTab.Recents
                        searchOpen = false
                        searchQuery = ""
                    },
                    icon = {
                        Icon(
                            painterResource(R.drawable.rd_ic_call_received_vector),
                            contentDescription = null,
                        )
                    },
                    label = { Text(CommsTab.Recents.label) },
                )
                NavigationBarItem(
                    selected = selectedTab == CommsTab.Contacts || searching,
                    onClick = {
                        selectedTab = CommsTab.Contacts
                        searchOpen = false
                        searchQuery = ""
                    },
                    icon = {
                        Icon(painterResource(R.drawable.person_24px), contentDescription = null)
                    },
                    label = { Text(CommsTab.Contacts.label) },
                )
                NavigationBarItem(
                    selected = selectedTab == CommsTab.Keypad,
                    onClick = {
                        selectedTab = CommsTab.Keypad
                        searchOpen = false
                        searchQuery = ""
                    },
                    icon = {
                        Icon(painterResource(R.drawable.dialpad_24px), contentDescription = null)
                    },
                    label = { Text(CommsTab.Keypad.label) },
                )
                NavigationBarItem(
                    selected = selectedTab == CommsTab.Messages,
                    onClick = {
                        selectedTab = CommsTab.Messages
                        searchOpen = false
                        searchQuery = ""
                    },
                    icon = {
                        Icon(painterResource(R.drawable.rd_ic_messages), contentDescription = null)
                    },
                    label = { Text(CommsTab.Messages.label) },
                )
            }
        },
    ) { contentPadding ->
        Box(modifier = Modifier.padding(contentPadding).fillMaxSize()) {
            when {
                searching -> ContactsScreen(
                    searchQuery = searchQuery,
                    showLocalSearch = false,
                    showAddFab = false,
                )
                selectedTab == CommsTab.Recents -> RecentsScreen(searchQuery = searchQuery)
                selectedTab == CommsTab.Contacts -> ContactsScreen(
                    searchQuery = searchQuery,
                    showLocalSearch = false,
                )
                selectedTab == CommsTab.Keypad -> DialpadScreen(initialNumber = initialNumber)
                selectedTab == CommsTab.Messages -> MessagesScreen()
            }
        }
    }
}
