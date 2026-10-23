---
sidebar_position: 0
---

# Get Started

Telos is a search-focused, free and open source launcher for Android, together with a set of built-in apps
(phone, messages, files, photos, music, video, radio, calculator and an app store). This page gets you from
download to a working setup. The rest of the guide describes every feature in detail.

::: tip Where to read what
- [Telos at a glance](./telos/) lists the launcher and every built-in app, one page each.
- [The launcher](./telos/launcher/) describes search, the home screen, widgets, themes, overlays and protection.
- The pages under **Concepts**, **Customization**, **Integrations**, **Search** and **Widgets** in the sidebar
  were written for Kvaesitso, the launcher that Telos is based on. They still describe how those parts work,
  but the pages under **Telos** are the ones that are written for Telos and checked against its code.
:::

## Requirements

| | |
| --- | --- |
| Android version | 8.0 or newer |
| Features that need a newer Android | Private space (Android 15), the screen-off gesture (Android 9), the screenshot tool of the floating launcher (Android 9), blurred wallpaper (Android 12) |
| Not needed | Root, a Google account, Google Play services |

## Install Telos

Telos is not on the Play Store. You can install it with 1 click using [Obtainium](https://obtainium.imranr.dev/), or download APKs directly from GitHub.

### 1-Click Install via Obtainium

If you use [Obtainium](https://obtainium.imranr.dev/), tap below to import Telos Launcher directly into Obtainium:

<p>
  <a href="obtainium://app/https://github.com/koukomin/Telos-Launcher">
    <img src="https://raw.githubusercontent.com/ImranR62/Obtainium/main/assets/badge.png" height="48" alt="Add to Obtainium">
  </a>
  &nbsp;&nbsp;
  <a href="https://github.com/koukomin/Telos-Launcher/releases">
    <img src="https://img.shields.io/badge/Get_it_on-GitHub-181717?style=for-the-badge&logo=github&logoColor=white" height="48" alt="Get it on GitHub">
  </a>
</p>

### Manual Installation

Two kinds of build are published on the [releases page](https://github.com/koukomin/Telos-Launcher/releases):

- **Stable releases** are signed with the Telos release key. This is the recommended download for everyday use. A new stable release is published automatically for every change that is merged into the main branch (changes to the documentation alone do not count), with the next patch number (for example `v1.40.3`). Telos Store notices it and offers the update.
- **Development builds** are debug-signed pre-releases, published from every successful build of the development
  branch for people who want to try the newest changes. The three newest are kept.

1. Open the [releases page](https://github.com/koukomin/Telos-Launcher/releases) on your phone. For everyday use,
   download the APK for your phone from the latest release, see **Which APK?** below; to try the newest changes,
   download `telos-debug.apk` from a pre-release.
2. Open the file. Android asks you to allow installing apps from this source (your browser or file manager). Allow
   it once, then confirm the installation.
3. Open Telos and set it as your home app, see below.

### Which APK?

A stable release has two files, one per CPU type. Each contains only the libraries of its own CPU, so neither is bigger than needed:

| File | Phones |
| --- | --- |
| `telos-arm64-v8a.apk` | **Almost every phone of the last ~8 years (64-bit ARM). Take this one if unsure.** |
| `telos-armeabi-v7a.apk` | Old 32-bit ARM phones, and some cheap phones that run a 32-bit Android on a 64-bit chip. Use it only if the 64-bit file cannot be installed |

Install only one. Both are signed with the same key, so you can switch later without uninstalling. Telos Store updates Telos itself and picks the right file for your phone. You can see the CPU type of your phone in **Settings > About phone**, or with an app such as CPU-Z.

::: warning Early software
Telos is a personal fork and a work in progress. Development builds in particular are debug-signed test builds with
rough edges, and several features are marked experimental in this guide. Back up before you update, see
[Update problems](./troubleshooting/update-not-installed).
:::

## Make Telos your home app

When Android asks, choose **Telos** and **Always**. You can also do it yourself: Android Settings > Apps >
Default apps > Home app > Telos. Telos works best as the home app. Some features need it, among them app shortcuts,
the Work Mode switch and the private space lock.

## First steps

1. **Search.** Swipe down on the home screen, or tap the search bar. Type a few letters of anything: an app, a
   contact, a file, a calendar event, a calculation (`12*7`), a unit conversion (`5 km in miles`) or a web search.
   See [Search](./telos/launcher/search).
2. **Open the settings.** Tap the **more** button in the search bar (three dots): the menu has **Wallpaper**,
   **Settings**, **Add widget** and **Help**. A long press on the home screen opens a smaller menu with
   **Change wallpaper** and **Add widget**.
3. **Look at the app grid.** Besides your installed apps you find the Telos apps (Phone, Messages, Files, Viewer,
   Music, Video, Radio, Calculator, Screenshot, Screen Recorder, Voice Recorder, Notes, Calendar and Store). They run inside the launcher.
4. **Open Telos Store** to switch the Telos apps on or off, and to install and update other apps from GitHub,
   F-Droid and similar sources. See [Store](./telos/store/).
5. **Set up widgets and gestures** in Settings > Home screen and Settings > Gestures. See
   [Home screen](./telos/launcher/home-screen) and [Widgets and feed](./telos/launcher/widgets-feed).
6. **Choose your look** in Settings > Appearance: presets, colors, fonts, shapes and icons. See
   [Customization](./telos/launcher/customization).

## Permissions

Telos asks for a permission only when a feature needs it, and everything works without the optional ones.

| Permission | Needed for |
| --- | --- |
| Contacts, Calendar | Contact and calendar search, the calendar widget |
| All files access | Searching local files, and Telos Files |
| Notification access | Notification badges and the music widget |
| Accessibility service | The screen-off, power menu and recents gestures, App Lock detection, Telos Screenshot and the screenshot tools of the floating launcher |
| Microphone | Telos Voice Recorder, the microphone of Telos Screen Recorder, call recording |
| Display over other apps | Floating launcher, Dynamic Island, App Lock overlay |
| Phone, call log, phone state | Telos Phone, the call pill of the Dynamic Island |
| Usage access | Smart Freeze idle detection, App Lock |

On Android 13 and newer, notification access and the accessibility service can be blocked for apps that were
installed from outside a store. Telos shows a hint after the first attempt, and
[Restricted settings](./troubleshooting/restricted-settings) shows the steps.

## What to read next

| If you want to | Read |
| --- | --- |
| Find things faster | [Search](./telos/launcher/search) and the [search catalogue](./telos/launcher/features/search-catalogue) |
| Use your phone through Telos | [Phone](./telos/phone/) and [Messages](./telos/messages/) |
| Manage files, photos, music and video | [Files](./telos/files/), [Photos](./telos/photos/), [Music](./telos/music/), [Video](./telos/video/) |
| Calculate, add or remove VAT, convert units | [Calculator](./telos/calculator/) |
| Take and edit screenshots, record the screen or your voice | [Screenshot](./telos/screenshot/), [Screen Recorder](./telos/screen-recorder/), [Voice Recorder](./telos/voice-recorder/) |
| Take photos and use the phone as a webcam | [Camera](./telos/camera/) |
| Get a quick-access sidebar over other apps | [Floating launcher](./telos/launcher/desktop-and-overlays#floating-launcher) |
| Protect your data | [Privacy and protection](./telos/launcher/privacy-protection) |
| Change something and cannot find it | [Where to find a setting](./telos/launcher/#where-to-find-a-setting) |
