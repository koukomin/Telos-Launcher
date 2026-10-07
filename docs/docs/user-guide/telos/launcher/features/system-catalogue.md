# System, plugins and integrations: complete feature catalogue

This page lists, in one place, everything the Telos launcher does that is not search, appearance or
home screen layout: favorites and tags, plugins, accounts, integrations, backup, profiles, permissions,
system entry points, crash reporting, debug tools and the build-dependent switches. Every row was
checked against the code. Anything that is off by default, experimental or limited to some builds is
marked.

The narrative pages cover the same ground in a friendlier order:
[Favorites and tags](../favorites-tags.md), [Plugins and integrations](../plugins-integrations.md),
[Privacy and protection](../privacy-protection.md) and [Freeze](../../freeze/index.md). Use this page
as the lookup table.

## Legend

| Mark | Meaning |
| --- | --- |
| <Badge type="warning" text="experimental" /> | Works, but is new or depends heavily on the device |
| <Badge type="info" text="non-release" /> | Only present in debug and nightly builds |
| <Badge type="danger" text="debug only" /> | Only present in debug builds |
| <Badge type="tip" text="needs permission" /> | Does nothing until an Android permission or special access is granted |

Setting tables use this order: setting, where it is in Settings, default, effect. "Settings" means the
launcher's own settings, opened from the launcher menu or from the app list.

## Settings map

The top level of Settings, and where each topic of this page lives.

| Entry | Contains (topics of this page) |
| --- | --- |
| Search | Favorites, Excluded search results, Tags, Protection (see [Privacy and protection](../privacy-protection.md)), the Wikipedia switch |
| Integrations | Weather, Media control, Feed, Cloud and network storage, Nextcloud, Owncloud, Wikipedia, Tasks, Breezy Weather, Smartspacer |
| Advanced settings | Performance, Context profiles, Plugins, Freeze, App Lock, Work profile, Backup and restore, Debug |
| Gestures | Which action each gesture runs, including global actions, Feed and plugin actions |
| Floating launcher, Smart Freeze Dashboard | Own entries on the main screen. Smart Freeze Dashboard is <Badge type="warning" text="pending review" /> |
| About | Version, Build information, licenses, links |

## Favorites

Favorites are items you pin, plus items the launcher learns you use often. They are stored in the
launcher database together with a launch counter, a pin position and a ranking weight.

### Pin levels

| Level | How you get it | Effect |
| --- | --- | --- |
| Not pinned, frequently used | Default for any item you launch | Can appear in the "frequently used" section while it has enough launches |
| Pinned, automatically sorted | Item menu > **Pin to favorites** | Always shown, ordered by launch count |
| Pinned, manually sorted | Drag in **Edit favorites** | Always shown, in your order |
| Unpinned | Item menu > **Unpin** | Back to "not pinned" |

The three sections you see in the editor are named "Not pinned - frequently used", "Pinned -
automatically sorted" and "Pinned - manually sorted". A fourth section, **Tags**, holds pinned tags
(see below).

### Favorites settings

| Setting | Where in Settings | Default | Effect |
| --- | --- | --- | --- |
| Favorites (screen) | Search > Favorites | on (`favoritesEnabled`) | Show pinned and frequently used items above the app grid |
| Edit favorites | Search > Favorites | n/a | Opens the editor sheet to reorder pinned items and move them between sections |
| Show in favorites (frequently used) | Search > Favorites > Frequently used | on | Show learned items in the favorites area |
| Number of rows | Search > Favorites > Frequently used | 1 (range 1 to 4) | Rows reserved for frequently used items |
| Ranking flexibility | Search > Favorites | Default (Low, Default, High) | How strongly launch history moves a result up in search ranking |
| Edit button | Search > Favorites | on | Show a button next to the favorites to rearrange them |
| Compact tags | Search > Favorites | off | Hide tag labels or icons to save space |

The same "Show in favorites" switch and row count are inside the **Edit favorites** sheet.

