# Sources, updates and installing

Where the Store gets apps from, which addresses it understands, the per-app options, how update checks and
notifications work, how an APK is installed or removed, and how to move your list to or from Obtainium. For the screens
see the [Store overview](./).

## Sources

Paste any of these addresses into **Add app** and Telos recognizes the source.

| Source | Address that is recognized | How it is checked |
| --- | --- | --- |
| GitHub | `github.com/owner/repo` | Releases of the repository: the newest 15 are read, drafts are skipped, pre-releases only when enabled. The first release that has a matching APK wins |
| GitLab | `gitlab.com/...`, or a host starting with `gitlab.` | The releases API. Sub groups in the path are kept |
| Codeberg, Forgejo, Gitea | `codeberg.org/owner/repo`, `gitea.com`, hosts starting with `gitea.`, `forgejo.` or `git.` with a two-part path | The Gitea releases API of any compatible server |
| F-Droid | `f-droid.org/packages/<package>` | The per-app version list of f-droid.org |
| IzzyOnDroid | `apt.izzysoft.de/...` with the package name | The same, from the IzzyOnDroid repository |
| SourceForge | `sourceforge.net/projects/<name>` | The newest `.apk` in the project's files |
| Any web page | Any other `http(s)` address | The page is searched for links to `.apk` files, or for links that match a link filter. The link with the highest version wins |
| Direct APK link | An address that ends in `.apk` | A fixed download. There is no version check, so it never reports updates |

Notes:

- The APK that fits the phone's CPU is chosen: the one that names your architecture first, then universal ones, and
  other CPU types last. If the newest release has no APK for the phone, older releases are looked at.
- The update engine can read any F-Droid repository through its `index-v1.json`, but the Add dialog only creates
  F-Droid sources for `f-droid.org` and IzzyOnDroid addresses.
- Only `http` and `https` addresses work.
- An address that is already in your list is refused with "This app is already in your list".
- The name is guessed from the address and can be changed under **Rename**.

### Obtainium sources that are not supported

When you import an Obtainium file, entries from APKMirror, Uptodown, Aptoide, APKPure, the Play Store, Huawei,
Tencent, RuStore and Telegram are counted as unsupported and skipped.

## Per-app settings

Open an app in the Store to change how it is tracked.

| Setting | Default | Effect |
| --- | --- | --- |
| Track only | off | Tells you about new versions, never downloads or installs them. **Update all** skips it |
| Stay on this version | off | No update is offered. The row shows "pinned" |
| Skip this version | none | Ignores exactly one release. The next release is offered again. Undo with **Stop skipping** |
| Exclude from background updates | off | The app is only checked and updated when you ask |
| File name filter | empty | A regular expression that picks the right APK from the release, for example `arm64` or `universal`. Empty means the best match for this phone |
| Include pre-releases | off | Pre-releases are considered (GitHub, GitLab and Gitea sources) |
| Category | empty | A label for grouping. Every category becomes a filter chip |
| Note | empty | A free text note |
| Rename | | Changes the name in the list |

After changing the filter or pre-releases choose **Apply and check** to look at the source again at once. A filter that
does not parse as a regular expression is ignored.

## Updates

| Option | Default | Where | Effect |
| --- | --- | --- | --- |
| Check for updates | 6 hours | Store settings | Never, 1 h, 3 h, 6 h, 12 h, Daily |
| Only on Wi-Fi | on | Store settings | The check runs only on an unmetered connection |
| Notify about updates | on | Store settings | A notification for new versions |
| Install updates automatically | off | Store settings | Installs updates without asking, only when Shizuku or root allows it |
| GitHub token (optional) | empty | Store settings | Raises GitHub's limit from 60 to 5000 requests per hour |

### How the background check works

- It is a periodic background job of Android (not a permanent service). The interval you choose is the repeat time, and
  Android decides the exact moment. "Never" cancels the job.
- Apps marked **Exclude from background updates** are skipped, and so are apps that are only a Play Store listing.
- An app has an update when the release has a higher version code than the installed one, or, when the source gives no
  version code, when the version numbers in the version names are higher (so `v1.4.2` against `1.4.2` works).
