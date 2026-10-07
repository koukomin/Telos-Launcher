# Telos Notes

A notes app that keeps your notes on the phone and can copy them to a folder of Markdown files or to Nextcloud Notes.

::: tip At a glance
Colours, pins, labels, archive and trash. Sync with a Markdown folder (Obsidian, Logseq, Syncthing, a cloud app's folder)
and with Nextcloud Notes. Import from Google Keep, Evernote, Markdown and text files. The notes are part of the Telos backup.
:::

## What it is

Telos Notes is a [virtual app](../#how-the-built-in-apps-work), part of the launcher. The notes are stored on the phone,
one file per note, in the private storage of Telos. Nothing leaves the phone unless you set up a sync. Switching it off in
[Telos Store](../store/) hides its icon.

## Writing notes

| Action | How |
| --- | --- |
| New note | The **+** button |
| Pin, colour, labels, archive, share, delete | The icons above the note |
| Search | The magnifier. It looks in titles, text and labels |
| Filter by label | The chips under the title |
| Archive or restore from the archive | Long-press a note in the list |
| Trash | Menu > **Trash**. Tap a note to restore it, long-press to delete it for good, menu > **Empty the trash** |

Checklists are plain Markdown lines: `- [ ] item` and `- [x] done`. An empty note is not saved.

## Sync

Menu > **Sync**. The newest change wins, there is no merge inside a note. Telos syncs when you tap **Sync now** or save the
settings, not in the background.

| Target | How it works |
| --- | --- |
| **Markdown folder** | Pick any folder. Every note becomes a `.md` file with a small front matter block (`title`, `pinned`, `tags`). Files that are new in the folder become notes. Use an Obsidian or Logseq vault, a Syncthing folder or the folder of a cloud app (Dropbox, Google Drive, OneDrive) to carry the notes to other devices |
| **Nextcloud Notes** | Server address and an **app password** (Nextcloud > Settings > Security). Notes are created, updated and deleted both ways. The first label is the Nextcloud category |

The sync login is kept in the private preferences of Telos. It is **not** part of the backup.

### What can and cannot be synced

| App | Support |
| --- | --- |
| Obsidian, Logseq, Joplin (file sync), any Markdown app | Yes, through the Markdown folder |
| Nextcloud Notes | Yes |
| Syncthing and cloud folders | Yes, through the Markdown folder |
| Google Keep | **Import only** (Google Takeout). Google offers no API that a launcher can use |
| Evernote | **Import only** (.enex export) |
| Notion | **Import only** (export as Markdown, then import the zip). The Notion API needs a developer integration per workspace |
| Microsoft OneNote, Microsoft Sticky Notes | Not possible. They only sync through Microsoft's own apps. Export to Markdown from there and import |
| Apple Notes | Not possible. There is no API outside Apple devices |

## Import

Menu > **Import notes**, choose one or more files:

- Google Keep: the `.json` files from Google Takeout (or the whole `.zip`), with colours, pins, labels, archive and checklists
- Evernote: `.enex` files
- Markdown and text: `.md`, `.markdown`, `.txt`, and `.zip` archives of them (Notion and Joplin exports, Obsidian vaults)

Importing again adds the notes again, so import a set once.

## Backup

The notes are the **Notes** part of the Telos backup (Settings > Advanced > Backup and restore). You can back up and restore
them on their own, see [Backup and restore](../launcher/privacy-protection#backup-and-restore). On restore, a note that
exists on both sides stays as the newer one.

## Limitations

- Notes are not encrypted
- Plain text with Markdown lines, no images or attachments, no formatting toolbar
- No background sync, no merge of two changes to the same note
- Notes deleted in the Markdown folder go to the trash in Telos, but a note you trash in Telos is not removed from the folder