::: tip Which items can be pinned
Anything the launcher can save: apps, contacts, files, calendar events, app shortcuts, places,
websites, web apps and plugin results. Items from plugins follow the plugin's storage strategy (see
[Plugin SDK contract](#plugin-sdk-contract)).
:::

## Tags

A tag is a name plus a set of items, with an icon (a custom icon or an emoji).

| Action | Where | Notes |
| --- | --- | --- |
| Create a tag | Search > Tags > **Create tag**, or Edit favorites > Tags > **Create tag** | A two-step sheet: name and icon, then **Select items** |
| Edit a tag | Search > Tags > the tag | Rename, change icon, change items |
| Duplicate | Search > Tags > menu on a tag | Copies the tag under a new name |
| Delete | Search > Tags > menu on a tag, or save a tag with no items | Saving with no items asks to delete the tag |
| Merge | Rename a tag to an existing name | The launcher warns that the two tags will be merged |
| Pin a tag | Edit favorites > Tags section | A pinned tag is shown in favorites and works as a folder |
| **Auto-organize** | Search > Tags | Groups installed apps into tags named after the Android app category (Games, Social and so on). Not automatic, run it again after installing apps |

::: warning Auto-organize overwrites
Re-running **Auto-organize** replaces the contents of any existing tag that has the same name as a
category. It does not run in the background.
:::

Tags are stored as custom attributes (type `tag`) next to custom icons and custom labels, and are part
of a backup.

## Excluded (hidden) search results

| Setting | Where in Settings | Default | Effect |
| --- | --- | --- | --- |
| Show reveal button | Search > Excluded search results | off | Adds a button to the search results to show items you excluded |
| Excluded items list | Search > Excluded search results | empty | Lists excluded apps and results, lets you restore them |

Per item, the **Show in** choice (item menu > Customize) has three levels:

| Level | Apps | Calendar events | Everything else |
| --- | --- | --- | --- |
| Default ("App grid and search results" for apps) | App list and search | Calendar widget | Search results, can be frequently used |
| Search only ("Search results") | Search only, not in the app list | Search only | Same as default |
| Never (hidden) | Nowhere, not frequently used | Nowhere | Nowhere, not frequently used |

Excluded items are part of a backup, because their visibility is stored with the saved item.

## Plugins

Plugins are other apps that answer the launcher through a content provider. The launcher finds them
by querying content providers for the action `de.mm20.launcher2.action.PLUGIN`, asks each one for its
type, and rescans whenever an app is added, removed, replaced or changed.

### Plugin types

| Type (SDK enum) | What it supplies | Settings section on the plugin page | Where it is switched on |
| --- | --- | --- | --- |
| `FileSearch` | Files | **Files** | Switch per plugin, on the plugin page |
| `ContactSearch` | Contacts | **Contacts** | Switch per plugin, on the plugin page |
| `LocationSearch` | Places | **Places search** | Switch per plugin, on the plugin page |
| `Calendar` | Events, with calendar lists | **Calendar** | Switch per plugin, plus a click-through to choose calendar lists |
| `Weather` | Forecast data | **Weather provider** | **Set as weather provider** button on the plugin page |
| `GestureAction` | A fixed list of named actions (id and label) | none | Settings > Gestures, the plugin actions of a gesture |
| `Widget` | Rows of title, subtitle and value | none | The widget picker |

Gesture action and widget plugins have no section on the plugin page. They are only enabled as a
package (below) and then offered where they are used.

### Plugin settings

| Setting | Where in Settings | Default | Effect |
| --- | --- | --- | --- |
| Plugin list: **Enabled** and **Installed** | Advanced > Plugins | none installed | Packages with an enabled plugin are listed under Enabled, others under Installed. Empty state: "No plugins installed" |
| Enable plugin | Advanced > Plugins > the plugin | off | Enables every plugin type of that package. Triggers the permission request |
| Plugin page toolbar: settings, info, delete | Advanced > Plugins > the plugin | n/a | Opens the plugin's own settings activity (if it has one), the Android app info, or uninstalls the package |
| Verified author badge | Advanced > Plugins > the plugin | n/a | A check mark next to the author when the signing certificate matches a built-in list (currently only the author `MM2-0`) |
| Plugin badges | Icons settings > Badges | on (`badgesPlugins`) | Shows which plugin produced a search result |
| Setup banner | Plugin page | n/a | "You need to set up this plugin first" with a **Set up** action, when the plugin reports `SetupRequired` |
| Error banner | Plugin page | n/a | "This plugin isn't working correctly", when the plugin reports an error |

A plugin that is not `Ready` cannot be switched on for search. 
### Access control

| Step | What happens |
| --- | --- |
| 1 | You switch on **Enable plugin**. If the plugin reports "no permission", the launcher starts the SDK's permission activity (`de.mm20.launcher2.plugin.REQUEST_PERMISSION`) |
| 2 | The plugin shows a dialog. **Allow** stores the launcher's package name in the plugin's own allow list |
| 3 | Every later call goes through the SDK's base provider, which checks the caller against that list |
| 4 | A caller that is not on the list gets a `SecurityException` |

Disabling a plugin in Telos only stops Telos from querying it. The allow list stays with the plugin.
The plugin author can change the list with `PluginPermissionManager`.

### Plugin SDK contract

Public module `plugins/sdk` (Apache-2.0, published as `de.mm20.launcher2:plugin-sdk`). Shared
contracts are in `core/shared`.

| Item | Detail |
| --- | --- |
| Contract methods | `getType`, `getState`, `getConfig` on every plugin |
| States | `Ready` (with optional status text), `SetupRequired` (setup activity and message), plus no-permission and error states |
| Query plugins | Config has a `storageStrategy`: `StoreReference` (launcher stores only the ID and asks the plugin again, must work offline), or `StoreCopy` (launcher stores a copy, the default) |
| Weather config | `minUpdateInterval` and `managedLocation` |
| Plugin metadata | Optional `de.mm20.launcher2.plugin.label` and `de.mm20.launcher2.plugin.description` provider meta-data override the label and description |
| Gesture contract | Path `actions` returns `id` and `label`, method `invokeAction` runs an action by id |
| Widget contract | Path `items` returns `title`, `subtitle`, `value`, parameter `lang`. Data only, the launcher draws the UI |

See `docs/docs/developer-guide/plugins/` for the full developer documentation.

## Accounts

The accounts service handles two account types.

| Account | Where in Settings | Sign-in | Effect |
| --- | --- | --- | --- |
| Nextcloud | Integrations > Nextcloud | Nextcloud's login flow, started from **Sign in** | Enables file search in your server. **Sign out** removes it |
| Owncloud | Integrations > Owncloud | Owncloud's login flow | Same, for ownCloud |

After sign-in the screen shows the signed-in user. A **Files** switch (summary: search your cloud
files) is the per-service switch for the file search provider. Account logins are not part of a backup.

Other cloud and network storage (Dropbox, Google Drive, OneDrive, WebDAV, SFTP, SMB, FTP) is handled
by Telos Files and opened from Integrations > **Cloud and network storage**. See
[Network and cloud](../../files/network-cloud.md).

## Integrations

| Integration | Where in Settings | Default | What it does | Needs |
| --- | --- | --- | --- | --- |
| Weather | Integrations > Weather | provider Open-Meteo, automatic location on | Choose the provider, location, measurement system | Location for automatic mode |
| Media control | Integrations > Media control | n/a | Choose which music apps the music widget follows. A reset button exists in debug builds | Notification access |
| Feed | Integrations > Feed | off | Swipe right opens an overlay feed from another app | <Badge type="info" text="non-release" />, a feed provider app |
| Cloud and network storage | Integrations > Cloud and network storage | n/a | Connections for Telos Files | per storage |
| Nextcloud | Integrations > Nextcloud | signed out | File search | Account |
| Owncloud | Integrations > Owncloud | signed out | File search | Account |
| Wikipedia | Integrations > Wikipedia | on | Article results | Online results |
| Tasks | Integrations > Tasks | off | Tasks as search results and in reminders | Tasks app, Tasks permission |
| Breezy Weather | Integrations > Breezy Weather | n/a | Use Breezy Weather as weather provider | Breezy Weather installed |
| Smartspacer | Integrations > Smartspacer | off | Smartspacer content in the clock widget | Android 10+, <Badge type="info" text="non-release" /> |

### Weather providers

The weather repository can build these providers: Open-Meteo, OpenWeatherMap, MET Norway, Bright Sky,
Breezy Weather, and any weather plugin (by its authority). Build information shows whether the MET
Norway and OpenWeatherMap providers are available in the installed build ("Weather providers" rows
under Features). The weather page has a **Provider** choice, location settings (automatic location,
manual location, or a managed location when the provider or plugin sets it) and a measurement system.
A **Clear weather data** action appears in debug builds only.

### Feed

| Setting | Where | Default | Effect |
| --- | --- | --- | --- |
| Feed | Integrations > Feed | off | Turning it on sets the swipe-right gesture to **Feed**. Turning it off sets swipe-right to no action |
| Feed provider | Integrations > Feed > Feed provider | none selected | Radio list of installed overlay providers |

How it works: the launcher binds to services that answer the action
`com.android.launcher3.WINDOW_OVERLAY` with the overlay protocol version 7. If none is installed the
screen says "No feed providers installed". In release builds `com.google.android.googlequicksearchbox`
and `app.lawnchair.lawnfeed` are blocked, because they are known not to work with third-party
launchers. In debug builds the block list is empty. The **Feed** gesture also appears in the gesture
list only when the feed flag is on (see [Feature flags](#feature-flags-and-build-types)).

### Wikipedia

| Setting | Where | Default | Effect |
| --- | --- | --- | --- |
| Wikipedia | Integrations > Wikipedia | on | Search the free encyclopedia |
| Show images | stored setting `wikipediaSearchImages` | on | Show article images in results. No toggle on this screen |
| Wikipedia URL | Integrations > Wikipedia | empty (placeholder `https://en.wikipedia.org`) | Use another language edition or MediaWiki-compatible site |

### Tasks

| Item | Behavior |
| --- | --- |
| App | Looks for the package `org.tasks` (Tasks.org). If missing, an **Install** banner opens `https://tasks.org/` |
| Permission | <Badge type="tip" text="needs permission" /> the Tasks permission group, declared as `org.tasks.permission.READ_TASKS` |
| Switch | **Tasks** (summary: search tasks in the Tasks app) enables the calendar provider `tasks.org`, default off |
| Shortcuts | **Open Tasks app**, and a link to the provider's calendar list settings |
| Ready state | "Tasks integration is fully set up and ready to use" |

### Breezy Weather

| Item | Behavior |
| --- | --- |
| App | Looks for `org.breezyweather`. If missing, **Install** opens the Breezy Weather install guide |
| Receiver | The launcher registers a receiver for the Gadgetbridge weather broadcast `nodomain.freeyourgadget.gadgetbridge.ACTION_GENERIC_WEATHER` |
| Setup | In Breezy Weather: Settings > Widgets and Live wallpaper > Send Gadgetbridge data > enable Telos. Then in Telos pick Breezy Weather as provider |
| Button | **Set as weather provider**, disabled once active ("Currently set as weather provider"). Plus **Open Breezy Weather** |

### Smartspacer

| Item | Behavior |
| --- | --- |
| Shown | Only on Android 10 and up, and only when the Smartspacer flag is on (non-release builds) |
| App | Looks for `com.kieronquinn.app.smartspacer`. If missing, **Install** opens the Smartspacer repository |
| Permission | The manifest requests `com.kieronquinn.app.smartspacer.permission.ACCESS_SMARTSPACER` |
| Switch | **Enable Smartspacer integration** (stored as the clock widget's Smartspacer option), default off |
| Button | **Open Smartspacer** |

## Backup and restore

Settings > Advanced settings > **Backup and restore** has two actions.

| Action | Effect |
| --- | --- |
| **Backup** | "Export preferences and launcher data". You pick a file location. A sheet shows progress and then a success message |
| **Restore** | "Import a previously created backup". You pick a file (any type is allowed in the picker). A sheet reads the backup's details and offers **Restore** |

### What a backup contains

A backup is a ZIP archive. It holds a `meta` file (app version name, timestamp, device name, backup
format) and one part per component.

| Component | Archive files | Content |
| --- | --- | --- |
| Settings | the settings data store | All launcher preferences (see the secrets rule below) |
| Favorites and saved items | `favorites.0000` and following | Saved searchable items with key, type, visibility (hidden or search only), launch count, pin position, ranking weight and the item data |
| Customizations | `customizations.*` | Custom icons, custom labels and tags |
| Widgets | `widgets2.*` | The widget list, with type, position, config and id |
| Search actions | `searchactions.*` | Custom search actions and their order |
| Themes | `colors.0000`, `shapes.0000`, `transparencies.0000`, `typographies.0000` | Custom color, shape, transparency and typography themes |

### What is not in a backup

| Not included | Why |
| --- | --- |
| Cloud account logins (Nextcloud, ownCloud) | Not stored in the backup |
| Plugin enable state and plugin allow lists | Plugin enablement is rebuilt by scanning; allow lists live in the plugin app |
| Messages and phone secrets | Telos Phone and Messages passwords and keys (TMDB, subtitle API, SIP and remote phonebook passwords) are blanked, as are hidden and protected numbers and the last dial pad digits |
| Wallpaper, installed icon packs, app data of other apps | Not part of the launcher database |

::: tip Secrets and restore
Passwords and keys are encrypted with a key that exists only on the device that made them, so they
would be useless elsewhere. On restore the launcher keeps the passwords and hidden numbers already on
this device. A SIP or remote phonebook account that has no password after restore is switched off.
:::

### Compatibility check

The backup format is `1.9`. A restore compares it with the current format.

| Backup format | Result |
| --- | --- |
| Same major and minor | Compatible, fully restored |
| Same major, different minor | Partially compatible, some parts may not be restored. The Restore button stays available with a warning |
| Different major, or unreadable | Incompatible. Restore is disabled |

Restoring wipes the saved favorites first and then imports the ones in the file. The restore reads
the archive defensively: entries that would be unpacked outside the restore folder are ignored.

## Profiles

Telos recognizes up to three Android profiles: **Personal**, **Work** and **Private**. On Android 15
and up it asks the system for the profile type; on older versions any other profile is treated as work.
The Samsung Secure Folder (user 150) is hidden.

| Feature | Where | Details |
| --- | --- | --- |
| Profile state | Internal | A profile is "locked" when its user is not unlocked or quiet mode is on. On Android 16 and up the launcher also reads whether the private space entry point is hidden |
| Profile badge | Icons settings > badges, always on | Work apps get a briefcase badge, private apps a lock badge |
| Work profile screen | Advanced > Work profile | Status (Not set up, Active, Paused), a button to set up or manage the profile in Android settings |
| **Work Mode** switch | Advanced > Work profile | Pauses or resumes work apps. <Badge type="tip" text="needs permission" /> Telos must be the default home app |
| **Lock work profile toggle** | Advanced > Work profile | Default off. Requires authentication to pause or resume work apps from the app list |
| **Initialize Telos Sandbox** | Advanced > Work profile, shown only while no work profile exists | Starts Android's managed profile provisioning with Telos as the profile owner <Badge type="warning" text="experimental" /> <Badge type="warning" text="pending review" /> |
| **Clone to Sandbox** | App item menu, shown when a work profile exists and the app is not already in it | Copies the app into the work profile through a bridge service <Badge type="warning" text="experimental" /> <Badge type="warning" text="pending review" /> |
| Private space | Automatic | A private space is listed as its own profile and shown with the lock badge |

There is no separate "clone" profile type in the profile manager. Apps cloned with Android's own dual
apps feature live in other user handles and are shown with a badged icon (pending review code path).
See [Freeze](../../freeze/index.md) for the Sandbox walkthrough.

### Context profiles

A different feature with a similar name: automatic switching of launcher behavior.

| Setting | Where | Default | Effect |
| --- | --- | --- | --- |
| Enable context profiles | Advanced > Context profiles | off | Triggers are only checked while the launcher is open, not in the background |
| Add and delete profile | Advanced > Context profiles | none | Each profile has a name and an icon (Home, Work, Car, Gaming, Battery saver, Sleep, Custom) |
| Trigger | per profile | Manual only | Manual, time of day, WiFi network (SSID), Bluetooth device (exact name), Battery Saver, charging with charger type |
| Active now | per profile | off | Forces the profile on regardless of its trigger |
| Gesture overrides | per profile | none | Swap any of swipe down, left, right, up, double tap and long press |
| Freeze profile, home widgets page | per profile | Don't override | Switch the freeze profile or the widget screen target |
| Do Not Disturb | per profile | Don't override | Turn on or off. <Badge type="tip" text="needs permission" /> notification policy access |
| Override brightness | per profile | off | 0 to 100 percent. <Badge type="tip" text="needs permission" /> modify system settings |
| Launch app when activated | per profile | none | Launches one app once when the profile becomes active |

The WiFi trigger needs location permission to read the SSID, and the Bluetooth trigger needs the
Bluetooth connect permission. Profile changes can lag, since nothing runs in the background.

## Permissions

The launcher asks for permissions only when a feature needs them. The central manager knows these
groups.

| Group | Android permission or special access | Why |
| --- | --- | --- |
| Calendar | Read calendar (write also declared) | Calendar search and the calendar widget |
| Tasks | `org.tasks.permission.READ_TASKS` | Tasks integration |
| Location | Coarse and fine location | Automatic weather location, places distance, WiFi trigger |
| Contacts | Read contacts (write also declared) | Contact search |
| External storage | All files access (`MANAGE_EXTERNAL_STORAGE`) | Local file search |
| Notifications | Notification listener access | Notification badges and the media widget |
| App shortcuts | Be the default home app (shortcut host) | Search app shortcuts |
| Manage profiles | Home role (Android 10+) | Pause and resume work profile |
| Accessibility | Accessibility service access | Lock screen, power menu, recents and foreground detection |
| Call, Call log, Phone state | Call, read and write call log, read phone state | Telos Phone, Dynamic Island call pill |
| Usage access | `PACKAGE_USAGE_STATS` | Smart Freeze idle detection, App Lock detection |
| Overlay window | Draw over other apps | Floating launcher, Dynamic Island, App Lock overlay |
| Bluetooth | Bluetooth connect | Bluetooth context trigger |
| Notification policy | Do Not Disturb access | Context profile override |
| Write settings | Modify system settings | Context profile brightness |
| Camera | Camera | App Lock intruder photo |

Other declared permissions: internet and network state, expand status bar, vibrate, set alarm,
schedule exact alarms, query all packages, install and delete packages, interact across profiles,
record audio and manage own calls (Telos Phone), full-screen intent, post notifications (web app
notifications, crash notifications), foreground service of type special use (floating launcher,
Dynamic Island, App Lock overlay), set wallpaper (video wallpaper), `WRITE_SECURE_SETTINGS` (desktop
mode, granted over adb), and the Shizuku and Island permissions for Freeze.

::: warning Restricted settings on Android 13 and up
Accessibility and notification listener access can be blocked for apps installed outside a store. The
launcher shows guidance about this only after you have tried to grant the permission once.
:::

## System entry points

| Entry point | Mechanism | What it does |
| --- | --- | --- |
| Default launcher | Activity with `MAIN` and `HOME` categories, single task | Telos as the home app. Required for the Home role features above |
| App list icon | `MAIN` and `LAUNCHER` | Same activity, opens the launcher |
| Digital assistant | Activity with the `ASSIST` action | Starts the launcher in assistant mode when set as the default assistant |
| Settings | Activity with `APPLICATION_PREFERENCES` | The system's "app settings" button opens Settings |
| Settings deep links | `https://kvaesitso.mm20.de/in-app?route=...` or an `EXTRA_ROUTE` extra | Opens a settings page directly. Known routes include weather and media integration, search actions, hidden items, floating launcher, wallpaper, web apps, app recommendations, intruder photos, store and crash report |
| Pin shortcut | Activity for `CONFIRM_PIN_SHORTCUT` | Other apps can pin a shortcut to the launcher |
| Theme import | `VIEW` with MIME `application/vnd.de.mm20.launcher2.theme` | Opens a theme file in the launcher |
| `obtainium://` links | `VIEW` for the scheme | Opens the Telos Store with the link |
| App widgets and tile | Recent calls and Dialpad widgets, a "Cellular" quick settings tile, a video live wallpaper | Telos Phone and wallpaper extras |
| Device admin receivers | Freeze device owner mode, Sandbox profile owner | Needed for device owner freezing and managed profile creation |
| File provider | `${applicationId}.fileprovider` | Shares files such as heap dumps |

Static app shortcuts: `shortcuts.xml` is present but empty, so the launcher icon has no static shortcuts.

Telos also registers as a handler for content of its built-in apps (telephone links, SMS and MMS
links, PDF and Office documents, images, video, magnet and torrent files). Those are described on the
pages of [Phone](../../phone/index.md), [Messages](../../messages/index.md),
[Photos](../../photos/index.md) and [Video](../../video/index.md).

## Global actions

System actions that need no root. Lock screen, power menu and recents use an accessibility service;
the notification panel and quick settings try the status bar service first and fall back to the
accessibility action.

| Action | Gesture name | Mechanism | Needs |
| --- | --- | --- | --- |
| Notification panel | Notifications | Status bar service, else accessibility | `EXPAND_STATUS_BAR` (declared) |
| Quick settings | Quick settings | Status bar service, else accessibility | `EXPAND_STATUS_BAR` (declared) |
| Lock screen | Screen lock (Android 9 and up) | Accessibility global action | <Badge type="tip" text="needs permission" /> accessibility |
| Power menu | Power menu | Accessibility global action | <Badge type="tip" text="needs permission" /> accessibility |
| Recents | Recents | Accessibility global action | <Badge type="tip" text="needs permission" /> accessibility |

If the accessibility service is off when you use one of those gestures, a "gesture failed" sheet
offers to grant it. The service listens to window state changes only, cannot read window content, and
is not flagged as an accessibility tool. It also reports the foreground app through an internal bridge. There is no screenshot action in the code.

The full gesture list also has: No action, Search, Widgets (with a target), Launch (an item), Web
apps panel, Home screen menu, Launcher settings, Feed (non-release builds only) and Plugin actions.

## Notification access

| Item | Detail |
| --- | --- |
| Service | `NotificationListenerService` in `data/notifications`, bound by the system |
| Used for | Notification badges on apps (style **Dot** or **Count**), and the media widget |
| Granted in | Android's notification listener settings, opened by the launcher when needed |
| Badge settings (Icons settings) | Notifications on, Suspended apps on, Cloud files on, Shortcuts on, Plugins on. Style default **Dot**, color default follows the theme |

## Crash reporter, logs and debug

| Setting | Where in Settings | Default | Effect |
| --- | --- | --- | --- |
| Crash reporter | Advanced > Debug > Crash reporter | n/a | Lists local error and crash reports from the last 7 days, newest first. Filter buttons for exceptions and crashes. Crashes shown by default, exceptions only in debug builds |
| Crash report link | Settings route | n/a | Route `settings/debug/crashreport` with a report path opens one report. The reporter module declares `POST_NOTIFICATIONS` |
| Logs | Advanced > Debug > Logs | n/a | View and export application logs |
| Memory snapshot | Advanced > Debug | n/a | Writes a `.hprof` heap dump to the external cache and opens the share sheet. The app freezes while it runs |
| Clean up database | Advanced > Debug > Tools | n/a | Removes saved items that are broken or whose key does not match. Reports the number removed |
| Reinstall icon packs | Advanced > Debug > Tools | n/a | Clears and rebuilds the icon pack cache |
| String normalization test | Advanced > Debug > Tools | n/a | <Badge type="danger" text="debug only" /> Test screen for transliteration |
| Reset widget | Media control | n/a | <Badge type="danger" text="debug only" /> Clears all music data |
| Clear weather data | Weather | n/a | <Badge type="danger" text="debug only" /> Removes weather data from the database |

Crash reports are kept on the device. The launcher does not send them anywhere by itself. The
reporter library's content provider starts automatically with the app. The settings file also has a
safety net: if `settings.json` cannot be read, it is first copied to `settings.json.corrupt` before
defaults replace it. Debug builds run an extra debug-mode setup at start.

The crash guard that switches off repeatedly crashing media apps is described on
[Privacy and protection](../privacy-protection.md).

## About and build information

| Item | Where | Shows |
| --- | --- | --- |
| Version | About | App version |
| Build information | About > Build information | Build type, version details, signature hash, and the Features list (weather provider availability: Met No, OpenWeatherMap) |
| License | About | The app license (GPL-3.0, except the plugin SDK and shared core, which are Apache-2.0) |
| GitHub link | About > Links | Project repository |
| Open source libraries | About | List of third-party libraries with descriptions |

## Feature flags and build types

| Flag or variant | Value | Controls |
| --- | --- | --- |
| `FeatureFlags.feed` | on in every build type except `release` | Feed page in Integrations, and the Feed gesture |
| `FeatureFlags.smartspacerIntegration` | on in every build type except `release` | Smartspacer page in Integrations (also needs Android 10+) |
| Build types | `debug` (id suffix `.debug`), `release` (`.release`, minified and shrunk), `nightly` (`.nightly`, based on release, date-stamped version name) | Different package names, so builds install side by side |
| Product flavors | `default` and `fdroid` (version suffix `-fdroid`) | The flavor does not change any flag in this catalogue |
| `BuildConfig.DEBUG` | debug build | String normalization test, widget reset, weather data clear, empty feed block list, exceptions shown in crash reporter, Koin error logging |

Because `feed` and `smartspacerIntegration` test only for `release`, nightly builds also show them.

## Doubts and limits

- The profile manager has no "clone" profile type. Cloned apps are handled only by the pending-review
  dual apps and Sandbox code.
- Several features are wrapped in `TELOS_PENDING_REVIEW` markers (Sandbox, Work Mode toggle, Smart
  Freeze). Their wording here may change after review.
- The Wikipedia image switch exists as a stored setting but has no toggle on the Wikipedia page.
