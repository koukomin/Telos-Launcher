# Telos Store

An app installer and updater that works like [Obtainium](https://github.com/ImranR98/Obtainium): it installs apps
straight from the place where developers publish them, and tells you when a new version exists. It also manages the
Telos apps.

::: info At a glance
<Badge type="tip" text="no account needed" /> <Badge type="tip" text="Obtainium import and export" />
<Badge type="tip" text="silent updates with Shizuku or root" />
:::

::: info This section
- This page: what it is, the screens, permissions, managing the Telos apps and the feature matrix.
- [Sources and updates](./sources-updates): every supported source, per-app settings, update checks, installing,
  import and export.
:::

## What it is

Telos Store keeps a **list of apps you track**. For each one it knows where the developer publishes releases (GitHub,
GitLab, Codeberg, F-Droid and so on), checks that place for new versions, downloads the APK that fits the phone, and
installs it. There is no central Telos catalog and no account. It does not scan or vouch for anything: you choose the
sources.

Telos Store is also where the built-in [Telos apps](../#how-the-built-in-apps-work) are switched on and off. It is
itself a Telos app, but it **cannot be removed**, otherwise there would be no way back.

## How to open it

| From | What opens |
| --- | --- |
| The Telos Store icon in the app grid or search | The dashboard |
| An `obtainium://add/<address>` link | The dashboard with the Add dialog filled in |
| An `obtainium://app/<definition>` link | The app is added at once (the definition is Obtainium's JSON for one app) |

## Permissions

| Needed for | Permission or access | Notes |
| --- | --- | --- |
| Checking sources and downloading | Internet | Used only for the sources you track |
| Installing apps | "Install unknown apps" (`REQUEST_INSTALL_PACKAGES`) | Android asks the first time. Not needed when Shizuku or root installs silently |
| Uninstalling apps | `REQUEST_DELETE_PACKAGES` | Android shows its own confirmation |
| Update notifications | Notifications | Android 13 and later |
| Silent installs | Shizuku or root | Optional |

## A tour of the screens

### Dashboard

| Element | What it does |
| --- | --- |
| Title "Store" | The screen name |
| **Check for updates** (refresh icon) | Asks every source now |
| **More** menu | **Add installed apps**, **Import (Obtainium file)**, **Export (Obtainium file)**, **Store settings** |
| Tabs **Apps**, **Telos apps** and **Recommended** | Your tracked apps, the built-in Telos apps, and a list of suggested free apps |
| Search field "Search apps" | Matches the name, the package name and the category |
| Filter chips | **All**, **Updates (N)**, **Installed**, **Not installed**, **Track only**, and one chip per category |
| **Update all (N)** | Installs every available update, except for apps set to Track only |
| **Add app** button | Opens the Add dialog |
| App rows | Name, source, the version state and a button: **Install**, **Update**, **Open** or **Retry**, and a progress text "Downloading" or "Installing" |

The list shows apps with updates first, then the others by name. A row says `v1.2 -> v1.3` for an update, `v1.2` for
the installed version, "Not installed", "v1.3 available" for track only apps, and marks "track only" and "pinned" apps.
An empty list says "No apps yet" and a filter without results says "No apps match".

### Recommended

Suggested apps in four groups. **Add** puts one into your tracked apps, where it is installed and updated like any other (an app that is already tracked shows "Added"). The Store only adds the place where the app's releases are published; it does not host anything.

| Group | Apps |
| --- | --- |
| Stores | Aurora Store, Obtainium, Accrescent, F-Droid, Droid-ify |
| Tools | Shizuku, Shizuku Next, Morphe Manager, Morphe microG, SD Maid SE |
| Browser | IronFox, Cromite, Titanium Browser, Iceraven, Tor Browser, Waterfox |
| Media | Nova Video Player, Transistor, VLC, BoomingMusic |

Icons: installed apps are shown with their icon as the launcher draws it (your icon pack and icon shape); apps that are not installed yet show a letter. Not every source was checked on a device, so an app can fail to add when its release page changes or has no APK (this may happen with Waterfox, whose project page is for the desktop browser); the error is shown. Tor Browser is read from the Tor Project's download folder, newest version first. There is no "IzzyOnDroid" entry because it is a repository and not an app: add any of its apps through its F-Droid page address instead.

### App page

| Section | Contents |
| --- | --- |
| Header | Name, package name ("Package name known after the first install" for apps added by address), source |
| **Check now** | Checks this app only |
| Versions | Installed (with the version code), Latest, Size, Published, Last checked |
| **Skip this version** | Ignores the latest release. A button **Stop skipping vN** undoes it |
| Changelog | The release notes when the source provides them |
| Settings of this app | Track only, Stay on this version, Exclude from background updates, Category, Note, Rename, file name filter, Include pre-releases, **Apply and check** |
| Source | The address, with **Open**, **Share link** (an `obtainium://add/` link), **Remove from list** and **Uninstall app** |

The details of these settings are in [Sources and updates](./sources-updates#per-app-settings).

### Add dialogs

- **Add an app**: an **Address** and an optional **Package name** ("Fill this in if the app is already installed, so
  it shows as installed"). Paste a link and Telos recognizes the source.
- **Add installed apps**: a list of apps already on the phone. Telos looks them up on F-Droid, then IzzyOnDroid, and
  keeps the ones it finds up to date.
- **Store settings**: see [Updates](./sources-updates#updates).

## Managing the Telos apps

The **Telos apps** tab lists the built-in apps with a description and feature list of each one. Its search field also matches the same English and Greek synonym keywords as the launcher search (for example `pdf` or `gallery` for Telos Viewer).

| Action | Result |
| --- | --- |
| Install | Shows the app's icon in the app grid and in search |
| Open | Opens the app |
| Remove | Hides it. The code stays in Telos, so hidden apps cost nothing |
| Store | Cannot be removed, otherwise there would be no way back |

The apps are Telos Store, Telos Phone, Telos Messages, Telos Radio, Telos Music, Telos Video, Telos Viewer, Telos
Files, Telos Calculator, Telos Screenshot, Telos Screen Recorder and Telos Voice Recorder. [Smart Freeze](../freeze/) is a launcher feature in Settings, not a Telos app, so it is not listed here.

If an app was switched off by the [crash guard](../launcher/privacy-protection#crash-guard), its row says "Switched off
automatically because it crashed repeatedly." Installing it again resets the counter.

For Radio, Music, Video and Viewer "removed" also disables their services and screens, so they use no memory or CPU and
are no longer offered in "Open with". Phone and Messages stay registered with Android either way.

## Feature matrix

| Feature | Where | Notes |
| --- | --- | --- |
| Track apps from GitHub, GitLab, Codeberg, Forgejo, Gitea, F-Droid, IzzyOnDroid, SourceForge, web pages, direct links | Add app | |
| Update checks in the background | Store settings | Periodic job, Wi-Fi only by default |
| Silent updates | Store settings | Shizuku or root |
| Per-app options | App page | Track only, pin, skip, exclude, filter, pre-releases, category, note, rename |
| Search, filters, categories | Dashboard | |
| Add installed apps | More menu | F-Droid, then IzzyOnDroid |
| Obtainium import and export | More menu | Both directions |
| `obtainium://` links | Links | add and app |
| Manage the Telos apps | Telos apps tab | |
| Account, reviews, ratings, a catalog | | Not available |

::: warning
Installing APKs from outside an app store means you trust the source. Check the developer and the signature. Telos
refuses an APK whose package name differs from the tracked app, and Android refuses an update signed with another key,
but Telos cannot judge whether the source itself is trustworthy.
:::

## Privacy

- The Store contacts only the sources you track, and GitHub with your token if you added one.
- No analytics, no account, no list of your apps leaves the phone. **Add installed apps** looks up package names at
  F-Droid and IzzyOnDroid.
- APKs are downloaded to the app's cache (`store_downloads`) and installed from there.

## Limitations

- Only the listed sources. Obtainium sources such as APKMirror, Uptodown, Aptoide, APKPure, the Play Store, Huawei,
  Tencent, RuStore and Telegram are skipped when importing.
- Obtainium file imports are limited to 4,194,304 characters. The limit is enforced while reading, so an oversized file is rejected before it is loaded into memory.
- Η εισαγωγή αρχείων Obtainium έχει όριο 4.194.304 χαρακτήρων, το οποίο ελέγχεται κατά την ανάγνωση ώστε ένα υπερβολικά μεγάλο αρχείο να μη φορτωθεί ολόκληρο στη μνήμη.
- Apps in several parts (split APKs, bundles) and apps that need a login to download are not supported.
- Silent installs need Shizuku or root. Otherwise Android shows its installer each time.
- Checks run as a periodic job, so Android decides the exact moment.
- GitHub allows 60 requests per hour without a token.

## Troubleshooting

| Problem | Try this |
| --- | --- |
| "GitHub's request limit is reached" | Add a token in Store settings or wait an hour |
| An app does not show as installed | Fill in its package name, or install it from the Store once |
| "This file is for X, not for Y" | The source delivered an APK for another package. Check the file name filter |
| Installing opens the system installer | Shizuku or root is not available or not allowed |
| An update is not offered | The app is pinned, the version is skipped, or it is set to Track only |
| A Telos app has no icon | Open the Telos apps tab and tap Install |

## Updating frozen applications

Enable **Update frozen apps** in Store settings (off by default). Authorized Root/Shizuku installs
stream the private APK to the privileged process without proactively thawing the target. Android
14+ uses the installer’s persistent enabled-setting option where available; older/OEM installers
may enable the target during commit. Telos verifies and restores the saved restrictions after
success or failure. Telos configured as **Device Owner** uses Android
PackageInstaller sessions and restores state from the final callback, including cancellation.
Device Owner updates reject package/component disable overrides that this backend cannot restore.
Android may still require user action under its update policies. Plain Device Admin access does
not grant these privileges; it is not advertised as a silent installer.

This switch does not enable automatic updates for any app. Existing per-app/global opt-in rules,
APK package/signature checks and Android update ownership restrictions continue to apply.
Unfinished restoration is retained in Freeze Manager; after an interrupted update, use its
**Freeze again** action once installation has finished. For Play Store updates, use Freeze Manager's
temporary **Unfreeze all / Freeze again** session instead. Current-profile and backend limitations
are documented in [Smart Freeze](../freeze/backends-profiles.md#temporary-update-session).
