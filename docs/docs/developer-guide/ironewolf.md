# Ironewolf source and build preparation

Ironewolf is planned as a Telos virtual browser retaining the full Firefox/Fenix UI and IronFox
tweaks. This branch only adds a pinned source submodule, isolated branding patch and build wrapper.
There is no browser virtual app or default-renderer change yet.

See the [source/build instructions](https://github.com/koukomin/Telos-Launcher/blob/codex/ironewolf-upstream/ironewolf/README.md)
for source verification, reviewed upstream updates and native build prerequisites.

The browser target is currently a standalone unsigned Fenix APK. Embedding its Application,
manifest, activities and runtime in Telos remains required; merely opening that APK with a
launcher shortcut would not be equivalent to a virtual app. A GeckoView-only wrapper would
also omit Fenix-level features.

Before enabling Ironewolf by default, validate browser feature parity, existing web-app custom
CSS/notifications, permission prompts, uploads/downloads, profile isolation, app locking, process
recovery and ABI size on Android. Keep an explicit WebView compatibility option. Do not silently
share or migrate cookies and other private site data between engines.

## Ελληνικά

Διατηρείται το πλήρες Firefox UI. Έχει προστεθεί μόνο η υποδομή κώδικα/build, όχι λειτουργικό
virtual app ούτε αλλαγή προεπιλεγμένης μηχανής. Η ενσωμάτωση και οι δοκιμές Android εκκρεμούν.

## Native build execution

The dedicated `Build Ironewolf Fenix ARM64` workflow provisions an ephemeral Linux runner and
uploads an unsigned full-browser build plus provenance on success. Source verification runs
separately from native compilation; a passing verification job does not establish that an APK
exists. See Actions for the actual compilation result. The build wrapper clears IronFox's GitLab
CI mode and uses an explicit GeckoView-archive switch when only the engine is requested.
