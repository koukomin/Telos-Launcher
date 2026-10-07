# Telos Store

An app installer and updater that works like [Obtainium](https://github.com/ImranR98/Obtainium): it
installs apps straight from the place where developers publish them. It also manages the Telos apps.

## Sources

| Source | Notes |
| --- | --- |
| GitHub | Releases |
| GitLab | Also self-hosted |
| Codeberg / Forgejo / Gitea | |
| F-Droid | Plus other F-Droid repositories |
| IzzyOnDroid | |
| SourceForge | |
| Direct APK link | |
| Any web page | That links to APKs |

The APK that fits the phone's CPU is chosen. If the newest release has no APK, older releases are used.

## Per-app settings

| Setting | Effect |
| --- | --- |
| Track only | Notify, never install |
| Stay on this version | Pin the version |
| Skip a version | Ignore one release |
| Exclude from background updates | |
| File name filter | Pick the right APK |
| Pre-releases | Include them |
| Category, note, rename | Organize |

## Updates

- Background checks with interval and Wi-Fi only settings, and a notification.
- Silent installs when Shizuku or root allows it.
- "Update all" and "Check now".

## Find and organize

Search, filters (updates, installed, not installed, track only) and categories. You can add apps that are
already installed, found through F-Droid and IzzyOnDroid.

## Import and export

Uses the Obtainium export format. `obtainium://` links open in the Store.

## Install and uninstall

With a root or Shizuku backend, or the system installer.

## Managing the Telos apps

The Store lists the Telos apps with a description of each one. "Installing" shows its icon in the app
grid and search. "Removing" hides it. The code stays in Telos, so hidden apps cost nothing. The Store
cannot be removed. If an app was switched off by the [crash guard](./launcher#resource-protection-and-crash-guard),
installing it again resets the counter.

::: warning
Installing APKs from outside an app store means you trust the source. Check the developer and signature.
:::
