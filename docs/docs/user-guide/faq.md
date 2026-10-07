---
sidebar_position: 2
---

# Frequently Asked Questions

## Where do I download Telos?

From the [releases page on GitHub](https://github.com/koukomin/Telos-Launcher/releases). Every successful build is
published there as a debug-signed pre-release for testing, and the three newest are kept. Telos is not on the
Play Store or F-Droid. See [Get Started](./).

## How do I get app icons on the home screen?

There are two options:

1. Turn on Settings > Home screen > Dock.
2. Pin apps as favorites, see [Favorites and tags](./telos/launcher/favorites-tags).

## What are the "Personal", "Work" and "Private" tabs above the app grid?

They are not a feature of Telos. They show Android's profiles, which are separate spaces with their own apps and
data. **Personal** is the main profile, **Work** is a work profile (you can create one with an app such as
[Shelter](https://f-droid.org/packages/net.typeblog.shelter/)), and **Private** is the private space of Android 15
and newer. See [Home screen](./telos/launcher/home-screen) and the work profile section of the
[system catalogue](./telos/launcher/features/system-catalogue).

## What are the Telos apps and can I switch them off?

Telos Phone, Messages, Files, Photos, Music, Video, Radio, Calculator, Screenshot, Screen Recorder, Voice Recorder and Store are *virtual apps*: they are part
of the launcher and run inside it, they are not separate APKs. Open Telos Store and **remove** an app to hide its
icon, **install** it to show it again. A hidden app costs nothing. Telos Store itself cannot be removed. See
[Telos at a glance](./telos/#how-the-built-in-apps-work).

## An app disappeared after it crashed

Radio, Music, Video and Photos are switched off automatically when they crash or hang twice within a day, and
Telos shows a notification that points to Telos Store. Install the app again in Telos Store to reset the counter.
Phone and Messages are never switched off. See [Privacy and protection](./telos/launcher/privacy-protection#crash-guard).

## Can I remove or change the clock?

Yes. The clock settings open from the clock itself, see [Home screen](./telos/launcher/home-screen#clock). The clock
is the first element of the home screen, so you change its look and layout, not whether it exists.

## The toggle to grant notification access or the accessibility service is disabled

Android blocks these for apps that were installed from outside a store. Follow
[Restricted settings on Android 13+](./troubleshooting/restricted-settings).

## Telos keeps asking for notification access or the accessibility service

See [Granted permissions](./troubleshooting/granted-permissions).

## I cannot update to the newest build

Debug builds can be signed with a different key than the build you have installed, and Android then refuses the
update. See [Update problems](./troubleshooting/update-not-installed).

## The floating launcher does not appear

It is off by default. Turn it on in Settings > **Floating launcher** and allow *Display over other apps*, then turn
on at least one trigger zone. Tap the thin handle on the screen edge, or drag it toward the middle of the screen.
If the system back gesture takes over, start the drag a little away from the very edge. See
[Floating launcher](./telos/launcher/desktop-and-overlays#floating-launcher).

## Why is wallpaper blur not available on my device?

Blur is available when the device runs Android 12 or newer, battery saver is off, and the manufacturer has enabled
[cross-window blur](https://source.android.com/docs/core/display/window-blurs) for the device.

## Does Telos send my data anywhere?

Telos has no analytics or telemetry library. Online sources only run with the Online results filter, and
calendar, contacts, apps, local files, the calculator and the unit converter never use the network. Currency rates
download a small public file from the European Central Bank. The crash reporter and the logs under Advanced > Debug
stay on the device. See [Privacy and protection](./telos/launcher/privacy-protection).

## Is Telos the same as Kvaesitso?

Telos is based on [Kvaesitso](https://github.com/MM2-0/Kvaesitso). The launcher part (search, home screen, widgets,
themes, plugins) comes from Kvaesitso and Telos extends it. The built-in apps, the overlays, App Lock, context
profiles and more are added by Telos. See
[What Telos adds to Kvaesitso](./telos/launcher/#what-telos-adds-to-kvaesitso).

