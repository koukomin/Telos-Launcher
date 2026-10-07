# Plugins and integrations

Telos gets data from three kinds of places: its own code, **plugins** (other apps that use the plugin
SDK), and **integrations** (apps and services that Telos knows how to talk to). This page lists all of
them, how to switch each on, and what each one needs.

All integrations are under **Settings > Integrations**. Plugins are under **Settings > Advanced >
Plugins**.

## Overview

| Integration | Gives you | Needs | Settings page |
| --- | --- | --- | --- |
| Weather | Forecast for the weather widget, clock and At a Glance | Network, location | Integrations > Weather |
| Media control | Now playing, play, pause, skip | Notification access | Integrations > Media control |
| Feed | A content page from another app | A non-release build, a provider app | Integrations > Feed |
| Nextcloud | File search in your Nextcloud | Account | Integrations > Nextcloud |
| ownCloud | File search in your ownCloud | Account | Integrations > Owncloud |
| Wikipedia | Article results | Online results filter | Integrations > Wikipedia |
| Tasks | Reminders in search and in the reminders widget | The Tasks app, Tasks permission | Integrations > Tasks |
| Breezy Weather | Weather data from Breezy Weather | Breezy Weather installed | Integrations > Breezy Weather |
| Smartspacer | Smartspacer in the clock's dynamic zone | Android 10+, a non-release build | Integrations > Smartspacer |
| Cloud and network storage | Connections for Telos Files | Per storage | Integrations > Cloud and network storage |

## Plugins

A plugin is an ordinary Android app that exposes a content provider. Telos does not run its code. It
asks the provider for data, so a plugin cannot crash the launcher.

### Plugin types

| Type (SDK name) | What it adds | Where it shows up |
| --- | --- | --- |
| Files (`FileSearch`) | Search results from a cloud or other file source | Search > Files, with a plugin badge |
| Places (`LocationSearch`) | Places and points of interest | Search > Places |
| Calendar (`Calendar`) | Events and tasks | Calendar search and the calendar widget |
| Contacts (`ContactSearch`) | Contacts | Search > Contacts |
| Weather (`Weather`) | A weather provider | Integrations > Weather > Provider |
| Gesture action (`GestureAction`) | Named actions | Settings > Gestures > Plugin actions |
| Widget (`Widget`) | A small list widget | The widget picker |

The existing user page [Plugins](../../concepts/plugins) lists known plugins (OneDrive, OpenWeatherMap,
HERE, Foursquare, Tasks.org, public transport and others). Developers should read the
[plugin guide](../../../developer-guide/plugins/get-started) and the
[access control](../../../developer-guide/plugins/access-control) page.

### Install and enable a plugin

1. Install the plugin like any other app (an app store, or an APK).
2. Open Settings > Advanced > **Plugins**. The plugin appears under **Installed**.
3. Open it and switch on **Enable plugin**.
4. The plugin shows a **permission dialog**. Tap **Allow**. Telos is then on the plugin's allow list.
5. If a banner says "You need to set up this plugin first", tap **Set up** and finish the plugin's own
   sign-in or configuration. A banner "This plugin isn't working correctly" means it reports an error.
6. Turn on the specific feature: choose it as weather provider, switch it on in the Files, Contacts,
   Calendar or Places search settings, or assign its action to a gesture. The plugin page has shortcuts
   to those places.

::: tip Security model
A plugin only answers apps that are on its allow list. The list is created when you tap **Allow**. An app
that is not on it gets a `SecurityException`. Disabling a plugin in Telos stops Telos from querying it.
:::

### Gesture and widget plugins

- **Gesture action plugins** offer named actions. Settings > Gestures > a gesture > **Plugin actions**
  lists them.
- **Widget plugins** supply rows of title, subtitle and optional value. Telos draws them with its own
  widget design and refreshes them every few minutes while visible. Add one through **Add widget**.

## Weather

Settings > Integrations > Weather.

| Setting | Default | What it does |
| --- | --- | --- |
| Provider | Open-Meteo | Which service delivers the data |
| Automatic location | On | Use device location. Needs the location permission |
| Location | none | A fixed location, searched by name or entered as `lat lon name` |
| Measurement system | System | Units for temperature, wind and so on |

Providers that are always there: **Open-Meteo** and **Bright Sky** (German weather service data). Others
appear when they are available:

| Provider | When it is listed |
| --- | --- |
| OpenWeatherMap | Only in builds that ship an API key |
| MET Norway | Only in builds that ship the required contact string |
| Breezy Weather | When the Breezy Weather app is installed |
| Weather plugins | When the plugin is enabled |

### Severe weather alerts

Settings > Integrations > Weather > **Severe weather alerts** (off by default). After every weather update Telos
checks the stored forecast for the next hours and shows a notification when something you chose is expected. It uses
the forecast that is already on the phone, so it needs no extra network access. On Android 13 and newer, switching
it on asks for the notification permission.

