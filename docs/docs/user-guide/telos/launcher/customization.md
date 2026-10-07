# Customization

Telos can be restyled almost completely: colors, shapes, fonts, transparency, icons, grids, badges,
animations and per-item names and icons. Most of it lives in **Settings > Appearance** and **Settings >
Grid and icons**. This page lists every option with its default. Colors, themed icons and per-item
customization also have their own pages from the original launcher: [Color
schemes](../../customization/color-schemes), [Themed icons](../../customization/themed-icons) and
[Per-item customization](../../customization/per-item-customization).

## Theme at a glance

| Setting | Where | Default | What it does |
| --- | --- | --- | --- |
| Theme | Appearance | Follow system | Follow system, Light, Dark, or **Time of day** |
| Dark theme starts at, light theme starts at | Appearance | 20:00 and 07:00 | Only used by "Time of day" |
| Text size | Appearance | 1.0 | A launcher-only multiplier on top of the system font size, with a live preview |
| Source for dynamic colors | Appearance > Advanced | System | System colors, or colors taken from the wallpaper (compat mode) |
| Presets | Appearance > Presets | n/a | Apply a ready-made bundle |
| Color scheme, Typography, Shapes, Transparency | Appearance | Default | Each has its own list of schemes to pick, edit or duplicate |
| Import theme, Export theme | Appearance | n/a | Share a theme as a `.kvtheme` file |

The "Time of day" mode switches by the clock hours you set, not by sunrise.

## Presets

Presets are bundles shipped with the app. A bundle can set colors, a layout, or both.

| Preset | What it changes |
| --- | --- |
| AMOLED Black | Colors |
| Cyberpunk | Colors |
| Colorful Grid | Colors and layout |
| Classic Grid | Layout |
| Clean Grid | Layout |
| Pixel-style | Layout |

Open Settings > Appearance > **Presets** and tap one. It is applied **immediately**, without a
confirmation step, and creates or updates the matching color, shape, typography, transparency or layout
settings. A layout preset overwrites grid and home screen settings, so note your current values first if
you want to return to them.

## Colors, shapes, typography, transparency

Each of these is a list of **schemes**. Pick one, or create your own with the **+** button, and use the
menu to edit, duplicate or delete it.

| Area | Built-in schemes | Custom |
| --- | --- | --- |
| Color scheme | Default (Material You on Android 12+), High contrast, Black and white | Key colors, then generated tones. Palette, custom or "from primary color" |
| Shapes | Default, Cut, Extra round, Rectangular | Base shape plus per-size overrides (extra small to extra extra large) |
| Typography | Google Sans, Google Sans (Rounded), System default, Serif, Monospace | Add or remove font styles and choose a device or generic font for each |
| Transparency | Default, Semi-transparent | Opacity of the Background, Surface and Elevated surface layers |

::: tip Custom scheme how-to
1. Settings > Appearance > Color scheme, tap **+** (New color scheme) or the menu of an existing scheme
   and **Duplicate**.
2. Set the key colors. A color can come from the palette, be a custom color, or be generated from the
   primary color. The original [Color schemes](../../customization/color-schemes) page lists every key
   color.
3. Go back and select the scheme.
:::

Themes are exported as `.kvtheme` files (the name comes from the original launcher). Exporting lets you
add a name and author, and share the file or save it as a file. Importing shows the
contents first and says if a theme of that name already exists and will be updated.

## Grid

Settings > Grid and icons.

| Setting | Default | What it does |
| --- | --- | --- |
| Number of columns | 5 | Columns of the grid. Home screen, search and dock can override it separately |
| Icon size | 48 dp | Icon size, with per-area overrides |
| Show labels | On | The name below the icon |
| Label size | 12 | Text size of labels |
| Label max lines | 1 | How many lines a label may use |
| Label shadow | Off | A text shadow for readability on busy wallpapers |
| Label color | Auto | Fixed label color |
| Show apps in a list | Off | A list instead of a grid, with optional icons (Settings > Search > Apps) |
| Override home grid | Off | Separate columns and icon size for the home screen |
| Override search grid | Off | Separate columns and icon size for the drawer |
| Drawer background | Off | A plate behind the drawer with color and opacity (default 90 percent) |
| Folder background color | Theme | Folder popups |
| Folder covers | On | A fading cover icon while a folder opens |

