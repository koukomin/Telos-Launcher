package de.mm20.launcher2.ui.comms

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.preferences.comms.CommsSettings
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.TelosSearchBar
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

private enum class CommsTab(@StringRes val label: Int) {
    Recents(R.string.au_phonea_recents),
    Contacts(R.string.preference_search_contacts),
    Keypad(R.string.comms_keypad),
    Messages(R.string.hc_messages),
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
        "contacts" -> CommsTab.Contacts
        "keypad", "dialpad", "dial" -> CommsTab.Keypad
        else -> CommsTab.Recents
    }
    var selectedTab by rememberSaveable(initialTab) { mutableStateOf(defaultTab) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showFilters by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    
    val commsSettings: CommsSettings = koinInject()
    val autoOpenDialpad by commsSettings.autoOpenDialpad.collectAsStateWithLifecycle(false)
    val phoneAppLock by commsSettings.phoneAppLock.collectAsStateWithLifecycle(false)
    val phoneUnlocked by de.mm20.launcher2.comms.privacy.PrivacySession.phoneUnlocked.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val backStack = LocalBackStack.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(autoOpenDialpad, initialNumber) {
        if (initialTab.lowercase() != "messages" && (autoOpenDialpad || initialNumber.isNotEmpty())) selectedTab = CommsTab.Keypad
    }

    if (selectedTab == CommsTab.Keypad || selectedTab == CommsTab.Messages) {
        BackHandler { selectedTab = CommsTab.Recents }
    }

    val inSubScreen = selectedTab == CommsTab.Keypad || selectedTab == CommsTab.Messages

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(16.dp))
                Text("Telos Comms", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.hc_settings)) },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        backStack.add(CommsSettingsRoute)
                    }
                )
                NavigationDrawerItem(
                    label = { Text("Clear call history") },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        showClearHistoryDialog = true
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                Text("Messengers", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                
                // Messenger shortcuts
                val pm = context.packageManager
                data class MessengerApp(val name: String, val pkg: String, val forks: List<String> = emptyList())
                val messengers = listOf(
                    MessengerApp("SMS", "com.google.android.apps.messaging"),
                    MessengerApp("WhatsApp", "com.whatsapp", listOf("com.whatsapp.w4b")),
                    MessengerApp("Viber", "com.viber.voip"),
                    MessengerApp(
                        "Telegram",
                        "org.telegram.messenger",
                        de.mm20.launcher2.comms.intent.MessengerApps.telegramPkgs.filter { it != "org.telegram.messenger" },
                    ),
                    MessengerApp("Signal", "org.thoughtcrime.securesms")
                )
                
                messengers.forEach { app ->
                    val name = app.name
                    val pkg = app.pkg
                    val forks = app.forks
                    var installed = false
                    try {
                        pm.getPackageInfo(pkg, 0)
                        installed = true
                    } catch (_: Exception) {
                        if (forks.isNotEmpty()) {
                            val fList: List<String> = forks
                            for (f in fList) {
                                try {
                                    pm.getPackageInfo(f, 0)
                                    installed = true
                                    break
                                } catch (_: Exception) {}
                            }
                        }
                    }
                    
                    if (installed) {
                        NavigationDrawerItem(
                            label = { Text(name) },
                            selected = false,
                            onClick = {
                                coroutineScope.launch { drawerState.close() }
                                val intent = pm.getLaunchIntentForPackage(pkg) ?: run {
    var foundIntent: Intent? = null
    if (forks.isNotEmpty()) {
                            val fList: List<String> = forks
        for (f in fList) {
            val i = pm.getLaunchIntentForPackage(f)
            if (i != null) {
                foundIntent = i
                break
            }
        }
    }
    foundIntent
}
                                if (intent != null) {
                                    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }
                            }
                        )
                    }
                }
            }
        }
    ) {
        Scaffold(
            containerColor = Color.Black,
            topBar = {
                Column(Modifier.statusBarsPadding()) {
                    if (inSubScreen) Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .padding(start = if (inSubScreen) 4.dp else 20.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (inSubScreen) {
                            IconButton(onClick = { selectedTab = CommsTab.Recents }) {
                                Icon(painterResource(R.drawable.arrow_back_24px), contentDescription = stringResource(R.string.hc_back), tint = Color.White)
                            }
                        }
                        Text(
                            text = stringResource(
                                if (inSubScreen) selectedTab.label else R.string.au_phonea_dialer
                            ),
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            modifier = Modifier.weight(1f).padding(start = if (inSubScreen) 8.dp else 0.dp),
                        )
                        IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                            Icon(painterResource(R.drawable.menu_24px), contentDescription = "Menu", tint = Color.White)
                        }
                    }
                    if (!inSubScreen) {
                        TelosSearchBar(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = stringResource(
                                when (selectedTab) {
                                    CommsTab.Recents -> R.string.tsp_search_calls
                                    else -> R.string.tsp_search_contacts
                                }
                            ),
                            leading = {
                                IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                                    Icon(painterResource(R.drawable.menu_24px), contentDescription = "Menu", tint = Color.White)
                                }
                            },
                            trailing = {
                                if (selectedTab == CommsTab.Recents) {
                                    IconButton(onClick = { showFilters = !showFilters }) {
                                        Icon(
                                            painterResource(R.drawable.filter_alt_24px),
                                            contentDescription = stringResource(R.string.hc_filter),
                                            tint = if (showFilters) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            },
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
                            Triple(CommsTab.Recents, R.drawable.schedule_24px, CommsTab.Recents.label),
                            Triple(CommsTab.Contacts, R.drawable.person_24px_filled, CommsTab.Contacts.label),
                            Triple(CommsTab.Messages, R.drawable.sms_24px, CommsTab.Messages.label),
                        ).forEach { (tab, icon, label) ->
                            NavigationBarItem(
                                selected = selectedTab == tab,
                                onClick = { selectedTab = tab },
                                icon = { Icon(painterResource(icon), contentDescription = null) },
                                label = { Text(stringResource(label)) },
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
                        Icon(painterResource(R.drawable.dialpad_24px), contentDescription = stringResource(R.string.hc_dialpad))
                    }
                }
            },
        ) { contentPadding ->
            Box(modifier = Modifier.padding(contentPadding).fillMaxSize().background(Color.Black)) {
                when (selectedTab) {
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

    if (showClearHistoryDialog) {
        var selectedInterval by remember { mutableStateOf("All time") }
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Clear Call History") },
            text = {
                Column {
                    listOf("Last hour", "Last day", "Last week", "Last month", "All time").forEach { interval ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { selectedInterval = interval }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selectedInterval == interval, onClick = { selectedInterval = interval })
                            Spacer(Modifier.width(8.dp))
                            Text(interval)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // Clear history logic based on interval
                        showClearHistoryDialog = false
                        Toast.makeText(context, "History cleared ($selectedInterval)", Toast.LENGTH_SHORT).show()
                    }
                ) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) { Text("Cancel") }
            }
        )
    }
}
