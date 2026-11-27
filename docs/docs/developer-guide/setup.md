---
sidebar_position: 1
---

# Setup

## Setup the build environment

- Download and install the latest canary version of Android Studio and the Android SDK: https://developer.android.com/studio/preview.
- Clone the Git repository: `git clone https://github.com/MM2-0/Kvaesitso`
- Open the project in Android Studio

Telos builds the Android launcher only. The JVM `:desktop-linux` module is retired and is not included in `settings.gradle.kts`. Do not add `:desktop-linux` to `:app:app`. `:app:ui` may depend on `:ui-kmp`.


## Development checks

Use `python3 tools/ai-cli.py locate <feature>` to find entry points and targeted
Gradle tasks. Run `python3 tools/ai-cli.py audit` to inspect local working-tree
changes before creating a commit.

To use the repository's versioned pre-push hook in a fresh clone, run
`git config --local core.hooksPath .githooks`. It checks the committed revisions
Git supplies on push. The GitHub static audit also checks committed pull
request changes. The audit output summarizes successful checks and shows
relevant diagnostics for failed checks.

Dependabot and the [dependency candidate report](./dependabot.md) identify
updates. Validate dependency changes separately and keep the last working pin
when an upstream release fails the affected build.