The dock has its own grid and background, see [Home screen](./home-screen#dock).

## Icons

| Setting | Default | What it does |
| --- | --- | --- |
| Shape | Platform default | Platform default, Circle, Square, Rounded square, Squircle, Reuleaux triangle, Hexagon, Pentagon, Teardrop, Pebble |
| Enforce shape | Off | Apply the shape also to icons that do not support it |
| Themed icons | Off | Color icons with the launcher color scheme |
| Enforce themed icons | Off | Apply theming to all icons, not recommended |
| Icon pack | None | Choose one installed icon pack |
| Icon cache size | 200 | Settings > Performance, more cached icons use more memory |

::: warning Fallback icon packs have no setting yet
The icon service can fall back to a second and third icon pack when the first has no icon for an app, and
the setting is stored. Settings does not expose it in this build, so only the single "Icon pack" choice
is usable. See [icon packs](../../../developer-guide/integrations/icon-packs) for how packs are matched.
:::

### Badges

Badges are small marks on an icon. Settings > Grid and icons > Badges.

| Badge | Default | Meaning |
| --- | --- | --- |
| Notification badges | On | Unread notifications. Needs notification access. Style: dot (default) or count |
| Cloud badges | On | A file is stored in the cloud |
| Suspended apps | On | The app is suspended or frozen |
| Shortcut badges | On | Which app a shortcut belongs to |
| Plugin badges | On | Which plugin produced a search result |
| Badge color | Theme tertiary | One color for all badges |

[Frozen apps](../freeze/) can also be shown grayscale or with a snowflake badge (Freeze Manager).

## Animations and motion

| Setting | Where | Default | What it does |
| --- | --- | --- | --- |
| Charging animation | Home screen | On | Bubbles while charging |
| Reduce animations | Performance | Off | Instant transitions |
| Animation speed | Performance | 1.0 | Scales the length of fade and effect motion |
| Bounce physics | Performance | 1.0 (no bounce) | Lower values overshoot before settling after a gesture |

The launcher uses Material 3 expressive motion. More about the cost of animation in
[Performance](./performance).

## Per-item customization

Long-press any app or search result and tap **Customize** (or the pen icon).

| What | Details |
| --- | --- |
| Label | A new name. The original name still matches in search |
| Icon | Default, original icon, adaptive transforms, any icon of an installed pack, a themed icon, a placeholder |
| Tags | See [Favorites and tags](./favorites-tags) |
| Show in | App grid and search, search only, or never (see [Favorites and tags](./favorites-tags#hiding-instead-of-removing)) |
| Shutter | Set, change or remove the app that a swipe up on the icon launches |
| Web apps | Name, URL, icon source, custom CSS, notifications, see [Desktop and overlays](./desktop-and-overlays#web-apps) |

## Language and region

Settings > Language and region.

| Setting | What it does |
| --- | --- |
| Language | Any of the launcher's languages. Greek and German come with Telos in addition to the original locales |
| Form of address | Neutral, feminine or masculine, where the language has it |
| Preferred transliteration | Which transliterator normalizes text for search. Automatic by default |
| Measurement system | System, metric, UK or US |
| Time format | System, 12-hour or 24-hour |
| Currency | The order of currencies in the unit converter |
| Calendar system | A primary and an optional secondary calendar |

## Limitations

- Fallback icon packs, as described above, have no UI.
- A layout preset overwrites settings, there is no one-tap undo.
- Video wallpaper theming and the wallpaper settings are in [Home screen](./home-screen#wallpaper).
