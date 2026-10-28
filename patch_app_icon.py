import re

with open('app/ui/src/main/java/de/mm20/launcher2/ui/store/StoreDashboardScreen.kt', 'r') as f:
    code = f.read()

# We need to optimize AppIcon by caching the launcherIcon logic so it doesn't do disk IO on the main UI thread during composition or scroll.
app_icon_old = r"""@OptIn\(kotlinx\.coroutines\.ExperimentalCoroutinesApi::class\)
@Composable
internal fun AppIcon\(packageName: String, name: String, size: Int, installedVersion: Long\? = null, source: de\.mm20\.launcher2\.store\.model\.AppSource\? = null\) \{
    val context = LocalContext\.current
    val appRepository: de\.mm20\.launcher2\.applications\.AppRepository = org\.koin\.compose\.koinInject\(\)
    val iconService: de\.mm20\.launcher2\.icons\.IconService = org\.koin\.compose\.koinInject\(\)
    val sizePx = with\(androidx\.compose\.ui\.platform\.LocalDensity\.current\) \{ size\.dp\.roundToPx\(\) \}
    val launcherIcon by remember\(packageName, installedVersion, sizePx\) \{
        if \(packageName\.isBlank\(\)\) \{
            kotlinx\.coroutines\.flow\.flowOf<de\.mm20\.launcher2\.icons\.LauncherIcon\?>\(null\)
        \} else \{
            appRepository\.findOne\(packageName, android\.os\.Process\.myUserHandle\(\)\)
                \.flatMapLatest \{ app ->
                    if \(app == null\) kotlinx\.coroutines\.flow\.flowOf<de\.mm20\.launcher2\.icons\.LauncherIcon\?>\(null\)
                    else iconService\.getIcon\(app, sizePx\)
                \}
        \}
    \}\.collectAsStateWithLifecycle\(null\)
    var localResolved by remember\(packageName, installedVersion\) \{ mutableStateOf\(false\) \}
    val bitmap by androidx\.compose\.runtime\.produceState<androidx\.compose\.ui\.graphics\.ImageBitmap\?>\(null, packageName, installedVersion\) \{
        value = if \(packageName\.isBlank\(\)\) null else withContext\(Dispatchers\.IO\) \{
            runCatching \{
                val d: Drawable = context\.packageManager\.getApplicationIcon\(packageName\)
                d\.toBitmap\(size \* 3, size \* 3\)\.asImageBitmap\(\)
            \}\.getOrNull\(\)
        \}
        localResolved = true
    \}
    val resolver: de\.mm20\.launcher2\.store\.icons\.StoreIconResolver = org\.koin\.compose\.koinInject\(\)
    val remote by androidx\.compose\.runtime\.produceState<List<String>>\(emptyList\(\), source, packageName, localResolved, launcherIcon != null \|\| bitmap != null\) \{
        value = if \(localResolved && source != null && launcherIcon == null && bitmap == null\) resolver\.candidates\(source, packageName\) else emptyList\(\)
    \}
    var remoteIndex by remember\(source, packageName, remote\) \{ mutableIntStateOf\(0\) \}
    val li = launcherIcon
    val b = bitmap
    if \(li != null\) \{
        de\.mm20\.launcher2\.ui\.component\.ShapedLauncherIcon\(size = size\.dp, icon = \{ li \}\)
    \} else if \(b != null\) \{
        Image\(b, contentDescription = null, modifier = Modifier\.size\(size\.dp\)\)
    \} else if \(remoteIndex < remote\.size\) \{
        coil\.compose\.AsyncImage\(model = remote\[remoteIndex\], contentDescription = null,
            modifier = Modifier\.size\(size\.dp\)\.clip\(RoundedCornerShape\(12\.dp\)\),
            contentScale = androidx\.compose\.ui\.layout\.ContentScale\.Fit,
            onError = \{ remoteIndex\+\+ \}\)
    \} else \{
        Surface\(Modifier\.size\(size\.dp\), shape = CircleShape, color = MaterialTheme\.colorScheme\.primaryContainer\) \{
            Box\(contentAlignment = Alignment\.Center\) \{
                Text\(name\.firstOrNull\(\)\?\.uppercaseChar\(\)\?\.toString\(\) \?: "\?", style = MaterialTheme\.typography\.titleMedium, color = MaterialTheme\.colorScheme\.onPrimaryContainer\)
            \}
        \}
    \}
\}"""

app_icon_new = """@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Composable
internal fun AppIcon(packageName: String, name: String, size: Int, installedVersion: Long? = null, source: de.mm20.launcher2.store.model.AppSource? = null) {
    val context = LocalContext.current
    val appRepository: de.mm20.launcher2.applications.AppRepository = org.koin.compose.koinInject()
    val iconService: de.mm20.launcher2.icons.IconService = org.koin.compose.koinInject()
    val sizePx = with(androidx.compose.ui.platform.LocalDensity.current) { size.dp.roundToPx() }
    
    // Defer DB/PackageManager lookups aggressively to IO and cache locally to prevent List UI jank
    val launcherIcon by remember(packageName, installedVersion, sizePx) {
        if (packageName.isBlank()) {
            kotlinx.coroutines.flow.flowOf<de.mm20.launcher2.icons.LauncherIcon?>(null)
        } else {
            appRepository.findOne(packageName, android.os.Process.myUserHandle())
                .flatMapLatest { app ->
                    if (app == null) kotlinx.coroutines.flow.flowOf<de.mm20.launcher2.icons.LauncherIcon?>(null)
                    else iconService.getIcon(app, sizePx)
                }
        }
    }.collectAsStateWithLifecycle(null)

    var localResolved by remember(packageName, installedVersion) { mutableStateOf(false) }
    val bitmap by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, packageName, installedVersion) {
        value = if (packageName.isBlank()) null else withContext(Dispatchers.IO) {
            runCatching {
                val d: Drawable = context.packageManager.getApplicationIcon(packageName)
                d.toBitmap(size * 3, size * 3).asImageBitmap()
            }.getOrNull()
        }
        localResolved = true
    }
    
    val resolver: de.mm20.launcher2.store.icons.StoreIconResolver = org.koin.compose.koinInject()
    val remote by androidx.compose.runtime.produceState<List<String>>(emptyList(), source, packageName, localResolved, launcherIcon != null || bitmap != null) {
        if (localResolved && source != null && launcherIcon == null && bitmap == null) {
            value = withContext(Dispatchers.IO) { resolver.candidates(source, packageName) }
        } else {
            value = emptyList()
        }
    }
    var remoteIndex by remember(source, packageName, remote) { mutableIntStateOf(0) }
    
    val li = launcherIcon
    val b = bitmap
    
    if (li != null) {
        de.mm20.launcher2.ui.component.ShapedLauncherIcon(size = size.dp, icon = { li })
    } else if (b != null) {
        Image(b, contentDescription = null, modifier = Modifier.size(size.dp))
    } else if (remoteIndex < remote.size) {
        coil.compose.AsyncImage(
            model = coil.request.ImageRequest.Builder(context)
                .data(remote[remoteIndex])
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier.size(size.dp).clip(RoundedCornerShape(12.dp)),
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            onError = { remoteIndex++ }
        )
    } else {
        Surface(Modifier.size(size.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Box(contentAlignment = Alignment.Center) {
                Text(name.firstOrNull()?.uppercaseChar()?.toString() ?: "?", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}"""

code = re.sub(app_icon_old, app_icon_new, code)

with open('app/ui/src/main/java/de/mm20/launcher2/ui/store/StoreDashboardScreen.kt', 'w') as f:
    f.write(code)

