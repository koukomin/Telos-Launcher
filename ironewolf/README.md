# Ironewolf — source and browser build foundation

**Status: preparation only. No Ironewolf virtual app, APK or default renderer is implemented yet.**

The upstream IronFox repository is an unchanged, pinned submodule. Its own version file pins
Firefox, Phoenix and the remaining dependencies. A small, separately reviewed branding patch is applied in a disposable build worktree. All upstream feature patches are retained; Telos does
not replace the hardened engine with Mozilla's stock GeckoView dependency. The submodule is
not included in the launcher APK by Gradle.

```sh
python3 ironewolf/engine.py init
python3 ironewolf/engine.py verify
python3 ironewolf/engine.py check-update
# On a dedicated Linux build machine; bootstrap installs upstream build dependencies:
python3 ironewolf/engine.py bootstrap
python3 ironewolf/engine.py prepare
python3 ironewolf/engine.py build --arch arm64 --project browser
# Engine-only output for integration work:
python3 ironewolf/engine.py build --arch arm64 --project engine
```

The default build delegates to IronFox's full **Fenix** release target, retaining its Firefox UI
and IronFox features/tweaks. Branding changes the name to **Ironewolf** and Android package to
`org.telos.ironewolf`. The upstream submodule remains unchanged; a separate patch is applied in
`.state/source`. Internal IronFox namespaces and preferences are deliberately preserved.

The browser target produces an **unsigned standalone Fenix APK**, not yet a virtual app inside
Telos. The engine target produces a GeckoView Maven ZIP. Both get a SHA-256/provenance sidecar.
Neither build has yet been compiled or validated on Android. No signing, installing or release
publishing is performed. Stock upstream artwork remains until separate Ironewolf artwork is supplied.

Use a dedicated machine with the resources/toolchains required by the pinned upstream build.
The current editing environment has only JDK 17, no Android SDK or Docker and about 29 GiB free;
it has not been provisioned for a complete Firefox native build.

## Updating upstream

1. Run `check-update`, then inspect upstream release notes and the changes between commits.
2. In a clean checkout, fetch and check out the reviewed **full commit SHA** in `upstream`.
3. Update `source.lock.json` to the same SHA and commit the lock and submodule gitlink together.
4. Prepare a fresh source checkout. Do not reuse an already patched Firefox tree across updates.
5. Build the ABI archives, review the feature matrix and run Android integration checks before
   using an archive in a launcher build. Update discovery never silently accepts new source.

Keep the full Firefox/Fenix UI. Maintain only narrow branding/integration patches outside the
upstream submodule. Updating a patch requires a fresh build worktree; the wrapper refuses unexpected edits.

## Intended integration

The requested final form is a built-in Telos virtual browser, retaining the complete Fenix UI.
The current standalone upstream application is **not** that integration. Fenix is an Android
application with its own Application class, manifest, dependencies and lifecycle, not a drop-in
Compose screen. Its browser host must be integrated with Telos without conflicting with launcher
initialisation. An external app shortcut or Custom Tab would not satisfy the requested virtual app.

Reuse existing Fenix UI instead of replacing it with Telos components. This keeps tabs,
extension management, bookmarks, history, Sync and IronFox settings together with upstream.
Gecko's native libraries will still increase APK size; per-ABI builds need measurement.

Changing `WebAppActivity` requires an engine abstraction and parity for navigation, cookies and
profile isolation, downloads, upload pickers, permissions, custom CSS, notifications, app locking
and lifecycle recovery. Preserve existing shortcut overrides and audit all direct WebView usages.

The default can become **Ironewolf** only in a build that bundles the verified hardened engine
and passes those checks. Preserve **Android WebView** as an explicit compatibility choice;
never silently switch engines or migrate private site data. Arbitrary WebView usages such as
authentication flows also need a separate compatibility audit.

## Ελληνικά

Έχει στηθεί μόνο η υποδομή πηγαίου κώδικα και build της μηχανής. Δεν υπάρχει ακόμη λειτουργικό
virtual app Ironewolf και δεν αλλάζει η προεπιλογή WebView. Στόχος είναι ενσωμάτωση στο ίδιο APK,
διατήρηση ολόκληρου του Firefox/Fenix UI και εύκολες ελεγχόμενες αναβαθμίσεις από το IronFox.
Η εντολή browser προετοιμάζει build πλήρους Fenix APK με όνομα Ironewolf και δικό του package ID.
Αυτό το ξεχωριστό build δεν αποτελεί ακόμη την ενσωμάτωση virtual app μέσα στον launcher.

## Licensing

IronFox build scripts are AGPL-3.0-or-later; patches and copied code retain their individual
licenses (including MPL-2.0, Apache-2.0 and GPL-3.0-or-later). See the pinned upstream README,
COPYING and source headers. This wrapper is GPL-3.0-or-later. Source/build tools are kept separate
from the Android application. No trademark ownership or upstream endorsement is implied.
