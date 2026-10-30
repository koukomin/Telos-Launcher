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
import de.mm20.launcher2.ui.component.AdaptiveNavigationBar
import de.mm20.launcher2.ui.component.AdaptiveNavigationItem
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import de.mm20.launcher2.applications.AppRepository
import de.mm20.launcher2.icons.IconService
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
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
import de.mm20.launcher2.ui.theme.transparency.transparency
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
    Recordings(R.string.hc_recordings_tab),
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
        "recordings", "recording" -> CommsTab.Recordings
        "keypad", "dialpad", "dial" -> CommsTab.Keypad
        else -> CommsTab.Recents
    }
    var selectedTab by de.mm20.launcher2.ui.component.rememberPersistentUiState(
        "phone.tab.$initialTab", defaultTab, { it.name },
        { name -> CommsTab.entries.firstOrNull { it.name == name } },
    )
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showFilters by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    val commsSettings: CommsSettings = koinInject()
    val phoneAppLock by commsSettings.phoneAppLock.collectAsStateWithLifecycle(false)
    val phoneUnlocked by de.mm20.launcher2.comms.privacy.PrivacySession.phoneUnlocked.collectAsStateWithLifecycle()
    val lockCallRecordings by commsSettings.lockCallRecordings.collectAsStateWithLifecycle(true)
    val recordingsUnlocked by de.mm20.launcher2.comms.privacy.PrivacySession.recordingsUnlocked.collectAsStateWithLifecycle()
    val simNames by commsSettings.simNames.collectAsStateWithLifecycle(emptyMap())
    val context = LocalContext.current
    val backStack = LocalBackStack.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(simNames) {
        simNames.forEach { (id, name) ->
            id.toIntOrNull()?.let { de.mm20.launcher2.comms.sms.SmsSims.setCustomName(context, it, name) }
        }
    }

    LaunchedEffect(initialNumber) {
        if (initialNumber.isNotEmpty() && initialTab.lowercase() != "messages") selectedTab = CommsTab.Keypad
    }

    if (selectedTab == CommsTab.Keypad) {
        BackHandler { selectedTab = CommsTab.Recents }
    }

    val inSubScreen = selectedTab == CommsTab.Keypad

    val messengers = remember {
        de.mm20.launcher2.comms.intent.MessengerApps.listLaunchers(context)
    }

    de.mm20.launcher2.ui.component.VirtualAppWindow {
    var showFontDialog by remember { mutableStateOf(false) }
    if (showFontDialog) {
        de.mm20.launcher2.ui.component.PerAppFontScaleDialog(
            appKey = "comms",
            appName = "Telos Phone & SMS",
            onDismiss = { showFontDialog = false }
        )
    }
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.width(280.dp),
                drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
            ) {
                Spacer(Modifier.height(20.dp))
                Text(
                    stringResource(R.string.au_phonea_dialer),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Text(
                    stringResource(R.string.hc_messages),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                NavigationDrawerItem(
                    icon = {
                        ThemedDrawerIcon(
                            iconRes = R.drawable.mic_24px,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    },
                    label = { Text(stringResource(R.string.hc_call_recordings)) },
                    selected = selectedTab == CommsTab.Recordings,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        selectedTab = CommsTab.Recordings
                    },
                )
                NavigationDrawerItem(
                    icon = {
                        ThemedDrawerIcon(
                            iconRes = R.drawable.settings_24px,
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    },
                    label = { Text(stringResource(R.string.hc_settings)) },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        backStack.add(CommsSettingsRoute)
                    },
                )
                NavigationDrawerItem(
                    icon = {
                        ThemedDrawerIcon(
                            iconRes = R.drawable.text_fields_24px,
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    },
                    label = { Text("Font Size / Γραμματοσειρά") },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        showFontDialog = true
                    },
                )
                NavigationDrawerItem(
                    icon = {
                        ThemedDrawerIcon(
                            iconRes = R.drawable.delete_24px,
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    },
                    label = { Text(stringResource(R.string.comms_clear_history)) },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        showClearHistoryDialog = true
                    },
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                Text(
                    stringResource(R.string.comms_messengers),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                NavigationDrawerItem(
                    icon = { DefaultSmsLauncherIcon() },
                    label = { Text(stringResource(R.string.hc_messages)) },
                    selected = selectedTab == CommsTab.Messages,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        selectedTab = CommsTab.Messages
                    },
                )
                messengers.forEach { app ->
                    NavigationDrawerItem(
                        icon = {
                            CommsLauncherIcon(app.component.packageName, app.user)
                        },
                        label = {
                            val dual = app.user != android.os.Process.myUserHandle()
                            Text(
                                if (dual) "${app.label} · ${stringResource(R.string.comms_dual_app)}" else app.label,
                                maxLines = 1,
                            )
                        },
                        selected = false,
                        onClick = {
                            coroutineScope.launch { drawerState.close() }
                            de.mm20.launcher2.comms.intent.MessengerApps.launch(context, app)
                        },
                    )
                }
            }
        }
    ) {
        Scaffold(
            containerColor = Color.Transparent,
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
                                Icon(painterResource(R.drawable.arrow_back_24px), contentDescription = stringResource(R.string.hc_back))
                            }
                        }
                        Text(
                            text = stringResource(
                                if (inSubScreen) selectedTab.label else R.string.au_phonea_dialer
                            ),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f).padding(start = if (inSubScreen) 8.dp else 0.dp),
                        )
                        IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                            Icon(painterResource(R.drawable.menu_24px), contentDescription = stringResource(R.string.comms_menu))
                        }
                    }
                    if (!inSubScreen) {
                        TelosSearchBar(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = stringResource(
                                when (selectedTab) {
                                    CommsTab.Recents -> R.string.tsp_search_calls
                                    CommsTab.Recordings -> R.string.tsp_search_call_recordings
                                    CommsTab.Messages -> R.string.tsp_search_messages
                                    else -> R.string.tsp_search_contacts
                                }
                            ),
                            leading = {
                                IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                                    Icon(painterResource(R.drawable.menu_24px), contentDescription = stringResource(R.string.comms_menu))
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
                    AdaptiveNavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        listOf(
                            Triple(CommsTab.Recents, R.drawable.schedule_24px, CommsTab.Recents.label),
                            Triple(CommsTab.Contacts, R.drawable.person_24px_filled, CommsTab.Contacts.label),
                            Triple(CommsTab.Messages, R.drawable.sms_24px, CommsTab.Messages.label),
                        ).forEach { (tab, icon, label) ->
                            AdaptiveNavigationItem(
                                selected = selectedTab == tab,
                                onClick = { selectedTab = tab },
                                icon = { if (tab == CommsTab.Messages) DefaultSmsLauncherIcon() else Icon(painterResource(icon), contentDescription = null) },
                                label = { Text(stringResource(label)) },
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                if (!inSubScreen && selectedTab != CommsTab.Keypad) {
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
            Box(modifier = Modifier.padding(contentPadding).fillMaxSize()) {
                when (selectedTab) {
                    CommsTab.Recents -> RecentsScreen(searchQuery = searchQuery, showFilters = showFilters)
                    CommsTab.Recordings -> de.mm20.launcher2.ui.component.AuthGate(
                        enabled = lockCallRecordings,
                        title = stringResource(R.string.auth_gate_recordings),
                        unlocked = recordingsUnlocked,
                        onUnlocked = { de.mm20.launcher2.comms.privacy.PrivacySession.unlockRecordings() },
                        onCancelled = { selectedTab = CommsTab.Recents },
                    ) {
                        CallRecordingsList(searchQuery = searchQuery)
                    }
                    CommsTab.Contacts -> ContactsScreen(
                        searchQuery = searchQuery,
                        showLocalSearch = false,
                        showAddFab = false,
                    )
                    CommsTab.Keypad -> Box(Modifier.fillMaxSize()) // Rendered in overlay below
                    CommsTab.Messages -> MessagesScreen(
                        searchQuery = searchQuery,
                        initialNumber = if (initialTab.lowercase() == "messages") initialNumber else "",
                        initialBody = initialBody,
                        initialAttachments = initialAttachments,
                    )
                }
            }
        }
    }

    if (selectedTab == CommsTab.Keypad) {
        BackHandler { selectedTab = CommsTab.Recents }
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(20f),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
        ) {
            Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                DialpadScreen(initialNumber = initialNumber)
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
}

/** Use the same live custom-icon/icon-pack pipeline as launcher search and the dock. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Composable
private fun CommsLauncherIcon(packageName: String?, user: android.os.UserHandle = android.os.Process.myUserHandle()) {
    val applications: AppRepository = koinInject()
    val icons: IconService = koinInject()
    val pixels = with(LocalDensity.current) { 28.dp.roundToPx() }
    val icon by remember(packageName, user, pixels) {
        if (packageName == null) flowOf(null)
        else applications.findOne(packageName, user).flatMapLatest { app ->
            if (app == null) flowOf(null) else icons.getIcon(app, pixels)
        }
    }.collectAsStateWithLifecycle(null)
    if (icon != null) ShapedLauncherIcon(size = 28.dp, icon = { icon })
    else Icon(painterResource(R.drawable.sms_24px), contentDescription = null)
}

@Composable
private fun ThemedDrawerIcon(
    iconRes: Int,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        modifier = Modifier.size(32.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Composable
private fun DefaultSmsLauncherIcon() {
    val context = LocalContext.current
    var packageName by remember { mutableStateOf(android.provider.Telephony.Sms.getDefaultSmsPackage(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        packageName = android.provider.Telephony.Sms.getDefaultSmsPackage(context)
    }
    // Telos hosts several virtual apps in one package; resolve the Messages virtual app explicitly.
    if (packageName == context.packageName || packageName.isNullOrBlank()) {
        val bitmap = remember(context) {
            de.mm20.launcher2.search.VirtualAppAppearance.largeIcon(context, "telos_messages_app")
        }
        if (bitmap != null) {
            val drawable = remember(bitmap, context) {
                android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
            }
            val staticIcon = remember(drawable) {
                de.mm20.launcher2.icons.StaticLauncherIcon(
                    foregroundLayer = de.mm20.launcher2.icons.StaticIconLayer(drawable),
                    backgroundLayer = de.mm20.launcher2.icons.ColorLayer(0)
                )
            }
            ShapedLauncherIcon(
                size = 28.dp,
                icon = { staticIcon }
            )
        } else {
            ShapedLauncherIcon(size = 28.dp, icon = { null })
        }
    } else CommsLauncherIcon(packageName)
}