| Setting | Default | What it does |
| --- | --- | --- |
| Check the next | 24 hours | 6, 12, 24, 48 or 72 hours |
| Rain | On, from 70 percent | The chance of rain. A provider without a chance of rain is judged by its rain icon |
| Heavy rain | On | The rain icon for heavy rain, or 4 mm per hour and more |
| Snow and sleet | On | The snow or sleet icon |
| Thunderstorms and hail | On | The thunderstorm, thunder or hail icon |
| High temperatures | On, from 35 °C | The temperature, or the extreme heat icon |
| Frost | On, at 0 °C and lower | The temperature, or the extreme cold icon |
| Strong wind | On, from 60 km/h | The wind speed, or the wind icon |
| High UV index | Off, from 8 | The UV index, when the provider has it |

The notification says what is expected, the strongest value (for example "Chance of rain up to 85%" or "Up to 37 °C"),
when it starts (**Now** or **From 18:00**, with the weekday if it is not today) and the place. Each kind is shown at most
once in 12 hours. Temperatures and speeds follow the [measurement system](#weather) (Celsius and km/h, or Fahrenheit and
mph). **Send a test notification** shows what an alert looks like.

::: info Limits
The alerts depend on what the provider delivers: a provider without a UV index or a chance of rain cannot trigger
those alerts. They are forecasts, not official warnings, so a storm warning of a weather service is not the same
thing. The check runs together with the weather updates (hourly by default), not at a fixed time.
:::

Telos refreshes weather hourly by default (a plugin can request another minimum interval), and only with
a network connection. If **Cannot find any locations**, your system geocoder may be missing (common on
de-Googled systems). Enter the location as `lat lon name`, for example `-90 0 South pole`. See the
original [Weather](../../integrations/weather) page.

## Media control

Settings > Integrations > Media control.

- Grant **notification access**. Android only lets third party apps read media sessions through
  notifications. The permission is used for media sessions and for notification badges, nothing is
  uploaded.
- By default only "music apps" are recognized. Add or remove apps in **Media apps** (there are an allow
  list and a deny list).
- It powers the media widget, the clock's media part, and the [Dynamic
  Island](./desktop-and-overlays#dynamic-island). The Telos [Music](../music/), [Video](../video/) and
  [Radio](../radio/) players are normal sessions to it.

On Android 13 and newer, see [restricted settings](../../troubleshooting/restricted-settings) if you
cannot grant notification access. Original page: [Media Control](../../integrations/mediacontrol).

## Nextcloud and ownCloud

1. Settings > Integrations > **Nextcloud** (or **Owncloud**).
2. Tap **Sign in** and follow the browser login for your server.
3. Back in Settings > Search > Files, switch on the cloud entry. Until you sign in it says "You haven't
   connected a Nextcloud account yet".

Cloud results are searched only with the **online results** filter on and only for queries of four or
more characters. They carry a cloud badge. Sign out from the same settings page. The login token is kept
in the app's encrypted preferences and is not part of the launcher backup, so sign in again after a
restore.

## Wikipedia

Settings > Integrations > Wikipedia has one option: the **Wikipedia URL**, which defaults to
`https://en.wikipedia.org`. Change it for another language edition or a compatible wiki. Results appear
with the online results filter, for queries of four or more characters. Requests go to that site's API.

## Tasks

The Tasks integration reads the free Tasks app (tasks.org).

1. Install the Tasks app and add your tasks there.
2. Settings > Integrations > **Tasks**. If Tasks is missing it offers **Install**.
3. Grant the **Tasks permission** when asked.
4. The page says "Tasks integration is fully set up and ready to use". Then switch on **Tasks** under
   Search > Calendar and **Reminders** under Search.

Tasks appear as reminders in search and in the reminders widget. A separate Tasks.org plugin from the
original launcher exists too. The built-in integration needs no plugin.

## Breezy Weather

Breezy Weather is a free weather app. As a data source it needs a one-time setup in **both** apps.

1. Install Breezy Weather and set up your locations there.
2. In Breezy Weather, go to Settings > Widgets and Live wallpaper > **Send Gadgetbridge data** and enable
   Telos.
3. In Telos, Settings > Integrations > Breezy Weather > **Set as weather provider**. The page then says
   "Currently set as weather provider".

The location is managed in Breezy Weather ("Managed by plugin"), so Telos shows **Manage location in
Breezy Weather** instead of its own location picker.

## Smartspacer

<Badge type="warning" text="debug and nightly only" />

Smartspacer is a free "At a Glance" widget app. With the integration, Smartspacer takes over the **dynamic
zone** of the clock.

- Requires Android 10 or newer.
- The settings page is hidden in release builds (a feature flag), so it is only visible in debug and
  nightly builds.
- Switch on **Enable Smartspacer integration**. The clock then shows "Managed by Smartspacer" with a
  **Turn off** button. The other dynamic zone parts (date, weather and so on) are not shown while it is on.

## Cloud and network storage

This entry configures connections used by [Telos Files](../files/): Dropbox, Google Drive, OneDrive,
Nextcloud, WebDAV, SFTP, SMB and FTP. It is separate from the search integrations above. Searching
Nextcloud or ownCloud files from the launcher search uses the accounts on their own pages.

## Limitations

- OpenWeatherMap and MET Norway depend on how a build was made.
- Feed and Smartspacer are not available in release builds.
- A plugin is only as reliable as the app that provides it.
- Cloud account logins are not part of the launcher backup.