- Pinned apps and a skipped version are not reported.
- After a run, the time of the last check is saved.
- If five or more sources fail and nothing else happened, the job is retried later. A few unreachable sources next to
  working ones do not cause a retry.

### Notifications

A notification on the channel "App updates" says "Update for (app)" or "N app updates", or, after silent installs, "(app)
was updated". It only announces versions you have not been told about yet, so you do not get the same message every six
hours. Tapping it opens the Store. Notifications are not counted as announced when they are switched off.

### Manual checks

- **Check for updates** on the dashboard checks everything. The message says "N update(s) available", "Up to date, N
  source(s) could not be reached" or "Everything is up to date".
- **Check now** on an app page checks that app.
- **Update all** installs every available update one by one.

## Install and uninstall

The APK is downloaded to the app's cache and then installed.

| Backend | Behavior |
| --- | --- |
| Shizuku | Silent install through `pm install` in Shizuku's shell |
| Root | Silent install through the superuser |
| System installer | Always available. Android asks you to confirm each install |

Telos tries them in this order. If a silent backend is available but fails, Telos reports the error and does **not**
fall through to the prompt, so you are not surprised by a second install dialog.

- **Safety check:** the downloaded file is checked with Android before installing. If it is for another package than the
  tracked app, it is deleted and you see "This file is for X, not for Y". For an app added by address, the package name
  is learned from the first download.
- Android itself refuses an update signed with a different key than the installed app.
- **Uninstall app** on the app page asks Android to remove the package, and Android shows its own confirmation.
- **Remove from list** only removes the entry from the Store. The installed app stays on the phone.

### Silent updates

Silent installs happen in two cases: you install from the Store while Shizuku or root is allowed, or the background
check runs with **Install updates automatically** on. Updates of apps set to Track only are never installed.

## Add installed apps

**More > Add installed apps** lists the apps on the phone. Select some and tap **Add (N)**. For each one Telos looks for
a source, F-Droid first and then IzzyOnDroid, and adds the ones it finds. Apps found nowhere are not added.

## Import and export

Import and export use the **Obtainium export file format**, in both directions, so you can move your list from
Obtainium to Telos and back.

| Action | Where | Result |
| --- | --- | --- |
| Export | More > Export (Obtainium file) | A JSON file `telos-store-export.json` with an `apps` list. Each app has its id, address, name, versions, additional settings (track only, exempt from background updates, pre-releases and the file name filter), pinned and category |
| Import | More > Import (Obtainium file) | Adds every app whose address Telos understands. A summary tells how many were added, skipped (already in the list) and unsupported |
| Share one app | App page > Share link | An `obtainium://add/<address>` link |
| Open a link | | `obtainium://add/<address>` fills the Add dialog, `obtainium://app/<definition>` adds the app, with the message "App added", "Already in your list" or "This app is not supported" |

Only the first category of an app is kept on import. Installed state is taken from the phone, not from the file.

## Privacy and permissions

- Requests go only to the sources of the apps you track, and to GitHub with your token when you set one (kept in the
  private Store preferences).
- The list, options and cache stay on the phone. Per-app and global options are in private preferences, not in the
  database.

## Limitations

- No split APKs or bundles, and no sources that need a login.
- Direct APK links have no version check.
- One category per app on export.
- Update detection depends on version names for sources without version codes. Unusual names may be compared wrongly.
- Android may delay the background job, especially on strict battery settings.

## Troubleshooting

| Problem | Try this |
| --- | --- |
| "Could not reach the source of (app)" | Check your network and the source address. Open the address in a browser |
| "No release found" | The repository has no APK in its releases, or the file name filter excludes them |
| Wrong APK is picked | Set a **File name filter**, for example `arm64` or `universal` |
| "This is not a web address" | Enter a full `http(s)` address |
| "GitHub's request limit is reached" | Add a token in Store settings |
| Update is not installed in the background | Turn on **Install updates automatically** and make Shizuku or root available, otherwise only a notification is shown |
| The installer does not open | Allow "Install unknown apps" for Telos in Android settings |
| Imported apps show as not installed | Their package ids differ from the installed ones. Edit the package name or add the app again |
