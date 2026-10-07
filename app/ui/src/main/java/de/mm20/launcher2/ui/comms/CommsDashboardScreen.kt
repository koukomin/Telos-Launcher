package de.mm20.launcher2.ui.comms

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

@Serializable
data class CommsDashboardRoute(
    val initialTab: String = "recents",
    val initialNumber: String = "",
    val initialBody: String = "",
    val initialAttachments: List<String> = emptyList(),
) : NavKey

private enum class CommsTab(val label: String) {
    Favorites("Favorites"),
    Recents("Recents"),
    Contacts("Contacts"),
    Keypad("Keypad"),
    Messages("Messages"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommsDashboardScreen(
    initialTab: String = "recents",
    initialNumber: String = "",
    initialBody: String = "",
    initialAttachments: List<String> = emptyList(),
) {
    val defaultTab = when (initialTab.lowercase()) {
        "messages" -> CommsTab.Messages
        "favorites" -> CommsTab.Favorites
        "contacts" -> CommsTab.Contacts
        "keypad", "dialpad", "dial" -> CommsTab.Keypad
        else -> CommsTab.Recents
    }
    var selectedTab by remember(initialTab) { mutableStateOf(defaultTab) }
    var searchQuery by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    val commsSettings: CommsSettings = koinInject()
    val autoOpenDialpad by commsSettings.autoOpenDialpad.collectAsStateWithLifecycle(false)
    val phoneAppLock by commsSettings.phoneAppLock.collectAsStateWithLifecycle(false)
    val phoneUnlocked by de.mm20.launcher2.comms.privacy.PrivacySession.phoneUnlocked.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val backStack = LocalBackStack.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        val snap = commsSettings.snapshot.first()
        val stale = System.currentTimeMillis() - de.mm20.launcher2.comms.remote.RemotePhonebook.lastSyncMillis > 6 * 3600_000L
        if (snap.remotePhonebookEnabled && snap.remotePhonebookUser.isNotBlank() && stale) {
            de.mm20.launcher2.comms.remote.RemotePhonebook.sync(
                snap.remotePhonebookHost,
                snap.remotePhonebookUser,
                de.mm20.launcher2.comms.remote.SecretBox.decrypt(snap.remotePhonebookPasswordEnc),
            )
        }
    }
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
        // a number given to the messages tab is the one to write to, not one to dial
        if (initialTab.lowercase() != "messages" && (autoOpenDialpad || initialNumber.isNotEmpty())) selectedTab = CommsTab.Keypad
    }

    if (selectedTab == CommsTab.Keypad || selectedTab == CommsTab.Messages) {
        BackHandler { selectedTab = CommsTab.Recents }
    }

    val inSubScreen = selectedTab == CommsTab.Keypad || selectedTab == CommsTab.Messages

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(Modifier.statusBarsPadding()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(start = if (inSubScreen) 4.dp else 20.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (inSubScreen) {
                        IconButton(onClick = { selectedTab = CommsTab.Recents }) {
                            Icon(painterResource(R.drawable.arrow_back_24px), contentDescription = null)
                        }
                    }
                    Text(
                        text = if (selectedTab == CommsTab.Keypad) "" else if (inSubScreen) selectedTab.label else "Dialer",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f).padding(start = if (inSubScreen) 8.dp else 0.dp),
                    )
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(painterResource(R.drawable.more_vert_24px), contentDescription = null)
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Messages") },
                                onClick = { menuOpen = false; selectedTab = CommsTab.Messages },
                            )
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                onClick = { menuOpen = false; backStack.add(CommsSettingsRoute) },
                            )
                        }
                    }
                }
                if (!inSubScreen) {
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .heightIn(min = 48.dp),
                        placeholder = { Text("Search") },
                        leadingIcon = {
                            Icon(painterResource(R.drawable.search_24px), contentDescription = null)
                        },
                        trailingIcon = if (selectedTab == CommsTab.Recents) {
                            {
                                IconButton(onClick = { showFilters = !showFilters }) {
                                    Icon(
                                        painterResource(R.drawable.filter_alt_24px),
                                        contentDescription = "Filter",
                                        tint = if (showFilters) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        } else null,
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                    )
                }
            }
        },
        bottomBar = {
            if (!inSubScreen) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 0.dp,
                ) {
                    listOf(
                        Triple(CommsTab.Favorites, R.drawable.star_24px, "Favorites"),
                        Triple(CommsTab.Recents, R.drawable.schedule_24px, "Recents"),
                        Triple(CommsTab.Contacts, R.drawable.person_24px_filled, "Contacts"),
                    ).forEach { (tab, icon, label) ->
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            icon = { Icon(painterResource(icon), contentDescription = null) },
                            label = { Text(label) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (!inSubScreen) {
                FloatingActionButton(
                    onClick = { selectedTab = CommsTab.Keypad },
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(painterResource(R.drawable.dialpad_24px), contentDescription = "Dialpad")
                }
            }
        },
    ) { contentPadding ->
        Box(modifier = Modifier.padding(contentPadding).fillMaxSize()) {
            when (selectedTab) {
                CommsTab.Favorites -> ContactsScreen(
                    searchQuery = searchQuery,
                    starredOnly = true,
                    showLocalSearch = false,
                    showAddFab = false,
                )
                CommsTab.Recents -> RecentsScreen(searchQuery = searchQuery, showFilters = showFilters)
                CommsTab.Contacts -> ContactsScreen(
                    searchQuery = searchQuery,
                    showLocalSearch = false,
                    showAddFab = false,
                )
                CommsTab.Keypad -> DialpadScreen(initialNumber = initialNumber)
                CommsTab.Messages -> MessagesScreen(
                    initialNumber = if (initialTab.lowercase() == "messages") initialNumber else "",
                    initialBody = initialBody,
                    initialAttachments = initialAttachments,
                )
            }
        }
    }
}
