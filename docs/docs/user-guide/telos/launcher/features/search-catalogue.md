# Search: complete feature catalogue

This page lists everything the launcher search can find, every special query it understands, every
filter and every setting that changes search. It is a reference. For a guided tour read
[Search](../search) first, and for the original Kvaesitso pages see [Filters](../../../search/filters),
[Quick actions](../../../search/quickactions), [Calculator](../../../search/calculator) and
[Unit converter](../../../search/unit-converter).

::: info How this page was checked
Every entry was read from the launcher source code (search settings, search UI, the data modules and
the plugin SDK), not copied from other documentation. Where the code and the user interface text
disagree, the code wins. Features that exist only half-way are collected in
[Experimental, unfinished and not wired](#experimental-unfinished-and-not-wired).
:::

**Reading the tables.** "Where" is a place in the Settings app. "Settings > Search" is the screen that
the Search entry opens. Defaults are the values on a fresh install.

## 1. Search sources and result types

Every source is queried in parallel when you type. "Min. text" is the shortest query that makes the
source answer. Shorter queries return nothing from that source.

| Source | Result type | Min. text | Needs | Network | Filter |
| --- | --- | --- | --- | --- | --- |
| Apps | Installed apps of all profiles (personal, work, private space) | 1 | Nothing | No | Apps |
| Telos apps | Phone, Messages, Radio, Music, Video, Photos, Files and Store as app results | 1 | The app is not switched off in its own settings | No | Apps |
| Web apps | [Web app shortcuts](../desktop-and-overlays#web-apps) that are shown in the grid | 1 | Nothing | No | Apps |
| Activity by component name | One app entry for a typed `package/class` name | 1 | The activity exists | No | Apps |
| App shortcuts | Launcher shortcuts that apps publish (pinned, dynamic, static, cached) | 3 | Telos is the default home app | No | App shortcuts |
| Contacts | Device contacts | 2 | Contacts permission | No | Contacts |
| Calendar events | Events in the next 730 days | 2 | Calendar permission | No | Calendar |
| Reminders | Tasks from Tasks.org and tasks delivered by calendar plugins | 2 | Tasks permission, Tasks source on | No | Reminders |
| Local files | Files from the Android media database | 2 | Storage permission | No | Files and file types |
| Nextcloud files | Files on your Nextcloud server | 4 | Signed-in account | Yes | Files and file types |
| ownCloud files | Files on your ownCloud server | 4 | Signed-in account | Yes | Files and file types |
| Places | OpenStreetMap places near you | 2 | Location permission | Yes | Places |
| Websites | A preview card for what you typed, read as a URL | 1 | Nothing | Yes | Websites |
| Wikipedia | One article | 4 | Nothing | Yes | Articles |
| Calculator | The value of an expression | 1 | Nothing | No | Tools |
| Unit converter | Unit and currency conversion | 1 | Nothing | No | Tools |
| Tags and custom names | Any saved item whose tag or custom name contains your text | 1 | Nothing | No | Follows the item type |
| Quick actions | Buttons next to the search bar | 1 | Depends on the action | No | Not filtered |
| Plugins | Contacts, files, places, calendar events | Set by the plugin | The plugin is enabled and set up | Plugin decides | Follows the result type |

Notes on the table:

- **Network = Yes** means the source only runs when the **Online results** filter is on for that
  search, see [Online results](../search#online-results). Nextcloud and ownCloud send the query to your own
  server, Places to an Overpass server, Websites to the site you typed, Wikipedia to Wikipedia.
- **Delays.** Places wait 250 ms and Wikipedia 750 ms after the query changes before they start.
  Everything else starts at once, after the optional [Search delay](#search-related-settings-elsewhere).
- **Limits.** Local files return at most 10 results (the scan stops after 500 database rows).
  Contacts return about 15. Nextcloud returns at most 10. Every group shows 5 entries until you tap
  **Show all**.
- **No full-text search.** Names, titles, tags and custom names are matched. File contents, message
  texts and note bodies are not searched.
- **No search history.** The launcher does not store the queries you type. Only usage counts of the
  items you launch are stored, see [Ranking](#4-ranking-and-ordering).

### What each source matches

| Source | Matched fields | How |
| --- | --- | --- |
| Apps, Telos apps | Label (custom label if you set one) | Normalized text, score of at least 0.8 needed |
| App shortcuts | Long label and short label | Same score rule as apps |
| Contacts | Display name, alternative name, phonetic name, sort key | Substring, case-insensitive |
| Calendar events | Event title | Substring |
| Tasks.org reminders | Task title | Substring |
| Local files | File title | Query of 3 characters or fewer: starts with. Longer: contains |
| Web apps | Label | Substring, case-insensitive. An empty query lists all |
| Tags and custom names | Tag text, custom label text | Substring on any saved item |
| Places | Name, brand, and well-known categories | Fuzzy name match inside the search radius |
| Wikipedia | Query text | Wikipedia search, best article only |

## 2. Result types: what you can do with them

Tap a result to open it. Long-press (or tap, for results that have no direct launch) to open the
detail card. The detail card has a toolbar of actions. Every card shows tags as `#tag` text and a
star to pin the item to [favorites](../favorites-tags) when favorites are on.

| Result | Tap | Detail card and actions |
| --- | --- | --- |
| App | Launch | Notifications (clearable), app shortcuts, version and package name (if *Show app information* is on), Pin, App info, Launch, Customize, Share (link or APK file), Uninstall (when allowed), Shutter set/change/remove (when shutters are on), Freeze or Unfreeze |
| App, with Smart Freeze advanced features on | Launch | Adds Freeze, Clone to Sandbox (only if a work profile exists and the app is not already in it), Force stop and Clear cache |
| App in the private space | Launch | A locked private space shows a locked state. Customize, share and app info are not offered for private-space apps |
| App shortcut | Launch the shortcut | Pin, Customize, App info, Delete (for shortcuts you can delete). A shortcut whose app was removed shows "unavailable" |
| Contact | Opens the detail card | Phone numbers (call or dial, plus message), email addresses, postal addresses (open in a map, copy), and extra rows from other apps (for example messengers) that the contact data offers. Open in contacts app, Customize, Pin |
| Calendar event or task | Opens the detail card | Time, place, description, open in the calendar app, Customize, Pin. Tasks show due date and completion |
| File | Opens the file | Metadata (path, size, type, title, artist, album, duration, year, dimensions, location, app name, version and so on), Open, Share, Delete (local files, with confirmation), Customize, Pin |
| Place | Opens the detail card | Map preview, opening hours with open/closed state, distance, phone (Dial), Website, Map, Navigation, Report a bug (OpenStreetMap), Customize, Pin |
| Website | Opens the site | Title, description, image, Share, Customize, Pin |
| Wikipedia article | Opens the article | Extract, image, source line, Share, Customize, Pin |
| Calculator | Nothing | Long-press the result to copy the raw number and feel a haptic tick |
| Unit converter | Nothing | "Show all" for the full list, a disclaimer link for currencies |
| Web app | Open the web app | Pin, Edit web app, Replace or Remove (in the dock) |
| Quick action | Start the action | See [section 3](#3-quick-actions) |

**Customize** opens a sheet to set a custom icon, a custom label, tags, and where the item shows up
(see [Excluded and hidden results](#8-excluded-and-hidden-results)).

Grouping details:
- Calendar events and reminders are two separate groups. Events are ranked by name match like any other
  list, not by date.
- The calculator and the unit converter each show one card.
- A frozen app can look grayscale or carry a snowflake badge. Which one depends on your freeze style
  setting. Frozen apps can be dropped from results entirely, see
  [Apps](#apps).

## 3. Quick actions

Quick actions are chips under the search bar. They are produced from what you typed. They are not
affected by filters. Text is classified into one of: plain text, email address, URL, phone number,
date and time, date, time or time span. Each built-in action decides from that class whether to show.

The pen button at the end of the chip row opens Settings > Search > Quick actions.

### Built-in actions

There are twelve. The first nine are on by default, in this order. Share and the two profile actions
are off until you add them.

| Action | Key | Shown when | What it does | Default |
| --- | --- | --- | --- | --- |
| Call | `call` | Text is a phone number | Opens the dialer with the number (dial, does not call directly) | On |
| Message | `message` | Text is a phone number | Opens a message to the number | On |
| Email | `email` | Text is an email address | Opens an email draft | On |
| Add to contacts | `contact` | Phone number or email address | Opens the "new contact" screen | On |
| Set alarm | `alarm` | Text is a time of day | Sets a clock alarm | On |
| Start timer | `timer` | Text is a time span of 24 hours or less | Starts a clock timer | On |
| Schedule event | `calendar` | A date (with or without time), or a time span longer than a day | Creates a calendar event, all-day if there is no time | On |
| Open website | `website` | Text is a URL | Opens it in the browser | On |
| Web search | `websearch` | Always | Runs your default web search with the text | On |
| Share | `share` | Always | Opens the share sheet with the text | Off |
| Lock or unlock private space | `private_space` | Text matches "Private space" (score 0.8 or more), Android 15 or newer, a private space exists | Locks or unlocks it. Clearing the query happens automatically afterwards | Off |
| Pause or resume work profile | `work_profile` | Text matches "Work profile" (score 0.8 or more), a work profile exists | Pauses or resumes it | Off |

Two custom web searches come pre-installed: **YouTube** and **Google Play**.

### Recognised formats

| Class | Pattern | Examples |
| --- | --- | --- |
| Email | One word with an `@` | `name@example.org` |
| Phone number | Optional `+`, then 4 to 18 digits, spaces, `-`, `/` or `.` | `+49 30 1234567` |
| URL | Optional `http(s)://`, optional `www.`, a name with a dot and a top level domain | `example.org/path` |
| Date and time | Your system locale date plus time | The format of your phone |
| Date | Your system date format | The format of your phone |
| Time of day | Your system time format (24 hour or 12 hour with am/pm) | `16:39`, `2:20 am` |
| Time span | Whole number, optional space, unit symbol | `30 s`, `11 min`, `2 h`, `3 d` |

Time span unit symbols are the localized second, minute, hour and day symbols (English `s`, `min`,
`h`, `d`). A span above 24 hours but below about 100 years becomes an event that many days from now.

### Custom quick actions

Create them in Settings > Search > Quick actions with the **+** button. Drag to reorder. Each can be
edited or deleted from its menu. Built-in actions that are not in the list can be added back.

| Kind | Needs | Notes |
| --- | --- | --- |
| Search on a website | A URL template with `${1}` as placeholder | The launcher tries to import name, icon and template from the site's OpenSearch description. Known sites fall back to a built-in table: Google, Bing, Amazon (several countries), DuckDuckGo, Yahoo, Ecosia. Query encoding: percent encoding, form URL encoding or none |
| Search in an app <Badge type="warning" text="experimental" /> | An app that handles the search intent | Lists apps that accept the standard search intent. Extras can be added. Some apps ignore it |
| Custom intent | Action, category, type, data, package, class name, extras | Pass the query as data (`${1}` in a URI template) or as a string extra with a template |

All quick actions are included in the launcher backup.

## 4. Ranking and ordering

All ranked lists use one formula: **0.6 x match score + 0.4 x usage weight**.

| Part | Detail |
| --- | --- |
| Match score | Jaro-Winkler similarity, plus a prefix bonus and a substring bonus, capped at 1. A match on a secondary field is scaled by 0.8 |
| Usage weight | Increases each time you launch an item. The step per launch is set by **Ranking flexibility**: Stable 0.01, Balanced 0.03 (default), Variable 0.1 |
| Normalization | Text is lower-cased and accents are stripped. A transliterator (Settings > Language and region) lets non-Latin names match Latin input. `ae`, `oe` and `ss` are expanded for the letters ae, oe and sharp s |
| Duplicates | Results with the same item key are removed |
| Places | When a location is cached, places are sorted by distance. Otherwise they use the formula above |
| Quick actions | Fixed order from the quick action settings |
| Drawer (empty search) | Alphabetical, per profile. Frozen apps can be moved to the end |

Group order on screen, top to bottom: favorites (only with an empty query), pending store updates (empty
query), apps, web apps, app shortcuts, unit converter, calculator, events, reminders, contacts, places,
Wikipedia, websites, files, documents, images, video, music, recommended app. With
**Arrangement of search results** set to bottom-up the list is reversed so the best result sits next
to a bottom search bar.

## 5. Filters

Open the filter panel with the funnel button in the search bar. A dot on the funnel means a filter
is active. The panel is closed again as soon as you type.

| Filter | Finds | Group name in results |
| --- | --- | --- |
| Online results | Switch. Allows Wikipedia, websites, places, cloud files and online plugin results | n/a |
| Apps | Apps, Telos apps, web apps | Apps |
| Files | Local and cloud files as one list | Files |
| Contacts | Contacts | Contacts |
| Documents | Documents (text, PDF, office files, e-books and more) | Documents |
| Images | Images | Images |
| Video | Videos | Video |
| Music | Audio files | Music |
| Calendar | Events | Events |
| Reminders | Tasks | Reminders |
| App shortcuts | App shortcuts | Shortcuts |
| Wikipedia | Articles (still needs Online results) | Articles |
| Websites | Website cards (still needs Online results) | Websites |
| Places | Places (still needs Online results) | Places |
| Tools | Calculator and unit converter | Calculator, Unit converter |
| Hidden items | Switch. Show items you excluded | n/a |

Rules:

- **Toggling.** When all categories are on, tapping one switches to that one only. When one is the only
  one on, tapping it turns all back on. Otherwise tapping toggles it.
- **Files versus types.** The *Files* filter and the four type filters are alternatives. With *Files*
  on you get one list. With it off, each file appears in exactly one type group.
- **Single category.** If exactly one category is on, its group starts expanded.
- **Reset.** Clearing the search or closing the drawer returns the filters to your default filter.
- **Types.** A file counts as an image, video or audio by its MIME type. Documents are `text/*`, PDF,
  Word, Excel, PowerPoint style files, OpenDocument, EPUB and RTF. Everything else is "other" and only
  shows under *Files*.

### Filter bar

A row of chips above the keyboard. It is only visible while the keyboard is open and search is open.

| Item | Name in the bar |
| --- | --- |
| Online results | Online results |
| Apps, Files, Contacts, Shortcuts, Calendar, Reminders | Same as the filter |
| Documents, Images, Video, Music | File types |
| Wikipedia, Websites, Places, Tools | Same as the filter |
| Hidden items | Excluded search results |

Default order: Apps, Files, Contacts, Online results, App shortcuts, Calendar, Reminders, Documents,
Images, Video, Music, Wikipedia, Websites, Places, Tools, Hidden items (all 16). Chips are grouped:
category chips first, then a divider, then the two switches.

## 6. Settings reference

### Settings > Search

| Setting | Where | Default | Effect |
| --- | --- | --- | --- |
| Favorites | Settings > Search (opens Favorites) | On | Show pinned and frequently used items above the app grid |
| Apps | Settings > Search (opens Apps) | On | Show all apps when the search field is empty |
| Files | Settings > Search (opens Files) | Local on | Search local and cloud files. Always opens a sub page |
| Contacts | Settings > Search | On | Search device contacts. Opens a sub page when a contact plugin exists. Needs the Contacts permission |
| Calendar | Settings > Search | On | Search events. Opens a sub page when a calendar plugin exists or Tasks.org is installed. Needs the Calendar permission |
| Reminders | Settings > Search | On | Toggles the Reminders filter in the default filter. Reminders still need the Tasks source |
| App shortcuts | Settings > Search | On | Search app shortcuts. Needs Telos as default home app |
| Calculator | Settings > Search | On | Evaluate mathematical terms |
| Unit converter | Settings > Search (opens Unit converter) | On | Convert units |
| Wikipedia | Settings > Search (opens Wikipedia) | On | Search the free encyclopedia. Needs Online results |
| Websites | Settings > Search | On | Show a preview if the query is a URL. Needs Online results |
| Places | Settings > Search (opens Places) | On | Search the local area. Needs Location permission |
| Quick actions | Settings > Search | Nine on | Configure quick actions and search shortcuts |
| Excluded search results | Settings > Search | None | Manage hidden items |
| Tags | Settings > Search | None | Manage tags and tagged items |
| Default filter | Settings > Search | All categories, online off | Filters that are on when a search starts |
| Show filter bar | Settings > Search | On | Show quick filters above the keyboard |
| Customize filter bar | Settings > Search | 16 items | Choose and reorder chips (drag and drop). Only visible when the filter bar is on |
| Open keyboard | Settings > Search | On | Show the keyboard when opening the drawer |
| Launch on enter | Settings > Search | On | The Go key launches the best match or quick action |
| Private keyboard | Settings > Search | Off | Asks the keyboard not to learn from what you type |
| Show app recommendations <Badge type="warning" text="experimental" /> | Settings > Search | On | Editorial app suggestion in the drawer and in matching searches |
| Arrangement of search results | Settings > Search | Top-down | Top-down or bottom-up |
| Lock sensitive settings | Settings > Search > Protection | Off | Require authentication for hidden items and excluded folders |
| Use separate lock | Settings > Search > Protection | Off | A custom PIN instead of the device credential |
| Authentication method | Settings > Search > Protection | Device lock | Device lock or biometrics only. Hidden when a separate lock is used |
| Set custom PIN | Settings > Search > Protection | Not set | PIN for the separate lock |
| Lock launcher | Settings > Search > Protection | Off | Require device authentication to see the home screen after leaving it |

The protection settings are explained in [Privacy and protection](../privacy-protection).

### Apps

| Setting | Where | Default | Effect |
| --- | --- | --- | --- |
| Apps | Settings > Search > Apps | On | Show all apps when the search field is empty |
| Show app information | Settings > Search > Apps | On | Version and package name in the app detail card |
| Show apps in a list | Settings > Search > Apps | Off | A list instead of a grid |
| Show icons in the list | Settings > Search > Apps | On | Icons in list mode. Only visible when list mode is on |
| Move frozen apps to the end | Settings > Search > Apps | Off | Group frozen apps at the bottom of the drawer |
| Hide frozen apps | Settings > Freeze Manager | Off | Remove frozen apps from home, drawer and search |
| Frozen app look | Settings > Freeze Manager | Snowflake badge | Grayscale icon or snowflake badge |
| Advanced freeze features | Settings > Freeze Manager | Off | Adds Freeze, Clone to Sandbox, Force stop and Clear cache to the app card |
| Override search grid | Settings > Grid and icons > Drawer | Off | Use your own column count and icon size for the drawer and search results instead of the global grid |

With a work profile or a private space, the empty-search app grid gets one tab per profile and a lock
button (needs Telos as the default home app).

### Favorites and ranking

| Setting | Where | Default | Effect |
| --- | --- | --- | --- |
| Edit favorites | Settings > Search > Favorites | n/a | Open the favorites editor |
| Show frequently used in favorites | Settings > Search > Favorites | Off | Fill free space with items you launch often |
| Number of rows | Settings > Search > Favorites | 1 | Rows of frequently used items |
| Ranking flexibility | Settings > Search > Favorites | Balanced | Stable, Balanced or Variable usage learning |
| Edit button | Settings > Search > Favorites | On | Button to rearrange favorites |
| Compact tags | Settings > Search > Favorites | Off | Hide tag labels or icons |

### Files

| Setting | Where | Default | Effect |
| --- | --- | --- | --- |
| Local files | Settings > Search > Files | On | Search files on the device. Needs storage permission (all files access on Android 10 and newer) |
| File types | Settings > Search > Files | All five on | Documents, Images, Videos, Music, Other files. Shown only when local files are on and permitted |
| Excluded folders | Settings > Search > Files | None | Folders local search never looks into. A path and everything below it is skipped. Locked by *Lock sensitive settings* |
| Nextcloud | Settings > Search > Files | Off | Needs a signed-in account |
| ownCloud | Settings > Search > Files | Off | Needs a signed-in account |
| File plugins | Settings > Search > Files | Off | One switch per installed file plugin. A plugin may need setup first |

### Contacts

| Setting | Where | Default | Effect |
| --- | --- | --- | --- |
| Contacts | Settings > Search > Contacts | On | Search device contacts. Needs the Contacts permission |
| Contact plugins | Settings > Search > Contacts | Off | One switch per installed contact plugin |
| Tap to call | Settings > Search > Contacts | Off | Start a call without confirmation when tapping a number. Needs the Phone permission |

### Calendar and reminders

| Setting | Where | Default | Effect |
| --- | --- | --- | --- |
| Calendar | Settings > Search > Calendar | On | Search calendars on the device. Any synced account works, including DAVx5 |
| Tasks | Settings > Search > Calendar | Off | Search the Tasks.org app. Needs the Tasks permission. Shown when Tasks.org is installed |
| Calendar plugins | Settings > Search > Calendar | Off | One switch per installed calendar plugin |
| Calendars (per provider) | Settings > Search > Calendar > provider | All | Choose which calendars are searched. Excluded calendars are skipped |

### Places

| Setting | Where | Default | Effect |
| --- | --- | --- | --- |
| OpenStreetMap | Settings > Search > Places | On | Search shops and other places |
| Place plugins | Settings > Search > Places | Off | One switch per installed place plugin |
| Search radius | Settings > Search > Places | 1500 m | 500 m to 10 km in steps of 500 m. Shown in your measurement system |
| Map | Settings > Search > Places | On | Map preview in place cards |
| Map theming | Settings > Search > Places | On | Apply the launcher colors to the map |
| Tileserver URL | Settings > Search > Places > Advanced | OpenStreetMap tiles | Missing `https://` is added. If it has no `${z}`, `${x}`, `${y}`, the suffix `/${z}/${x}/${y}.png` is added |
| Hide uncategorized places | Settings > Search > Places > OpenStreetMap | On | Only places with a category such as cafe |
| Overpass URL | Settings > Search > Places > OpenStreetMap > Advanced | `https://overpass-api.de` | Custom Overpass server. A trailing `/api/interpreter` is removed |

Places query a 10 second Overpass request, de-duplicate entries with the same name, category and a
distance under 100 m, and drop anything outside the radius. Without a location fix for 30 seconds the
source gives up.

### Wikipedia, calculator and unit converter

| Setting | Where | Default | Effect |
| --- | --- | --- | --- |
| Wikipedia | Settings > Search > Wikipedia | On | Enable Wikipedia search |
| Wikipedia URL | Settings > Search > Wikipedia | `https://en.wikipedia.org` | Another language edition or a MediaWiki site. Empty means the default |
| Calculator | Settings > Search | On | Evaluate expressions |
| Unit converter | Settings > Search > Unit converter | On | Convert units |
| Currency converter | Settings > Search > Unit converter | On | Download exchange rates hourly in the background (network connected, battery not low) |
| Supported units | Settings > Search > Unit converter | n/a | Help page listing units |
| Currency settings | Settings > Search > Unit converter | By system locale | Choose and order preferred currencies, which are listed first |

### Quick actions, excluded results, tags, filters

| Setting | Where | Default | Effect |
| --- | --- | --- | --- |
| Quick actions list | Settings > Search > Quick actions | Nine built-ins plus two web searches | Add, remove, reorder, edit |
| Show reveal button | Settings > Search > Excluded search results | Off | A button in the search bar to reveal excluded apps |
| Visibility per item | Settings > Search > Excluded search results | Default | App grid and search, search only, or never |
| Tags | Settings > Search > Tags | None | Create, rename, delete, duplicate tags. Pin a tag as a favorite to use it as a folder |
| Auto-organize | Settings > Search > Tags | n/a | One-off action that tags installed apps by their system category. It does not run again by itself |
| Default filter | Settings > Search | See [Filters](#5-filters) | Edit with the same chips |
| Customize filter bar | Settings > Search | See above | Drag to reorder, remove or add chips |

### Search related settings elsewhere

| Setting | Where | Default | Effect |
| --- | --- | --- | --- |
| Search delay | Settings > Performance | Off (0 ms) | Wait 0 to 500 ms (steps of 50) after the last keystroke before searching |
| Search bar style | Settings > Home screen | Transparent | Transparent, Solid or Hidden |
| Different colors in drawer | Settings > Home screen | Off | A separate search bar color scheme when the drawer is open |
| Search bar position | Settings > Home screen | Bottom | Top or bottom |
| Fixed search bar | Settings > Home screen | Off | Do not scroll the bar out of view |
| Remember scroll position | Settings > Home screen | Off | Keep the drawer scroll position when it is closed and opened again |
| Cloud badges | Settings > Grid and icons | On | Badge on files that live in a cloud service |
| Plugin badges | Settings > Grid and icons | On | Mark results that came from a plugin |
| Preferred transliteration | Settings > Language and region | Automatic | Which script conversion is used when matching text |
| Measurement system | Settings > Language and region | System | Used for place distances and the search radius label |

## 7. Search bar and keyboard

| Behavior | Detail |
| --- | --- |
| Text field | Single line, no auto-correct, no automatic capital letters |
| Action key | The keyboard shows **Go** |
| Launch on enter | With the setting on, Go launches the best match, or starts the highlighted quick action. The best match is highlighted in the list or on its chip. With it off, Go does nothing |
| Best match order | Apps, app shortcuts, events, reminders, places, contacts, Wikipedia, websites, files, documents, images, video, music, then quick actions |
| Private keyboard | Sets the "no personalized learning" flag on the field |
| Opening the drawer | Opens the keyboard if *Open keyboard* is on. A swipe-down "peek" does not open it. Tapping the field always does |
| Scrolling | Scrolling the results hides the keyboard and clears the best match |
| Menu button, empty field | Wallpaper picker, Settings, Add widget (when the home screen allows it), Help |
| Menu button, with text | Clears the text and resets filters |
| Funnel button | Shows the filter panel. Shown while search is open |
| Reveal button | An eye-slash button, only when *Show reveal button* is on and there are hidden apps |
| Filter bar | Chips above the keyboard. Hidden when the keyboard is closed |
| Reset on close | Closing the drawer clears the query, resets filters and resets the scroll position (unless *Remember scroll position* is on) |

## 8. Excluded and hidden results

Each saved item has a visibility level:

| Level | Name in settings | Effect |
| --- | --- | --- |
| Default | App grid and search results (apps), Calendar widget and search results (events), Search results (everything else) | Shown everywhere |
| Search only | Search results | Not in the app grid or calendar widget, still found by search and can be frequently used |
| Hidden | Never | Not in search, not in the grid, not in frequently used |

- Change it in the Customize sheet of an item or in Settings > Search > Excluded search results.
- Turn the **Hidden items** filter on to see hidden results again.
- With an empty query, apps set to "search only" are removed from the grid. They are listed behind the
  reveal button (when that setting is on).
- The excluded results page and the excluded folders page can be locked behind authentication, see
  [Privacy and protection](../privacy-protection).
- Wikipedia, website and calculator results have no hide option.

## 9. Permissions per source

Missing permissions never crash search. The group shows a prompt with **Grant** and **Turn off**.

| Source | Permission | Where it is granted |
| --- | --- | --- |
| Contacts | Contacts | Prompt in results, or Settings > Search > Contacts |
| Tap to call | Phone | Settings > Search > Contacts |
| Calendar | Calendar | Prompt in results, or Settings > Search > Calendar |
| Reminders (Tasks.org) | Tasks | Settings > Search > Calendar |
| Local files | Storage (all files access on Android 10 and newer) | Prompt in results, or Settings > Search > Files |
| Places | Location | Prompt in results, or Settings > Search > Places |
| App shortcuts | Default home app role | Prompt in results, or Settings > Search |
| Work profile and private space lock actions | Default home app role | Needs Telos as default home app |
| Nextcloud, ownCloud | Account sign-in | Settings > Search > Files |
| Websites, Wikipedia, Places, cloud files | Internet (no runtime permission) | Online results filter |

## 10. Plugin-provided results

Search plugins are apps that offer results over a content provider. Four result types exist.

| Plugin type | Result | Where it is enabled |
| --- | --- | --- |
| Contact search | Contacts | Settings > Search > Contacts |
| File search | Files | Settings > Search > Files |
| Places search | Places | Settings > Search > Places |
| Calendar | Events and tasks | Settings > Search > Calendar |

Other plugin types (weather, gesture actions, widgets) do not add search results.
- A plugin is off until you switch it on. Some plugins need a one-time setup, then the switch shows
  "Setup required" and a setup action.
- The launcher passes the query, a flag that says whether Online results is on, and the language. The
  plugin may ignore the flag, but well-behaved ones use the network only when it is set.
- Plugin results behave like built-in ones: ranking, hiding, tags, pinning and filters apply. Plugin
  badges mark where they came from.
- See [Plugins and integrations](../plugins-integrations) and the developer guide for the contracts.

## Experimental, unfinished and not wired

::: warning Read before relying on these
Items in this section are present in code or text but are experimental, partly implemented or not
connected to anything you can switch on.
:::

| Item | State |
| --- | --- |
| Search in an app (quick action) | <Badge type="warning" text="experimental" /> Marked experimental in the user guide. Depends on the target app |
| App recommendations | <Badge type="warning" text="experimental" /> Fixed editorial list, affiliate-style card, off with one switch |
| "Advanced search" texts (learning ranking, fuzzy initials such as "PS" for Play Store, quick settings search, contact quick actions, user shortcuts, search frozen apps) | <Badge type="danger" text="not available" /> Only text exists. No setting or code uses it. Do not expect these features |
| Wikipedia switch | Settings > Integrations > Wikipedia. When off, Wikipedia is not searched. The **Online results** and *Wikipedia* filters also apply |
| Google Drive file source | <Badge type="danger" text="not available" /> A stored switch exists, but no screen offers it and there is no Drive code |
| Separate work profile in results | A stored setting exists. Nothing reads it. The work profile and private space always appear as tabs in the empty-search grid |
| Wikipedia images, position on map | Stored settings with no switch and no effect |
| Other conversion dimensions (bitrate, pressure, energy, frequency) | Named in code, no converter. Not available |
| Hidden results during a query | The reveal button is only populated when the query is empty, and lists apps only |

## Verified conversion units (English symbols)

| Dimension | Units |
| --- | --- |
| Length | m, km, dm, cm, mm, in, ft, yd, mi, nmi |
| Mass | kg, g, t, long ton, st, lb, oz, short ton |
| Area | m², km², cm², mm², sq in, sq ft, sq yd, ha, ac |
| Volume | L, mL, m³, cm³, mm³, cu in, cu ft, cu yd, gal US, gal imp, pt US, pt imp, fl oz US, fl oz imp, cup, tbsp, tsp |
| Time | ms, s, min, h, d, a (year) |
| Data | B, kB, MB, GB, TB, kiB, MiB, GiB, TiB, bit, kbit, Mbit, Gbit, Tbit |
| Speed | m/s, km/h, mph, kn |
| Temperature | °C, °F, K |
| Currency | ISO 4217 codes (EUR, USD, JPY and others) from the ECB daily reference rates |

Syntax: `<number> <unit>`, or `<number> <unit> >> <unit>` (also `>` or `-`) for one target. The number
may use `.` or `,` as decimal separator and may have a sign or exponent. Without a target, every unit
of the same dimension is listed. The calculator handles `0x` hex, `0b` binary and `0` octal integers,
shows binary, hex and octal for integer input, accepts `,` as decimal point and `;` as argument
separator, and switches to scientific notation above 1e12 or below 1e-5.
