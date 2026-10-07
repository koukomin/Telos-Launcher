# Favorites and tags

Favorites and tags are the two ways to organize things yourself. **Favorites** answer "what do I use
most?". **Tags** answer "what is this about?". Both work for apps and for every other kind of search
result, and they feed each other: a tag can be pinned as a favorite and then behaves like a folder.

The original concepts are explained in [Favorites](../../concepts/favorites) and
[Tags](../../concepts/tags). This page adds what is specific to Telos and verified against the code.

## Favorites

Favorites are stored per item with three levels:

| Level | Meaning | Order |
| --- | --- | --- |
| Pinned, manually sorted | You pinned it and placed it | The order you set |
| Pinned, automatically sorted | You pinned it, Telos orders it | By how often you use it |
| Not pinned, frequently used | Shown only if there is room | By how often you use it |

Whatever you launch from search, the app grid or the favorites raises that item's **usage weight** and
lowers the weight of everything else a little. The same weight also helps order [search
results](./search#ranking).

### Pin and unpin

1. Long-press an app or any search result.
2. Tap the star (**Pin to favorites**). Tap it again (**Unpin**) to remove it.
3. To place a pinned item by hand, open **Edit favorites** (the pen icon on the favorites panel, or
   Settings > Search > Favorites) and drag it into "Pinned - manually sorted".

Dragging an item in the "automatically sorted" section has no effect. Move it to the manual section
first.

### Where favorites appear

| Place | Default | Notes |
| --- | --- | --- |
| Above the app grid and under search | On | Switch in Settings > Search > **Favorites**. Hidden while you type |
| Dock | Off | A normal dock shows pinned items, see [Home screen](./home-screen#dock) |
| Clock dynamic zone | Off | The first row of favorites |
| Calendar widget | n/a | Pinned **calendar events** show in the calendar widget, not in the other places |

The Favorites widget is no longer in the widget picker, see [Widgets and feed](./widgets-feed).

### Settings

| Setting | Where | Default | What it does |
| --- | --- | --- | --- |
| Favorites | Settings > Search | On | Show pinned and frequently used items above the app grid |
| Edit favorites | Settings > Search > Favorites | n/a | Reorder pinned items, add shortcuts, manage tags |
| Frequently used: show in favorites | Same | On | Fill free room with non-pinned, frequently used items |
| Number of rows | Same | 1 | Rows of frequently used items (the last pinned row is filled first) |
| Ranking flexibility | Same | Balanced | How strongly one launch changes the weights: Stable, Balanced or Variable |
| Edit button | Same | On | The pen icon on the favorites panel |
| Compact tags | Same | Off | Hide tag labels or icons to save space |

Ranking flexibility changes the step per launch: Stable 0.01, Balanced 0.03, Variable 0.1. A high value
makes the order react fast and also forget fast.

::: tip Shortcuts as favorites
App shortcuts (for example "New message") can be pinned like any other item. Creating them from the
favorites editor needs Telos to be the default home app.
:::

## Tags

A tag is a word you attach to items. Tags have two jobs:

- **Search keyword.** Typing a tag finds every item that carries it, even if the name does not match.
- **Favorite folder.** A tag that you pin shows as a chip in the favorites panel. Selecting it filters
  the panel to that tag's items.

### Create and assign

Pick the way that fits:

1. **From an item.** Long-press a result, choose **Customize**, and type comma-separated tags into the
   tags field.
2. **From the favorites editor.** Open **Edit favorites**, press **+** in the tags section, choose or
   create a tag, then drag items onto it.
3. **From settings.** Open Settings > Search > **Tags**, tap **+** (New tag), enter a name, and use
   **Select items** to tick the apps and results that belong to it.

A tag can have an **icon** or an **emoji**.

### Manage

| Action | How |
| --- | --- |
| Rename | Open the tag and change the name |
| Merge | Rename a tag to the name of another one. Telos asks, then merges the contents |
| Duplicate | The menu on the tag |
| Delete | The menu on the tag. A tag with no items is deleted when you save it |

### Auto-organize

<Badge type="tip" text="Search, Tags" />

**Auto-organize** groups your installed apps into tags named after their Android category (Games,
Social, and so on).

1. Open Settings > Search > Tags and tap **Auto-organize**.
2. Telos reads each app's system category and builds one tag per category.
3. Pin the tags you like as favorites to get instant folders.

Details that matter:

- A category with fewer than two apps is skipped, and apps with no declared category are left out.
- It does **not** run by itself. Run it again after installing new apps.
- It **replaces the contents** of an existing tag that has the same name as a category. Rename your own
  tag if you want to keep it.

### Folders

A pinned tag works as a folder of anything you tagged, not only apps, and its panel says "Nothing tagged
yet" when empty. For a classic home screen folder, use Settings > Grid and icons > **Create folder**
(pick at least two apps). It places the folder in the dock, and **Folder covers** shows its first icon
as a fading lid while it opens.

## Hiding instead of removing

You can also decide where an item appears without deleting it. Long-press it, tap **Customize**, and
choose **Show in**:

| Choice | The item shows in |
| --- | --- |
| App grid and search results | Everywhere (default for apps) |
| Calendar widget and search results | Default for events |
| Search results | Only when you search for it |
| Never | Nowhere, except when you reveal hidden results |

The list of hidden items is Settings > Search > **Excluded search results**. See
[Privacy and protection](./privacy-protection#hidden-items) for how to lock that page.

## Backup

Pinned items, usage weights, visibility, custom names, custom icons and tags are part of the launcher
backup. See [Privacy and protection](./privacy-protection#backup-and-restore).

## Limitations

- Tags are plain names. They do not nest.
- Auto-organize depends on the app category that each app declares, which many apps leave empty.
- The usage weight is local. It is not synced between devices, except through a backup restore.
