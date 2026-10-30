---
sidebar_position: 1
---

# Setup

## Setup the build environment

- Download and install the latest canary version of Android Studio and the Android SDK: https://developer.android.com/studio/preview.
- Clone the Git repository: `git clone https://github.com/MM2-0/Kvaesitso`
- Open the project in Android Studio

The optional Linux desktop shell is a separate Gradle module:

```sh
./gradlew :desktop-linux:packageUberJarForCurrentOS
```

Do not add `:desktop-linux` or `:ui-kmp` to `:app:app`. `:ui-kmp` has JVM and Android targets; `:app:ui` does not host its screens yet. Details: [Telos Linux Desktop](/docs/developer-guide/desktop-linux).
