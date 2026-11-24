---
sidebar_position: 1
---

# Setup

## Setup the build environment

- Download and install the latest canary version of Android Studio and the Android SDK: https://developer.android.com/studio/preview.
- Clone the Git repository: `git clone https://github.com/MM2-0/Kvaesitso`
- Open the project in Android Studio

Telos builds the Android launcher only. The JVM `:desktop-linux` module is retired and is not included in `settings.gradle.kts`. Do not add `:desktop-linux` to `:app:app`. `:app:ui` may depend on `:ui-kmp`.
