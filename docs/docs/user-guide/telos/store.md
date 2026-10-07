# Telos Store

An app installer and updater that works like [Obtainium](https://github.com/ImranR98/Obtainium): it
installs apps straight from the place where developers publish them, and tells you when a new version
exists. It also manages the Telos apps.

::: info At a glance
<Badge type="tip" text="no account needed" /> <Badge type="tip" text="Obtainium import and export" />
<Badge type="tip" text="silent updates with Shizuku or root" />
:::

## Sources

| Source | How it is checked |
| --- | --- |
| GitHub | Releases (latest release, optional pre-releases) |
| GitLab | gitlab.com or a self-hosted server, releases API |
| Codeberg, Forgejo, Gitea | Gitea releases API, any compatible server |
| F-Droid | Per-app version list of f-droid.org, or another F-Droid repository |
| IzzyOnDroid | Treated as an F-Droid repository |
| SourceForge | Newest `.apk` in the project's files |
| Any web page | Searched for links to `.apk` files, optionally with a link filter |
| Direct APK link | Fixed download, no version check |

The APK that fits the phone's CPU architecture is chosen. If the newest release has no APK for the phone,
older releases are looked at. Paste a link into **Add an app** and Telos recognizes the source.

## Per-app settings

Open an app in the Store to change how it is tracked.

| Setting | Effect |
| --- | --- |
| Track only | Tells you about new versions, never downloads or installs |
| Stay on this version | No update is offered |
| Skip this version | Ignores one release (and can be undone) |
| Exclude from background checks | Only updated when you ask |
| File name filter | Regular expression that picks the right APK, for example `arm64` or `universal` |
| Pre-releases | Include them (GitHub, GitLab, Gitea sources) |
| Category, note, rename | Organize your list |

## Updates

| Option | Default | Where |
| --- | --- | --- |
| Check interval | 6 hours (Never, 1 h, 3 h, 6 h, 12 h, Daily) | Store settings |
| Only on Wi-Fi | on | Store settings |
| Notify about updates | on | Store settings |
| Install updates automatically | off | Store settings |
| GitHub token (optional) | empty | Raises GitHub's limit from 60 to 5000 requests per hour |

- Background checks run as a periodic job, not as a permanent service.
- Updates are installed without a prompt only when Shizuku or root allows it. Otherwise you confirm in the
  system installer.
- **Check for updates** and **Update all** are on the dashboard.

## Find and organize

Filter chips show **All**, **Updates**, **Installed**, **Not installed** and **Track only**, plus one chip
per category you created. **Add installed apps** lists what is already on your phone and looks up a source
for each one (F-Droid first, then IzzyOnDroid).

## Import and export

Import and export use the Obtainium file format, in both directions, so you can move your list from
Obtainium to Telos and back. `obtainium://add/<address>` and `obtainium://app/<app>` links open in the Store.

## Install and uninstall

| Backend | Behaviour |
| --- | --- |
| Shizuku | Silent install |
| Root | Silent install |
| System installer | Always available, asks you to confirm |

Telos tries them in this order.

## Managing the Telos apps

The **Telos apps** section lists the built-in apps with a description of each one.

| Action | Result |
| --- | --- |
| Install | Shows the app's icon in the app grid and in search |
| Remove | Hides it. The code stays in Telos, so hidden apps cost nothing |
| Store | Cannot be removed, otherwise there would be no way back |

If an app was switched off by the [crash guard](./launcher/privacy-protection#crash-guard), installing it again
resets the counter.

::: warning
Installing APKs from outside an app store means you trust the source. Check the developer and the
signature.
:::
