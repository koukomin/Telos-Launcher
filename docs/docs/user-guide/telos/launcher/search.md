# Search

Search is the main way to reach things in Telos. One text field finds apps, contacts, events, files,
places, articles and more, and it can also calculate, convert units and offer quick actions. This page
lists every search source, what it needs, how results are ranked and shown, and where to change it.

::: info Where the settings are
All search settings live in **Settings > Search**. Some sources open their own sub page there. The
"Where" column names that sub page.
:::

## Sources at a glance

| Source | What it finds | Needs | Online? | Default |
| --- | --- | --- | --- | --- |
| Apps | Installed apps of every profile, plus the [Telos apps](../) that are switched on. Telos apps are also found by synonym keywords in English and Greek, for example `pdf`, `gallery`, `photos` or `έγγραφα` for Telos Viewer | Nothing | No | On |
| Web apps | [Web app shortcuts](./desktop-and-overlays#web-apps) that are shown in the grid | Nothing | No | On |
| App shortcuts | Launcher shortcuts that apps publish (for example "New message") | Telos set as the default home app | No | On |
| Contacts | Android contacts, plus contact plugins | Contacts permission | No | On |
| Calendar | Events of the Android calendar, plus calendar plugins | Calendar permission | No | On |
| Reminders | Tasks from the Tasks app, and tasks that calendar plugins deliver | Tasks permission, and the Tasks source switched on under Calendar | No | Filter on, source off |
| Files | Local files, Nextcloud, ownCloud and file plugins | Storage permission (local) or an account (cloud) | Cloud only | Local on |
| Places | OpenStreetMap places near you, plus place plugins | Location permission | Yes | On |
| Websites | A preview card for a URL you type | Network | Yes | On |
| Wikipedia | Articles | Network | Yes | On |
| Calculator | The result of an expression | Nothing | No | On |
| Unit converter | Units and currencies | Nothing (currencies download rates) | Rates only | On |
| Quick actions | Call, message, web search and more for what you typed | Depends on the action | No | On |
| Tags and custom names | Items you tagged or renamed | Nothing | No | Always |

Sources that are marked "Online" never run on a normal search. See [Online results](#online-results).

## How a search works

1. You type. If Settings > Performance > **Search delay** is above zero, Telos waits that long after
   the last keystroke (default: off, search runs on every keystroke).
2. Every enabled source that matches the active [filters](#filters) is queried in parallel. A slow
   source never blocks a fast one: results appear as they arrive.
3. Places wait 250 ms and Wikipedia 750 ms before they start, so they do not fire on every letter.
4. Excluded results are removed (see [Excluded results](#excluded-results)), then each list is ranked,
   de-duplicated by item key and shown in its own group.

## Ranking

Most lists (apps, shortcuts, files, contacts, events, reminders, articles, websites, web apps) are
ordered by one number: **0.6 x match score + 0.4 x usage weight**.

- **Match score** compares your text with the item name. A name that starts with your text or contains
  it scores higher, and similar-sounding names (Jaro-Winkler similarity) get partial credit. Matches on
  a secondary field count 20 percent less than a match on the name.
- **Usage weight** grows each time you launch an item and decays for everything else, so what you use
  often moves up.
- Apps need a match score of at least 0.8 to appear at all, which keeps the list short.
- A custom name (see [Customization](./customization#per-item-customization)) is what is matched
  and shown. Tags are searched too, see [Favorites and tags](./favorites-tags).
- Places are sorted by **distance** from your last known location when one is cached. Without a
  location they are ranked like the other lists.
- Text is normalized before it is compared. Settings > Language and region has a **transliterator**
  option so, for example, accented or non-Latin names can match Latin input.

| Setting | Where | Default | What it does |
| --- | --- | --- | --- |
| Ranking flexibility | Settings > Favorites | Balanced | How quickly the usage weight reacts to a launch. Stable, Balanced, Variable (steps of 0.01, 0.03 and 0.1 per launch) |

## Result groups, top to bottom

When the search field is empty you see the favorites, pending updates from the [Store](../store/) (if any),
the app grid and your web apps. Once you type, these groups can appear, in this order:

| Order | Group | Notes |
| --- | --- | --- |
| 1 | Favorites | Hidden while you type |
| 2 | Apps and web apps | A grid, or a list if the grid is set to list |
| 3 | App shortcuts | Shows a permission prompt if the default-home-app role is missing |
| 4 | Unit converter | Several lines, one per target unit |
| 5 | Calculator | One card with the result and a copy action |
| 6 | Events, then reminders | Reminders appear as tasks |
| 7 | Contacts | Call, message and email buttons per contact |
| 8 | Places | Map preview, opening hours, distance |
| 9 | Wikipedia, then websites | Only with online results |
| 10 | Files, documents, images, video, music | Type groups replace the plain file list, see [Filters](#filters) |
| 11 | Recommended app | See [App recommendations](#app-recommendations) |

Each group shows **five** items and a "show all" button. The button expands that group, and when only
one category is enabled in the filter, that group starts expanded. Quick actions are not part of the
list: they sit next to the search bar.

Settings > Search > **Arrangement of search results** can put the list bottom-up, so the best results
sit next to a search bar at the bottom of the screen.

## Filters

Filters narrow the search by type. They are covered in the original
[Filters](../../search/filters) page. This is how Telos applies them.

- **Type filters:** Apps, Contacts, Events, Reminders, Files, Documents, Images, Video, Music, Places,
  Articles (Wikipedia), Websites, Shortcuts and Tools (calculator and unit converter).
- **Online results** (off by default) and **Hidden results** are separate switches.
- **Files versus file types:** the generic *Files* filter and the *Documents / Images / Video / Music*
  filters are alternatives. If *Files* is on, you get one list. If it is off, files are split by type, so
  one file never shows in two groups.

| Setting | Where | Default | What it does |
| --- | --- | --- | --- |
| Default filter | Settings > Search | All types, online off | Which filters are on when you start typing |
| Show filter bar | Settings > Search | On | A row of quick filters above the keyboard |
| Customize filter bar | Settings > Search | 16 items | Choose and reorder the entries (drag and drop) |

::: warning Default filter with online results
If you switch online results on in the default filter, every query you type is sent to external web
services. Telos shows a warning in the filter settings for this reason.
:::

## Online results

Network lookups only run when the **Online results** filter is on for that search.

| Source | Sends | Minimum text |
| --- | --- | --- |
| Websites | The URL you typed, to that website (3 s timeout) | Any |
| Wikipedia | Your query, to the Wikipedia API | 4 characters |
| Places | Your query and area, to an Overpass server | 2 characters |
| Nextcloud, ownCloud | Your query, to your own server | 4 characters |
| Plugins | Whatever the plugin does | Set by the plugin |

Websites read the page title, description, image, theme color and icon. Apps, contacts, calendar, local
files and tools never use the network.

## Source settings

### Apps

| Setting | Where | Default | What it does |
| --- | --- | --- | --- |
| Apps | Settings > Search > Apps | On | Show all apps when the search field is empty |
| Show app information | Settings > Search > Apps | On | Version and package name in the app menu |
| Show apps in a list | Settings > Search > Apps | Off | A list instead of a grid, with optional icons |
| Move frozen apps to the end | Settings > Search > Apps | Off | Group [frozen](../freeze/) apps at the bottom of the drawer |
| Hide frozen apps | Settings > Freeze Manager | Off | Remove frozen apps from home, drawer and search |

With a work profile or a private space, the app grid gets tabs. A lock button pauses or unlocks the
profile (Telos must be the default home app for this).

### Files

| Setting | Where | Default | What it does |
| --- | --- | --- | --- |
| Local files | Files | On | Search documents, photos and other files on the device |
| File types | Files | All five | Documents, images, music, videos, other files |
| Excluded folders | Files | None | Folders that are never searched |
| Nextcloud, ownCloud | Files | Off | Search your cloud, needs an [account](./plugins-integrations#nextcloud-and-owncloud) |

Local search reads the Android media database. It needs the storage permission (all files access on
Android 10 and newer), skips excluded folders, and stops after ten hits or 500 scanned rows. A query of
three characters or fewer matches the **start** of a file name only, longer queries match anywhere.

### Contacts, calendar and reminders

| Setting | Where | Default | What it does |
| --- | --- | --- | --- |
| Contacts | Contacts | On | Search device contacts (and contact plugins) |
| Tap to call | Contacts | Off | Start a call without confirmation when tapping a number. Needs the phone permission |
| Calendar | Calendar | On | Any synced calendar account works, including DAVx5 |
| Calendars | Calendar | All | Pick which calendars are searched |
| Reminders | Settings > Search | On | Whether reminders are part of the default filter. They only appear once Tasks is switched on under Calendar and the Tasks permission is granted |
| Tasks | Calendar | Off | Search the Tasks app, see [Tasks](./plugins-integrations#tasks) |

Contact matching checks the display name, the alternative name, the phonetic name and the sort key, and
returns at most about fifteen contacts. The [Telos Phone](../phone/) contacts are the same Android
contacts.

### Places

| Setting | Where | Default | What it does |
| --- | --- | --- | --- |
| OpenStreetMap | Places | On | Search shops and other places in the area |
| Search radius | Places | 1500 m | How far around you to look |
| Hide uncategorized places | Places | On | Only places with a clear category, like cafes |
| Map | Places | On | Show a map preview |
| Map theming | Places | On | Apply the launcher color scheme to the map |
| Overpass URL | Places > OpenStreetMap | Public server | Use your own Overpass instance |
| Tileserver URL | Places > Advanced | Public server | Use your own tile server |

Places need the location permission. Telos shows the places closest to your last cached position first.

### Wikipedia, calculator and unit converter

| Setting | Where | Default | What it does |
| --- | --- | --- | --- |
| Wikipedia | Wikipedia | On | Needs online results |
| Wikipedia URL | Wikipedia | Default | Use another language edition or a compatible wiki |
| Calculator | Settings > Search | On | Evaluate mathematical terms |
| Unit converter | Settings > Search | On | `1.5 kg` or `4 cm >> in` |
| Currency converter | Unit converter | On | Download exchange rates periodically |

The calculator understands hexadecimal (`0xFF`), binary (`0b101`) and octal (`017`) literals and normal
expressions. A comma is accepted as the decimal separator. The unit converter handles time, length,
mass, area, volume, speed, data and temperature, and currencies. See the existing pages
[Calculator](../../search/calculator) and [Unit Converter](../../search/unit-converter).

Exchange rates come from the European Central Bank daily file. They download in the background when a
network is available and the battery is not low.

## Quick actions

[Quick actions](../../search/quickactions) turn what you typed into an action: call, message, email,
add contact, set alarm, start timer, schedule event, open website, share, and web search. Telos adds
two more: **Lock or unlock the private space** (Android 15 or newer, and only if a private space
exists) and **Pause or resume the work profile**. They appear when your text matches "Private space" or
"Work profile". Web search, app search and custom intent actions are created in Settings > Search >
Quick actions. A text classifier decides which built-in actions fit what you typed.

## Search bar behavior

| Setting | Where | Default | What it does |
| --- | --- | --- | --- |
| Open keyboard | Settings > Search | On | Show the keyboard when the drawer opens |
| Launch on enter | Settings > Search | On | The Go key launches the best match or quick action |
| Private keyboard | Settings > Search | Off | Asks the keyboard not to learn from what you type |
| Search bar position | Settings > Home screen | Bottom | Top or bottom, see [Home screen](./home-screen#search-bar) |

**Launch on enter** picks the best match in this order: apps, shortcuts, events, reminders, places,
contacts, articles, websites, files (and types), then quick actions.

## Excluded results

Long-press an item and hide it, or manage the list in Settings > Search > **Excluded search results**.
Each item can be shown in the *app grid and search results*, in *search results* only, or *never*.
**Show reveal button** adds a button to the search view that shows excluded results. If
[Lock sensitive settings](./privacy-protection#settings-lock) is on, this page asks for authentication.

## App recommendations

<Badge type="warning" text="experimental" />

Telos can show one editorial app suggestion (VPN, mail, password manager, cloud storage, productivity
or browser) in the drawer and in results that match a category word such as "vpn". The card is labeled
"Affiliate - Supports Telos" and links to a store page. The list is fixed in the app, not personalized
and not downloaded. Turn it off in Settings > Search > **Show app recommendations**, or use "Hide this
tab" on the card.

::: warning Honest note
The code states that no affiliate agreement exists yet. The label is shown in advance. Switch the
feature off if you do not want any promotion in your launcher.
:::

## Limitations

- Each source returns a limited number of hits (about 10 local files, about 15 contacts).
- Search is a name match. There is no search inside file contents.
